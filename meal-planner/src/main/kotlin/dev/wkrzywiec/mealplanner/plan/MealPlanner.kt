package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import dev.wkrzywiec.mealplanner.shared.config.toJson
import dev.wkrzywiec.mealplanner.shared.config.toObject
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import java.util.UUID

data class RawRecipeProposals(val response: String, val nextActions: List<String>, val recipes: List<RecipeIdWithRationale>)

class RecipeIdWithRationale(val rationale: String, val recipeId: UUID)

data class RecipeProposals(val response: String, val nextActions: List<String>, val recipes: List<RecipeWithRationale>)

data class RecipeWithRationale(val rationale: String, val recipe: Recipe?)

@Service
class MealPlanner(
    private val recipeSearch: RecipeSearchFacade,
    private val builder: ChatClient.Builder
) {

    companion object {
        private val log = logger {}
    }

    fun proposeMealStreaming(userPrompt: String, onEvent: (AiAgentEvent) -> Unit) {
        log.info { "Streaming meal proposals for prompt '$userPrompt'" }
        onEvent(AiAgentEvent.PlanningStarted(userPrompt))
        onEvent(AiAgentEvent.SearchingRecipes())

        val recipes = recipeSearch.findRecipes(userPrompt, 10)
        log.info { "Found ${recipes.size} recipes" }
        onEvent(AiAgentEvent.RecipesFound(recipes.size))

        // --- Call 1: stream the response text token by token ---
        log.info { "Streaming response from LLM..." }
        onEvent(AiAgentEvent.LlmCallStarted("meal-planner"))
        builder.build().prompt()
            .system(
                """
                You are a nutrition assistant that selects the best fitting recipes for meal planning.
                Write a brief overall explanation of your selection strategy and nutritional focus
                for the user query, given the available recipes. Plain text only, no JSON, no bullet points.
                RECIPES: ${recipes.toJson()}
                """.trimIndent()
            )
            .user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .stream()
            .content()
            .doOnNext { token -> onEvent(AiAgentEvent.ResponseToken(token)) }
            .blockLast()

        // --- Call 2: batch — select recipes + rationales + nextActions ---
        log.info { "Fetching recipe selection from LLM..." }
        onEvent(AiAgentEvent.LlmCallStarted("meal-planner"))
        val answer = builder.build().prompt()
            .system(
                """
                You are a nutrition assistant that selects the best fitting recipes for meal planning.
                
                RESPONSE FORMAT: Respond with valid JSON in exactly this structure:
                {
                  "nextActions": ["suggested action 1", "suggested action 2"],
                  "recipes": [
                    {
                      "recipeId": "recipe-uuid-here",
                      "rationale": "Why this recipe fits the user's goals"
                    }
                  ]
                }
                
                REQUIREMENTS:
                - Select 3-5 most suitable recipes from the provided list
                - Focus on nutritional balance, variety, and health benefits
                - Include specific nutritional reasons in each rationale
                - Suggest 2-3 relevant next actions for the user
                - Use the exact recipe IDs provided
                - Respond in the same language as the user's request
                - Return only valid JSON, no additional text
                
                RECIPES: ${recipes.toJson()}
                """.trimIndent()
            )
            .user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .call()
            .content()
        log.info { "Recipe selection from LLM:\n$answer" }

        answer
            ?.let { runCatching { it.toObject<RawRecipesAndActions>() }.getOrNull() }
            ?.let { raw ->
                raw.recipes.forEach { r ->
                    val recipe = recipes.find { it.id == r.recipeId }
                    onEvent(AiAgentEvent.RecipeSelected(r.recipeId, recipe, r.rationale))
                }
                onEvent(AiAgentEvent.NextActions(raw.nextActions))
            }
    }

    private data class RawRecipesAndActions(
        val nextActions: List<String>,
        val recipes: List<RecipeIdWithRationale>,
    )

    fun proposeMeal(userPrompt: String, onEvent: (AiAgentEvent) -> Unit) {
        log.info { "Searching for best meal proposals based on user prompt... '$userPrompt'"}
        onEvent(AiAgentEvent.PlanningStarted(userPrompt))
        onEvent(AiAgentEvent.SearchingRecipes())

        val recipes = recipeSearch.findRecipes(userPrompt, 10)
        log.info {" Found ${recipes.size} recipes with ids: ${recipes.map { it.id }} "}
        onEvent(AiAgentEvent.RecipesFound(recipes.size))
        onEvent(AiAgentEvent.LlmCallStarted("meal-planner"))
        log.info { "Calling an AI agent..."}
        val answer = builder.build().prompt()
            .system(
                """
                You are a nutrition assistant that selects the best fitting recipes for meal planning.
                
                TASK: Analyze the provided recipes and select the most suitable ones based on nutritional benefits and user goals. If no specific goal is provided, assume recommendations for a regular healthy adult diet.
                
                RESPONSE FORMAT: You must respond with valid JSON in exactly this structure:
                {
                  "response": "Brief overall explanation of your selection strategy and nutritional focus",
                  "nextActions": ["suggested action 1", "suggested action 2"],
                  "recipes": [
                    {
                      "recipeId": "recipe-uuid-here",
                      "rationale": "Detailed explanation of why this recipe was selected, focusing on specific nutritional benefits"
                    }
                  ]
                }
                
                REQUIREMENTS:
                - Select 3-5 most suitable recipes from the provided list
                - Focus on nutritional balance, variety, and health benefits
                - Include specific nutritional reasons in each rationale
                - Suggest 2-3 relevant next actions for the user
                - Use the exact recipe IDs provided
                - Respond in the same language as the user's request
                - Return only valid JSON, no additional text
                
                RECIPES: ${recipes.toJson()}
                """.trimIndent()
            )
            .user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .call()
            .content()
        log.info { "Response from AI Agent:\n $answer" }

        answer?.let {
            mapToRawRecipeProposals(it)
        }?.let {
            onEvent(AiAgentEvent.PlanReady(mapToRecipeProposals(it, recipes)))
        }



//        override fun send(event: AiEvent) {
//            executor.execute {
//                try {
//                    emitter.send(
//                        SseEmitter.event()
//                            .name("status")
//                            .data(StatusEvent(phase = "start", message = "Starting meal proposal"))
//                    )
//
//                    // Ensure the first event is flushed to the client early.
//                    // Some clients/proxies don't display anything until they receive at least one chunk.
////                emitter.send(SseEmitter.event().comment("flush"))
//
//                    emitter.send(
//                        SseEmitter.event()
//                            .name("status")
//                            .data(StatusEvent(phase = "search", message = "Searching recipes"))
//                    )
//
////                emitter.send(SseEmitter.event().comment("flush"))
//
//                    emitter.send(
//                        SseEmitter.event()
//                            .name("status")
//                            .data(StatusEvent(phase = "llm", message = "Calling LLM"))
//                    )
//
////                emitter.send(SseEmitter.event().comment("flush"))
//
//                    val proposals: RecipeProposals? = mealPlanner.proposeMeal(prompt)
//
//                    val finalPayload: Any = proposals ?: mapOf("error" to "No proposals")
//
//                    emitter.send(
//                        SseEmitter.event()
//                            .name("final")
//                            .data(finalPayload)
//                    )
//
//                    emitter.complete()
//                } catch (e: Exception) {
//                    SsEventEmitter.Companion.log.warn(e) { "SSE stream failed" }
//                    emitter.completeWithError(e)
//                }
//            }
//        }
    }

    fun proposeMeal(userPrompt: String): RecipeProposals? {
        log.info { "Searching for best meal proposals based on user prompt... '$userPrompt'"}
        val recipes = recipeSearch.findRecipes(userPrompt, 10)

        log.info {" Found ${recipes.size} recipes with ids: ${recipes.map { it.id }} "}
        log.info { "Calling an AI agent..."}
        val answer = builder.build().prompt()
            .system(
                """
                You are a nutrition assistant that selects the best fitting recipes for meal planning.
                
                TASK: Analyze the provided recipes and select the most suitable ones based on nutritional benefits and user goals. If no specific goal is provided, assume recommendations for a regular healthy adult diet.
                
                RESPONSE FORMAT: You must respond with valid JSON in exactly this structure:
                {
                  "response": "Brief overall explanation of your selection strategy and nutritional focus",
                  "nextActions": ["suggested action 1", "suggested action 2"],
                  "recipes": [
                    {
                      "recipeId": "recipe-uuid-here",
                      "rationale": "Detailed explanation of why this recipe was selected, focusing on specific nutritional benefits"
                    }
                  ]
                }
                
                REQUIREMENTS:
                - Select 3-5 most suitable recipes from the provided list
                - Focus on nutritional balance, variety, and health benefits
                - Include specific nutritional reasons in each rationale
                - Suggest 2-3 relevant next actions for the user
                - Use the exact recipe IDs provided
                - Respond in the same language as the user's request
                - Return only valid JSON, no additional text
                
                RECIPES: ${recipes.toJson()}
                """.trimIndent()
            )
            .user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .call()
            .content()
        log.info { "Response from AI Agent:\n $answer" }
        return answer?.let {
            mapToRawRecipeProposals(it)
        }?.let {
            mapToRecipeProposals(it, recipes)
        }
    }

    private fun mapToRawRecipeProposals(answer: String): RawRecipeProposals? {
        return answer.toObject<RawRecipeProposals>()
    }

    private fun mapToRecipeProposals(raw: RawRecipeProposals, fullRecipes: List<Recipe>): RecipeProposals {
        return RecipeProposals(
            response = raw.response,
            nextActions = raw.nextActions,
            recipes = raw.recipes.map { RecipeWithRationale(rationale = it.rationale, recipe = fullRecipes.find { recipe -> recipe.id == it.recipeId}) }
        )
    }
}
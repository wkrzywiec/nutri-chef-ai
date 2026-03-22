package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import dev.wkrzywiec.mealplanner.shared.config.toJson
import dev.wkrzywiec.mealplanner.shared.config.toObject
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import java.util.UUID

data class RecipeProposals(val response: String, val nextActions: List<String>, val recipes: List<RecipeEntry>)

data class RecipeEntry(val recipe: Recipe?)

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

        // --- Call 2: batch — select recipe IDs + nextActions ---
        log.info { "Fetching recipe selection from LLM..." }
        onEvent(AiAgentEvent.LlmCallStarted("meal-planner"))
        val selectionAnswer = builder.build().prompt()
            .system(
                """
                You are a nutrition assistant that selects the best fitting recipes for meal planning.
                
                RESPONSE FORMAT: Respond with valid JSON in exactly this structure:
                {
                  "nextActions": ["suggested action 1", "suggested action 2"],
                  "recipeIds": ["uuid-1", "uuid-2"]
                }
                
                REQUIREMENTS:
                - Select 3-5 most suitable recipe IDs from the provided list
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
        log.info { "Recipe selection from LLM:\n$selectionAnswer" }

        val selection = selectionAnswer
            ?.let { runCatching { it.toObject<RawSelection>() }.getOrNull() }
            ?: return

        onEvent(AiAgentEvent.NextActions(selection.nextActions))

        val selectedRecipes = selection.recipeIds.mapNotNull { recipeId ->
            val recipe = recipes.find { it.id == recipeId } ?: return@mapNotNull null
            onEvent(AiAgentEvent.RecipeSelected(recipeId, recipe))
            RecipeEntry(recipe = recipe)
        }

        onEvent(AiAgentEvent.PlanReady(RecipeProposals(
            response = "",
            nextActions = selection.nextActions,
            recipes = selectedRecipes,
        )))
    }

    private data class RawSelection(
        val nextActions: List<String>,
        val recipeIds: List<UUID>,
    )
}

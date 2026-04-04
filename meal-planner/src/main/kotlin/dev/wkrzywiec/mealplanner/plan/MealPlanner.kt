package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import dev.wkrzywiec.mealplanner.shared.config.toJson
import dev.wkrzywiec.mealplanner.shared.config.toObject
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

data class RecipeProposals(
    val response: String,
    val nextActions: List<String>,
    val recipes: List<RecipeEntry>,
)

data class RecipeEntry(
    val recipe: Recipe?,
)

@Service
class MealPlanner(
    private val recipeSearch: RecipeSearchFacade,
    private val builder: ChatClient.Builder,
    @Value("\${meal-planner.ai.low-cost-model}") private val lowCostModel: String,
) {
    companion object {
        private val log = logger {}
        private const val RECIPE_FETCH_LIMIT = 100
    }

    fun proposeMealStreaming(
        userPrompt: String,
        onEvent: (AiAgentEvent) -> Unit,
    ) {
        log.info { "Streaming meal proposals for prompt '$userPrompt'" }
        onEvent(AiAgentEvent.PlanningStarted(userPrompt))

        // Step 0: stream a brief acknowledgement so the user knows the input was received (low-cost model)
        log.info { "Streaming acknowledgement from LLM (low-cost model: $lowCostModel)..." }
        onEvent(AiAgentEvent.LlmCallStarted(lowCostModel))
        builder
            .build()
            .prompt()
            .options(OpenAiChatOptions.builder().model(lowCostModel).build())
            .system(
                """
                You are a nutrition assistant. The user has just submitted a meal planning request.
                Write 1-2 short sentences acknowledging that you received their request, that you
                understood what they are looking for, and that you are now searching for suitable
                recipes. Plain text only, no JSON, no bullet points.
                """.trimIndent(),
            ).user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .stream()
            .content()
            .doOnNext { token -> onEvent(AiAgentEvent.ResponseToken(token)) }
            .blockLast()

        onEvent(AiAgentEvent.SearchingRecipes())

        // Step 1: fetch 100 recipes matching the query
        val recipes = recipeSearch.findRecipes(userPrompt, RECIPE_FETCH_LIMIT)
        log.info { "Found ${recipes.size} recipes" }
        onEvent(AiAgentEvent.RecipesFound(recipes.size))

        // Step 2: Call 1 — LLM selects the best recipes (batch) and emits them as events
        log.info { "Requesting recipe selection from LLM..." }
        onEvent(AiAgentEvent.LlmCallStarted("meal-planner"))
        val selectionAnswer =
            builder
                .build()
                .prompt()
                .system(
                    """
                    You are a nutrition assistant that selects the best fitting recipes for meal planning.
                    
                    RESPONSE FORMAT: Respond with valid JSON in exactly this structure:
                    {
                      "recipeIds": ["uuid-1", "uuid-2"]
                    }
                    
                    REQUIREMENTS:
                    - Select 3-5 most suitable recipe IDs from the provided list
                    - Use the exact recipe IDs provided
                    - Respond in the same language as the user's request
                    - Return only valid JSON, no additional text
                    
                    RECIPES: ${recipes.toJson()}
                    """.trimIndent(),
                ).user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
                .call()
                .content()
        log.info { "Recipe selection from LLM:\n$selectionAnswer" }

        val selection =
            selectionAnswer
                ?.let { runCatching { it.toObject<RawRecipeSelection>() }.getOrNull() }

        if (selection == null) {
            log.warn { "Failed to parse recipe selection from LLM response: $selectionAnswer" }
            onEvent(AiAgentEvent.PlanFailed("Failed to parse recipe selection from AI response. Please try again."))
            return
        }

        val selectedRecipes =
            selection.recipeIds.mapNotNull { recipeId ->
                val recipe = recipes.find { it.id == recipeId } ?: return@mapNotNull null
                onEvent(AiAgentEvent.RecipeSelected(recipeId, recipe))
                RecipeEntry(recipe = recipe)
            }

        // Step 3: Call 2 — LLM streams the rationale for selected recipes chunk by chunk
        log.info { "Streaming rationale from LLM..." }
        onEvent(AiAgentEvent.LlmCallStarted("meal-planner"))
        val rationaleRecipes = selectedRecipes.mapNotNull { it.recipe }
        builder
            .build()
            .prompt()
            .system(
                """
                You are a nutrition assistant explaining meal planning choices.
                Write a brief overall explanation of why the selected recipes fit the user's query,
                covering nutritional focus and overall strategy. Plain text only, no JSON, no bullet points.
                SELECTED RECIPES: ${rationaleRecipes.toJson()}
                """.trimIndent(),
            ).user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .stream()
            .content()
            .doOnNext { token -> onEvent(AiAgentEvent.ResponseToken(token)) }
            .blockLast()

        // Step 4: Call 3 — LLM suggests next actions using a low-cost model
        log.info { "Requesting next actions from LLM (low-cost model: $lowCostModel)..." }
        onEvent(AiAgentEvent.LlmCallStarted(lowCostModel))
        val nextActionsAnswer =
            builder
                .build()
                .prompt()
                .options(OpenAiChatOptions.builder().model(lowCostModel).build())
                .system(
                    """
                    You are a nutrition assistant suggesting follow-up actions for a meal plan.
                    
                    RESPONSE FORMAT: Respond with valid JSON in exactly this structure:
                    {
                      "nextActions": ["suggested action 1", "suggested action 2"]
                    }
                    
                    REQUIREMENTS:
                    - Suggest 2-3 relevant next actions for the user based on the selected recipes
                    - Respond in the same language as the user's request
                    - Return only valid JSON, no additional text
                    
                    SELECTED RECIPES: ${rationaleRecipes.toJson()}
                    """.trimIndent(),
                ).user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
                .call()
                .content()
        log.info { "Next actions from LLM:\n$nextActionsAnswer" }

        val nextActionsSelection =
            nextActionsAnswer
                ?.let { runCatching { it.toObject<RawNextActions>() }.getOrNull() }

        if (nextActionsSelection == null) {
            log.warn { "Failed to parse next actions from LLM response: $nextActionsAnswer" }
            onEvent(AiAgentEvent.PlanFailed("Failed to parse next actions from AI response. Please try again."))
            return
        }

        onEvent(AiAgentEvent.NextActions(nextActionsSelection.nextActions))

        onEvent(
            AiAgentEvent.PlanReady(
                RecipeProposals(
                    response = "",
                    nextActions = nextActionsSelection.nextActions,
                    recipes = selectedRecipes,
                ),
            ),
        )
    }

    private data class RawRecipeSelection(
        val recipeIds: List<UUID>,
    )

    private data class RawNextActions(
        val nextActions: List<String>,
    )
}

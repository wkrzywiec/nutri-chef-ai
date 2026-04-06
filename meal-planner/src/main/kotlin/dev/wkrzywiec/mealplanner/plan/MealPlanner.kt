package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.stereotype.Service

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
    private val acknowledgementAgent: AcknowledgementAgent,
    private val recipeSelectionAgent: RecipeSelectionAgent,
    private val rationaleAgent: RationaleAgent,
    private val nextActionsAgent: NextActionsAgent,
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

        // Step 0: stream a brief acknowledgement so the user knows the input was received
        onEvent(AiAgentEvent.LlmCallStarted("acknowledgement"))
        acknowledgementAgent.execute(userPrompt) { token -> onEvent(AiAgentEvent.ResponseToken(token)) }

        onEvent(AiAgentEvent.SearchingRecipes())

        // Step 1: fetch candidate recipes
        val recipes = recipeSearch.findRecipes(userPrompt, RECIPE_FETCH_LIMIT)
        log.info { "Found ${recipes.size} recipes" }
        onEvent(AiAgentEvent.RecipesFound(recipes.size))

        // Step 2: LLM selects the best recipes
        onEvent(AiAgentEvent.LlmCallStarted("recipe-selection"))
        val selectedRecipes = recipeSelectionAgent.execute(userPrompt, recipes)
        if (selectedRecipes == null) {
            onEvent(AiAgentEvent.PlanFailed("Failed to parse recipe selection from AI response. Please try again."))
            return
        }
        selectedRecipes.forEach { entry ->
            entry.recipe?.let { onEvent(AiAgentEvent.RecipeSelected(it.id, it)) }
        }

        // Step 3: LLM streams the rationale for the selected recipes
        val rationaleRecipes = selectedRecipes.mapNotNull { it.recipe }
        onEvent(AiAgentEvent.LlmCallStarted("rationale"))
        rationaleAgent.execute(userPrompt, rationaleRecipes) { token -> onEvent(AiAgentEvent.ResponseToken(token)) }

        // Step 4: LLM suggests next actions
        onEvent(AiAgentEvent.LlmCallStarted("next-actions"))
        val nextActions = nextActionsAgent.execute(userPrompt, rationaleRecipes)
        if (nextActions == null) {
            onEvent(AiAgentEvent.PlanFailed("Failed to parse next actions from AI response. Please try again."))
            return
        }

        onEvent(AiAgentEvent.NextActions(nextActions))
        onEvent(
            AiAgentEvent.PlanReady(
                RecipeProposals(
                    response = "",
                    nextActions = nextActions,
                    recipes = selectedRecipes,
                ),
            ),
        )
    }
}

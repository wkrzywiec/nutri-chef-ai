package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.stereotype.Service

data class RecipeProposals(
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
    private val suggestedFollowUpsAgent: SuggestedFollowUpsAgent,
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

        // Step 0: stream a brief acknowledgement so the user knows the input was received
        onEvent(AiAgentEvent.PlanningStarted(userPrompt))
        acknowledgementAgent.execute(userPrompt) { token -> onEvent(AiAgentEvent.ResponseToken(token)) }

        // Step 1: fetch candidate recipes
        onEvent(AiAgentEvent.SearchingRecipes())
        val recipes = recipeSearch.findRecipes(userPrompt, RECIPE_FETCH_LIMIT)
        log.info { "Found ${recipes.size} recipes" }
        onEvent(AiAgentEvent.RecipesFound(recipes.size))

        // Step 2: LLM selects the best recipes
        val selectedRecipes = recipeSelectionAgent.execute(userPrompt, recipes)
        if (selectedRecipes.isEmpty()) {
            onEvent(AiAgentEvent.PlanFailed("Failed to found matching recipes. Please try again."))
            return
        }
        selectedRecipes.forEach { entry ->
            entry.recipe?.let { onEvent(AiAgentEvent.RecipeSelected(it.id, it)) }
        }

        // Step 3: LLM streams the rationale for the selected recipes
        val rationaleRecipes = selectedRecipes.mapNotNull { it.recipe }
        rationaleAgent.execute(userPrompt, rationaleRecipes) { token -> onEvent(AiAgentEvent.ResponseToken(token)) }

        // Step 4: LLM suggests follow-up actions
        val suggestedFollowUps = suggestedFollowUpsAgent.execute(userPrompt, rationaleRecipes)
        onEvent(AiAgentEvent.SuggestedFollowUps(suggestedFollowUps))
        onEvent(
            AiAgentEvent.PlanReady(
                RecipeProposals(
                    recipes = selectedRecipes,
                ),
            ),
        )
    }
}

package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import java.util.UUID

sealed interface AiAgentEvent {
    data class PlanningStarted(
        val prompt: String,
    ) : AiAgentEvent

    data class ResponseToken(
        val token: String,
    ) : AiAgentEvent

    class SearchingRecipes : AiAgentEvent

    data class RecipesFound(
        val count: Int,
    ) : AiAgentEvent

    data class RecipeSelected(
        val recipeId: UUID,
        val recipe: Recipe?,
    ) : AiAgentEvent

    data class SuggestedFollowUps(
        val suggestions: List<String>,
    ) : AiAgentEvent

    data class PlanReady(
        val proposals: RecipeProposals,
    ) : AiAgentEvent

    data class PlanFailed(
        val reason: String,
    ) : AiAgentEvent
}

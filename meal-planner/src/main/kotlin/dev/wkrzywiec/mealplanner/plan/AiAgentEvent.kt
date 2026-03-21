package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import java.util.UUID

sealed class AiAgentEvent {
    data class PlanningStarted(val prompt: String) : AiAgentEvent()
    class SearchingRecipes(): AiAgentEvent()
    data class RecipesFound(val count: Int) : AiAgentEvent()
    data class LlmCallStarted(val modelName: String) : AiAgentEvent()
    data class LlmResponse(val answer: String): AiAgentEvent()

    // Streaming token events
    data class ResponseToken(val token: String) : AiAgentEvent()
    data class RecipeSelected(val recipeId: UUID, val recipe: Recipe?, val rationale: String) : AiAgentEvent()
    data class NextActions(val actions: List<String>) : AiAgentEvent()

    data class PlanReady(val proposals: RecipeProposals) : AiAgentEvent()
    data class PlanFailed(val reason: String) : AiAgentEvent()
}

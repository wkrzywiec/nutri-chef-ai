package dev.wkrzywiec.mealplanner.plan

sealed class AiAgentEvent {
    data class PlanningStarted(val prompt: String) : AiAgentEvent()
    class SearchingRecipes(): AiAgentEvent()
    data class RecipesFound(val count: Int) : AiAgentEvent()
    data class LlmCallStarted(val modelName: String) : AiAgentEvent()
    data class LlmResponse(val answer: String): AiAgentEvent()
    data class PlanReady(val proposals: RecipeProposals) : AiAgentEvent()
    data class PlanFailed(val reason: String) : AiAgentEvent()
}
package dev.wkrzywiec.mealplanner.plan.application

import dev.wkrzywiec.mealplanner.plan.AiAgentEvent
import dev.wkrzywiec.mealplanner.shared.config.toJson
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Clock
import java.time.Instant

@Component
class AiAgentEventMapper(
    private val clock: Clock,
) {
    data class StatusEvent(
        val ts: Instant,
        val phase: String,
        val message: String,
    )

    private data class AgentResponseDto(
        val type: String,
        val ts: Instant,
        val payload: Any,
    )

    fun toSseEvent(event: AiAgentEvent): SseEmitter.SseEventBuilder =
        SseEmitter.event().data(toAgentResponseDto(event).toString(Charsets.UTF_8).trimEnd())

    fun toAgentResponseDto(event: AiAgentEvent): ByteArray {
        val now = clock.instant()
        val envelope =
            when (event) {
                is AiAgentEvent.PlanningStarted ->
                    AgentResponseDto(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "start", message = "Starting meal proposal for: ${event.prompt}"),
                    )
                is AiAgentEvent.SearchingRecipes ->
                    AgentResponseDto(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "search", message = "Searching for matching recipes"),
                    )
                is AiAgentEvent.RecipesFound ->
                    AgentResponseDto(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "search", message = "Found ${event.count} matching recipes"),
                    )
                is AiAgentEvent.ResponseToken -> AgentResponseDto("response.token", ts = now, payload = event.token)
                is AiAgentEvent.RecipeSelected ->
                    AgentResponseDto(
                        "recipe.selected",
                        ts = now,
                        payload =
                            mapOf(
                                "recipeId" to event.recipeId,
                                "name" to event.recipe?.name,
                                "description" to event.recipe?.description,
                                "ingredients" to event.recipe?.ingredients,
                                "instructions" to event.recipe?.instructions,
                                "sourceUrl" to event.recipe?.sourceUrl,
                                "source" to event.recipe?.source,
                                "imageUrl" to event.recipe?.imageUrl,
                                "servings" to event.recipe?.servings,
                                "tags" to event.recipe?.tags,
                                "similarityScore" to event.recipe?.similarityScore,
                            ),
                    )
                is AiAgentEvent.SuggestedFollowUps -> AgentResponseDto("suggested.follow.ups", ts = now, payload = event.suggestions)
                is AiAgentEvent.PlanReady -> AgentResponseDto("final", ts = now, payload = event.proposals)
                is AiAgentEvent.PlanFailed ->
                    AgentResponseDto(
                        "error",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "error", message = event.reason),
                    )
            }
        return "${envelope.toJson()}\n".toByteArray(Charsets.UTF_8)
    }
}

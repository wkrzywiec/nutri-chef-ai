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
        val pct: Int? = null,
    )

    private data class NdJsonEvent(
        val type: String,
        val ts: Instant,
        val payload: Any,
    )

    fun toSseEvent(event: AiAgentEvent): SseEmitter.SseEventBuilder =
        SseEmitter.event().data(toNdJsonLine(event).toString(Charsets.UTF_8).trimEnd())

    fun toNdJsonLine(event: AiAgentEvent): ByteArray {
        val now = Instant.now(clock)
        val envelope =
            when (event) {
                is AiAgentEvent.PlanningStarted ->
                    NdJsonEvent(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "start", message = "Starting meal proposal for: ${event.prompt}"),
                    )
                is AiAgentEvent.SearchingRecipes ->
                    NdJsonEvent(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "search", message = "Searching for matching recipes"),
                    )
                is AiAgentEvent.RecipesFound ->
                    NdJsonEvent(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "search", message = "Found ${event.count} matching recipes"),
                    )
                is AiAgentEvent.LlmCallStarted ->
                    NdJsonEvent(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "llm", message = "Calling LLM (${event.modelName})"),
                    )
                is AiAgentEvent.LlmResponse ->
                    NdJsonEvent(
                        "status",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "llm", message = "LLM responded"),
                    )
                is AiAgentEvent.ResponseToken -> NdJsonEvent("response.token", ts = now, payload = event.token)
                is AiAgentEvent.RecipeSelected ->
                    NdJsonEvent(
                        "recipe.selected",
                        ts = now,
                        payload =
                            mapOf(
                                "recipeId" to event.recipeId,
                                "name" to event.recipe?.name,
                            ),
                    )
                is AiAgentEvent.SuggestedFollowUps -> NdJsonEvent("suggested.follow.ups", ts = now, payload = event.suggestions)
                is AiAgentEvent.PlanReady -> NdJsonEvent("final", ts = now, payload = event.proposals)
                is AiAgentEvent.PlanFailed ->
                    NdJsonEvent(
                        "error",
                        ts = now,
                        payload = StatusEvent(ts = now, phase = "error", message = event.reason),
                    )
            }
        return "${envelope.toJson()}\n".toByteArray(Charsets.UTF_8)
    }
}

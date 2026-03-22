package dev.wkrzywiec.mealplanner.plan.application

import dev.wkrzywiec.mealplanner.plan.AiAgentEvent
import dev.wkrzywiec.mealplanner.plan.MealPlanner
import dev.wkrzywiec.mealplanner.plan.RecipeProposals
import dev.wkrzywiec.mealplanner.shared.config.toJson
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/planner")
class MealPlannerController(
    private val mealPlanner: MealPlanner,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @GetMapping(
        path = ["/single"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun proposeMeal(
        @RequestParam prompt: String,
    ): ResponseEntity<RecipeProposals> {
        val responseText = StringBuilder()
        var proposals: RecipeProposals? = null
        mealPlanner.proposeMealStreaming(prompt) { event ->
            when (event) {
                is AiAgentEvent.ResponseToken -> responseText.append(event.token)
                is AiAgentEvent.PlanReady     -> proposals = event.proposals.copy(response = responseText.toString())
                else                          -> Unit
            }
        }
        return ResponseEntity.ok(proposals)
    }

    // Keep this controller self-contained. For production usage, inject a TaskExecutor.
    private val executor: Executor = Executors.newCachedThreadPool()

    data class StatusEvent(
        val ts: Instant = Instant.now(),
        val phase: String,
        val message: String,
        val pct: Int? = null,
    )

    // -------------------------------------------------------------------------
    // Token-streaming SSE  (Accept: text/event-stream)
    // -------------------------------------------------------------------------

    @GetMapping(
        path = ["/single"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    fun proposeMealSse(
        @RequestParam prompt: String,
    ): ResponseEntity<SseEmitter> {
        val sseEmitter = SseEmitter(0L)
        executor.execute {
            try {
                mealPlanner.proposeMealStreaming(prompt) { event ->
                    sseEmitter.send(toSseEvent(event))
                }
            } catch (e: Exception) {
                log.warn(e) { "SSE stream failed" }
                sseEmitter.send(toSseEvent(AiAgentEvent.PlanFailed(e.message ?: "Unknown error")))
            } finally {
                sseEmitter.complete()
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .header("Cache-Control", "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(sseEmitter)
    }

    // -------------------------------------------------------------------------
    // Token-streaming NDJSON  (Accept: application/x-ndjson)
    // -------------------------------------------------------------------------

    @GetMapping(
        path = ["/single"],
        produces = ["application/x-ndjson"],
    )
    fun proposeMealNdJson(
        @RequestParam prompt: String,
    ): ResponseEntity<StreamingResponseBody> {
        val body = StreamingResponseBody { out: OutputStream ->
            try {
                mealPlanner.proposeMealStreaming(prompt) { event ->
                    out.write(toNdJsonLine(event))
                    out.flush()
                }
            } catch (e: Exception) {
                log.warn(e) { "NDJSON stream failed" }
                out.write(toNdJsonLine(AiAgentEvent.PlanFailed(e.message ?: "Unknown error")))
                out.flush()
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-ndjson"))
            .header("Cache-Control", "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(body)
    }

    // -------------------------------------------------------------------------
    // SSE event mapping
    // -------------------------------------------------------------------------

    private fun toSseEvent(event: AiAgentEvent): SseEmitter.SseEventBuilder = when (event) {
        is AiAgentEvent.PlanningStarted  -> SseEmitter.event().name("status")
            .data(StatusEvent(phase = "start",  message = "Starting meal proposal for: ${event.prompt}"))
        is AiAgentEvent.SearchingRecipes -> SseEmitter.event().name("status")
            .data(StatusEvent(phase = "search", message = "Searching for matching recipes"))
        is AiAgentEvent.RecipesFound     -> SseEmitter.event().name("status")
            .data(StatusEvent(phase = "search", message = "Found ${event.count} matching recipes"))
        is AiAgentEvent.LlmCallStarted   -> SseEmitter.event().name("status")
            .data(StatusEvent(phase = "llm",    message = "Calling LLM (${event.modelName})"))
        is AiAgentEvent.LlmResponse      -> SseEmitter.event().name("status")
            .data(StatusEvent(phase = "llm",    message = "LLM responded"))
        is AiAgentEvent.ResponseToken    -> SseEmitter.event().name("response.token")
            .data(event.token)
        is AiAgentEvent.RecipeSelected   -> SseEmitter.event().name("recipe.selected")
            .data(mapOf("recipeId" to event.recipeId, "name" to event.recipe?.name))
        is AiAgentEvent.NextActions      -> SseEmitter.event().name("next.actions")
            .data(event.actions)
        is AiAgentEvent.PlanReady        -> SseEmitter.event().name("final")
            .data(event.proposals)
        is AiAgentEvent.PlanFailed       -> SseEmitter.event().name("error")
            .data(StatusEvent(phase = "error",  message = event.reason))
    }

    // -------------------------------------------------------------------------
    // NDJSON line mapping
    // -------------------------------------------------------------------------

    // Discriminated union wrapper so the client always has a `type` field to switch on.
    private data class NdJsonEvent(
        val type: String,
        val ts: Instant = Instant.now(),
        val payload: Any,
    )

    private fun toNdJsonLine(event: AiAgentEvent): ByteArray {
        val envelope = when (event) {
            is AiAgentEvent.PlanningStarted  -> NdJsonEvent("status",           payload = StatusEvent(phase = "start",  message = "Starting meal proposal for: ${event.prompt}"))
            is AiAgentEvent.SearchingRecipes -> NdJsonEvent("status",           payload = StatusEvent(phase = "search", message = "Searching for matching recipes"))
            is AiAgentEvent.RecipesFound     -> NdJsonEvent("status",           payload = StatusEvent(phase = "search", message = "Found ${event.count} matching recipes"))
            is AiAgentEvent.LlmCallStarted   -> NdJsonEvent("status",           payload = StatusEvent(phase = "llm",    message = "Calling LLM (${event.modelName})"))
            is AiAgentEvent.LlmResponse      -> NdJsonEvent("status",           payload = StatusEvent(phase = "llm",    message = "LLM responded"))
            is AiAgentEvent.ResponseToken    -> NdJsonEvent("response.token",   payload = event.token)
            is AiAgentEvent.RecipeSelected   -> NdJsonEvent("recipe.selected",  payload = mapOf("recipeId" to event.recipeId, "name" to event.recipe?.name))
            is AiAgentEvent.NextActions      -> NdJsonEvent("next.actions",     payload = event.actions)
            is AiAgentEvent.PlanReady        -> NdJsonEvent("final",            payload = event.proposals)
            is AiAgentEvent.PlanFailed       -> NdJsonEvent("error",            payload = StatusEvent(phase = "error",  message = event.reason))
        }
        return "${envelope.toJson()}\n".toByteArray(Charsets.UTF_8)
    }
}

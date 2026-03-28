package dev.wkrzywiec.mealplanner.plan.application

import dev.wkrzywiec.mealplanner.plan.AiAgentEvent
import dev.wkrzywiec.mealplanner.plan.MealPlanner
import dev.wkrzywiec.mealplanner.plan.RecipeProposals
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
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/planner")
class MealPlannerController(
    private val mealPlanner: MealPlanner,
    private val mapper: AiAgentEventMapper,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val executor: Executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

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
        val finalProposals = proposals ?: run {
            log.error { "Meal planning stream completed without producing a PlanReady event for prompt='$prompt'" }
            throw IllegalStateException("Failed to generate meal plan for the given prompt")
        }
        return ResponseEntity.ok(finalProposals)
    }

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
                    sseEmitter.send(mapper.toSseEvent(event))
                }
            } catch (e: Exception) {
                log.warn(e) { "SSE stream failed" }
                try {
                    sseEmitter.send(mapper.toSseEvent(AiAgentEvent.PlanFailed(e.message ?: "Unknown error")))
                } catch (sendException: Exception) {
                    log.debug(sendException) { "Failed to send SSE error event after stream failure" }
                }
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
                    out.write(mapper.toNdJsonLine(event))
                    out.flush()
                }
            } catch (e: Exception) {
                log.warn(e) { "NDJSON stream failed" }
                out.write(mapper.toNdJsonLine(AiAgentEvent.PlanFailed(e.message ?: "Unknown error")))
                out.flush()
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-ndjson"))
            .header("Cache-Control", "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(body)
    }
}

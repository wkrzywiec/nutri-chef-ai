package dev.wkrzywiec.mealplanner.plan

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AcknowledgementAgent(
    private val builder: ChatClient.Builder,
    @Value("\${meal-planner.ai.low-cost-model}") private val lowCostModel: String,
) {
    companion object {
        private val log = logger {}
    }

    /**
     * Streams a brief acknowledgement that the user's request was received.
     * Calls [onToken] for each streamed text token.
     */
    fun execute(
        userPrompt: String,
        onToken: (String) -> Unit,
    ) {
        log.info { "Streaming acknowledgement from LLM (low-cost model: $lowCostModel)..." }
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
            .doOnNext { token -> onToken(token) }
            .blockLast()
    }
}

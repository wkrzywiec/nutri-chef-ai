package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.shared.config.toJson
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class RationaleAgent(
    private val builder: ChatClient.Builder,
) {
    companion object {
        private val log = logger {}
    }

    /**
     * Streams a brief explanation of why [selectedRecipes] fit [userPrompt].
     * Calls [onToken] for each streamed text token.
     */
    fun execute(
        userPrompt: String,
        selectedRecipes: List<Recipe>,
        onToken: (String) -> Unit,
    ) {
        log.info { "Streaming rationale from LLM..." }
        builder
            .build()
            .prompt()
            .system(
                """
                You are a nutrition assistant explaining meal planning choices.
                Write a brief overall explanation of why the selected recipes fit the user's query,
                covering nutritional focus and overall strategy. Plain text only, no JSON, no bullet points.
                SELECTED RECIPES: ${selectedRecipes.toJson()}
                """.trimIndent(),
            ).user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .stream()
            .content()
            .doOnNext { token -> onToken(token) }
            .blockLast()
    }
}

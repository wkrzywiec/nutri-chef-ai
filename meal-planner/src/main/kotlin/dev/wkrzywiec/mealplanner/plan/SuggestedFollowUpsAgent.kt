package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.shared.config.toJson
import dev.wkrzywiec.mealplanner.shared.config.toObject
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SuggestedFollowUpsAgent(
    private val builder: ChatClient.Builder,
    @Value("\${meal-planner.ai.low-cost-model}") private val lowCostModel: String,
) {
    companion object {
        private val log = logger {}
    }

    /**
     * Asks the LLM to suggest 2–3 follow-up actions based on [selectedRecipes] and [userPrompt].
     * Returns the list of action strings, or `null` when the LLM response cannot be parsed.
     */
    fun execute(
        userPrompt: String,
        selectedRecipes: List<Recipe>,
    ): List<String> {
        log.info { "Requesting next actions from LLM (low-cost model: $lowCostModel)..." }
        val answer =
            builder
                .build()
                .prompt()
                .options(OpenAiChatOptions.builder().model(lowCostModel).build())
                .system(
                    """
                    You are a nutrition assistant suggesting follow-up actions for a meal plan.
                    
                    RESPONSE FORMAT: Respond with valid JSON in exactly this structure:
                    {
                      "suggestedFollowUps": ["suggested action 1", "suggested action 2"]
                    }
                    
                    REQUIREMENTS:
                    - Suggest 2-3 relevant next actions for the user based on the selected recipes
                    - Respond in the same language as the user's request
                    - Return only valid JSON, no additional text
                    
                    SELECTED RECIPES: ${selectedRecipes.toJson()}
                    """.trimIndent(),
                ).user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
                .call()
                .content()
        log.info { "Next actions from LLM:\n$answer" }

        val selection = answer?.let { runCatching { it.toObject<RawSuggestedFollowUps>() }.getOrNull() }
        if (selection == null) {
            log.warn { "Failed to parse next actions from LLM response: $answer" }
            return emptyList()
        }

        return selection.suggestedFollowUps
    }

    private data class RawSuggestedFollowUps(
        val suggestedFollowUps: List<String>,
    )
}

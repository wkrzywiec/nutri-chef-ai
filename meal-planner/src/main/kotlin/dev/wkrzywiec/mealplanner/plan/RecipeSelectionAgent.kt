package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.shared.config.toJson
import dev.wkrzywiec.mealplanner.shared.config.toObject
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RecipeSelectionAgent(
    private val builder: ChatClient.Builder,
) {
    companion object {
        private val log = logger {}
    }

    /**
     * Asks the LLM to pick the 3–5 best fitting recipes from [recipes] for the given [userPrompt].
     * Returns the matched [RecipeEntry] list, or `null` when the LLM response cannot be parsed.
     */
    fun execute(
        userPrompt: String,
        recipes: List<Recipe>,
    ): List<RecipeEntry>? {
        log.info { "Requesting recipe selection from LLM..." }
        val answer =
            builder
                .build()
                .prompt()
                .system(
                    """
                    You are a nutrition assistant that selects the best fitting recipes for meal planning.
                    
                    RESPONSE FORMAT: Respond with valid JSON in exactly this structure:
                    {
                      "recipeIds": ["uuid-1", "uuid-2"]
                    }
                    
                    REQUIREMENTS:
                    - Select 3-5 most suitable recipe IDs from the provided list
                    - Use the exact recipe IDs provided
                    - Respond in the same language as the user's request
                    - Return only valid JSON, no additional text
                    
                    RECIPES: ${recipes.toJson()}
                    """.trimIndent(),
                ).user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
                .call()
                .content()
        log.info { "Recipe selection from LLM:\n$answer" }

        val selection = answer?.let { runCatching { it.toObject<RawRecipeSelection>() }.getOrNull() }
        if (selection == null) {
            log.warn { "Failed to parse recipe selection from LLM response: $answer" }
            return null
        }

        return selection.recipeIds.mapNotNull { recipeId ->
            val recipe = recipes.find { it.id == recipeId } ?: return@mapNotNull null
            RecipeEntry(recipe = recipe)
        }
    }

    private data class RawRecipeSelection(
        val recipeIds: List<UUID>,
    )
}

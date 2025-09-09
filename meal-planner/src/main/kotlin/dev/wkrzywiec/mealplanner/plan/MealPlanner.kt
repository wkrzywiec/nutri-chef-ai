package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import dev.wkrzywiec.mealplanner.shared.config.toJson
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class MealPlanner(
    private val recipeSearch: RecipeSearchFacade,
    private val builder: ChatClient.Builder
) {

    companion object {
        private val log = logger {}
    }

    fun proposeMeal(userPrompt: String): String {
        log.info { "Searching for best meal proposals based on user prompt... '$userPrompt'"}
        val recipes = recipeSearch.findRecipes(userPrompt, 10)

        log.info {" Found ${recipes.size} recipes with ids: ${recipes.map { it.id }} "}
        log.info { "Calling an AI agent..."}
        val answer = builder.build().prompt()
            .system(
                """
                You are a nutrition assistant. 
                Select best fitting recipes and provide the rationale for each of them. Focus on nutrition benefits
                If goal is not provided assume the regular person intake.
                Answer in the same language as user asked.
                There are recipes attached to this question.
                In return provide the id of each recipe (UUID).
                RECIPES: ${recipes.toJson()}
                """.trimIndent()
            )
            .user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .call()
            .content()
        return answer ?: "No response from AI"
    }
}
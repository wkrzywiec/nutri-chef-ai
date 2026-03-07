package dev.wkrzywiec.mealplanner.plan

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import dev.wkrzywiec.mealplanner.shared.config.toJson
import dev.wkrzywiec.mealplanner.shared.config.toObject
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import java.util.UUID

data class RawRecipeProposals(val response: String, val nextActions: List<String>, val recipes: List<RecipeIdWithRationale>)

class RecipeIdWithRationale(val rationale: String, val recipeId: UUID)

data class RecipeProposals(val response: String, val nextActions: List<String>, val recipes: List<RecipeWithRationale>)

data class RecipeWithRationale(val rationale: String, val recipe: Recipe?)

@Service
class MealPlanner(
    private val recipeSearch: RecipeSearchFacade,
    private val builder: ChatClient.Builder
) {

    companion object {
        private val log = logger {}
    }

    fun proposeMeal(userPrompt: String): RecipeProposals? {
        log.info { "Searching for best meal proposals based on user prompt... '$userPrompt'"}
        val recipes = recipeSearch.findRecipes(userPrompt, 10)

        log.info {" Found ${recipes.size} recipes with ids: ${recipes.map { it.id }} "}
        log.info { "Calling an AI agent..."}
        val answer = builder.build().prompt()
            .system(
                """
                You are a nutrition assistant that selects the best fitting recipes for meal planning.
                
                TASK: Analyze the provided recipes and select the most suitable ones based on nutritional benefits and user goals. If no specific goal is provided, assume recommendations for a regular healthy adult diet.
                
                RESPONSE FORMAT: You must respond with valid JSON in exactly this structure:
                {
                  "response": "Brief overall explanation of your selection strategy and nutritional focus",
                  "nextActions": ["suggested action 1", "suggested action 2"],
                  "recipes": [
                    {
                      "recipeId": "recipe-uuid-here",
                      "rationale": "Detailed explanation of why this recipe was selected, focusing on specific nutritional benefits"
                    }
                  ]
                }
                
                REQUIREMENTS:
                - Select 3-5 most suitable recipes from the provided list
                - Focus on nutritional balance, variety, and health benefits
                - Include specific nutritional reasons in each rationale
                - Suggest 2-3 relevant next actions for the user
                - Use the exact recipe IDs provided
                - Respond in the same language as the user's request
                - Return only valid JSON, no additional text
                
                RECIPES: ${recipes.toJson()}
                """.trimIndent()
            )
            .user { u -> u.text("USER_QUERY: \"$userPrompt\"") }
            .call()
            .content()
        log.info { "Response from AI Agent:\n $answer" }
        return answer?.let {
            mapToRawRecipeProposals(it)
        }?.let {
            mapToRecipeProposals(it)
        }
    }

    private fun mapToRawRecipeProposals(answer: String): RawRecipeProposals? {
        return answer.toObject<RawRecipeProposals>()
    }

    private fun mapToRecipeProposals(raw: RawRecipeProposals): RecipeProposals? {
        val fullRecipes = recipeSearch.findRecipes(raw.recipes.map { it.recipeId })
        return RecipeProposals(
            response = raw.response,
            nextActions = raw.nextActions,
            recipes = raw.recipes.map { RecipeWithRationale(rationale = it.rationale, recipe = fullRecipes.find { recipe -> recipe.id == it.recipeId}) }
        )
    }
}
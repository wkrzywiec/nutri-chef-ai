package dev.wkrzywiec.mealplanner.search

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/recipes")
class RecipeController(
    val recipeSearchService: RecipeSearchService
) {

    @GetMapping("/search")
    fun findRecipes(
        @RequestParam prompt: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<RecipeSearchResponse?> {
        val matches = recipeSearchService
            .findRecipes(prompt, limit)

        val response: RecipeSearchResponse? = RecipeSearchResponse(matches = matches, totalFound = matches.size)

        return ResponseEntity.ok<RecipeSearchResponse?>(response)
    }

    @GetMapping("/plan")
    fun planMeals(
        @RequestParam prompt: String?,
    ): ResponseEntity<String> {
        if (prompt != null) {
            val plan = recipeSearchService.generateMealPlan(prompt)
            return ResponseEntity.ok(plan)
        }
        return ResponseEntity.ok("Nothing was provided")
    }
}

data class RecipeSearchResponse(
    val matches: List<RecipeMatch?>? = null,
    val totalFound: Int = 0,
    val searchTimeMs: Long = 0,
)

data class RecipeMatch(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val similarityScore: Double? = null,
    val ingredients: Any? = null,
    val instructions: Any? = null,
    val sourceUrl: String? = null,
    val servings: String? = null,
)
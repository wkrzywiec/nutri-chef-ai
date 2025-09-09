package dev.wkrzywiec.mealplanner.search.application

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/recipes")
class RecipeController(
    val recipeSearchService: RecipeSearchFacade
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
}

data class RecipeSearchResponse(
    val matches: List<Recipe>? = null,
    val totalFound: Int = 0,
    // todo cound time
    val searchTimeMs: Long = 0,
)
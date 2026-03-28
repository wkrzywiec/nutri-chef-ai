package dev.wkrzywiec.mealplanner.search.application

import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.system.measureTimeMillis

@RestController
@RequestMapping("/api/recipes")
class RecipeController(
    val recipeSearchFacade: RecipeSearchFacade,
) {
    @GetMapping("/search")
    fun findRecipes(
        @RequestParam prompt: String?,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ResponseEntity<RecipeSearchResponse> {
        lateinit var matches: List<Recipe>

        val duration =
            measureTimeMillis {
                matches =
                    recipeSearchFacade
                        .findRecipes(prompt, limit)
            }

        val response = RecipeSearchResponse(matches = matches, totalFound = matches.size, searchTimeMs = duration)

        return ResponseEntity.ok(response)
    }
}

data class RecipeSearchResponse(
    val matches: List<Recipe> = emptyList(),
    val totalFound: Int = 0,
    val searchTimeMs: Long = 0,
)

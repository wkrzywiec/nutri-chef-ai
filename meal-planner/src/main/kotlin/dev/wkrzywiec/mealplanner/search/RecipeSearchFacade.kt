package dev.wkrzywiec.mealplanner.search

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RecipeSearchFacade(
    private val embeddingEngine: EmbeddingEngine,
    private val repository: RecipeRepository
) {

    companion object {
        private val log = logger {}
    }

    fun findRecipes(prompt: String?, limit: Int): List<Recipe> {
        if (prompt == null || prompt.isBlank()) return emptyList()
        log.info { "Searching for $limit recipes based on a user prompt: '$prompt'..."}

        val promptEmbedding: FloatArray = embeddingEngine.embed(prompt)
        log.info { "User input was embedded. Looking for closest recipes..." }
        return repository.findNearestRecipes(promptEmbedding, limit)
    }

    fun findRecipes(recipeIds: List<UUID>): List<Recipe> {
        return repository.findRecipes(recipeIds)
    }
}
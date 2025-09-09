package dev.wkrzywiec.mealplanner.search

import dev.wkrzywiec.mealplanner.shared.config.toObject
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class RecipeRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun findNearestRecipes(promptEmbedding: FloatArray, limit: Int = 10): List<Recipe> {
        val sql = """
            SELECT
              r.id AS recipe_id,
              r.name,
              r.description,
              re.similarity_score,
              r.ingredients,
              r.instructions,
              r.source_url,
              r.servings,
              r.tags
            FROM (
                SELECT
                  recipe_id,
                  MIN(embedding <=> CAST(:prompt_embedding AS vector)) AS similarity_score
                FROM recipe_embeddings
                GROUP BY recipe_id
                ORDER BY similarity_score ASC
                LIMIT :limit
            ) AS re
            JOIN recipe r ON r.id = re.recipe_id
            ORDER BY re.similarity_score ASC;
        """

        val params = MapSqlParameterSource()
            .addValue("prompt_embedding", promptEmbedding)
            .addValue("limit", limit)

        return jdbcTemplate.query(sql, params, rowMapper())
    }

    fun rowMapper() = RowMapper<Recipe> { rs, _ ->
        Recipe(
            id = UUID.fromString(rs.getString("recipe_id")),
            name = rs.getString("name"),
            description = rs.getString("description"),
            ingredients = parseFromJsonb<Ingredients>(rs.getString("ingredients")),
            instructions = rs.getString("instructions").toObject<List<Map<String, Any>>>(),
            sourceUrl = rs.getString("source_url"),
            servings = rs.getString("servings"),
            tags =  parseFromJsonb<String>(rs.getString("tags")),
            similarityScore = rs.getDouble("similarity_score"),
        )
    }

    private inline fun <reified T> parseFromJsonb(jsonbString: String?): List<T> {
        if (jsonbString.isNullOrBlank()) return emptyList()

        return try {
            jsonbString.toObject<List<T>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun findRecipes(recipeIds: List<UUID>): List<Recipe> {
        val sql = """
            SELECT
              r.id AS recipe_id,
              r.name,
              r.description,
              r.ingredients,
              r.instructions,
              r.source_url,
              r.servings,
              r.tags
            FROM recipe r
            WHERE r.id IN (:recipe_ids)
        """
        val params = MapSqlParameterSource()
            .addValue("recipe_ids", recipeIds)

        return jdbcTemplate.query(sql, params, rowMapperWithoutSimilarityScore())
    }

    fun rowMapperWithoutSimilarityScore() = RowMapper<Recipe> { rs, _ ->
        Recipe(
            id = UUID.fromString(rs.getString("recipe_id")),
            name = rs.getString("name"),
            description = rs.getString("description"),
            ingredients = parseFromJsonb<Ingredients>(rs.getString("ingredients")),
            instructions = rs.getString("instructions").toObject<List<Map<String, Any>>>(),
            sourceUrl = rs.getString("source_url"),
            servings = rs.getString("servings"),
            tags =  parseFromJsonb<String>(rs.getString("tags"))
        )
    }
}
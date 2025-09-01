package dev.wkrzywiec.mealplanner.search

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class RecipeSearchService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val embeddingEngine: EmbeddingEngine,
) {

    companion object {
        private val log = logger {}
        val objectMapper = objectMapper()
    }

    fun findRecipesByPrompt(prompt: String?, limit: Int): List<RecipeMatch?> {
        if (prompt == null || prompt.isBlank()) return mutableListOf()

        val promptEmbedding: FloatArray = embeddingEngine.embed(prompt)
        log.info { "Fetched vectors: ${promptEmbedding.size}"}

        val sql = """
            SELECT
              r.id AS recipe_id,
              r.name,
              r.description,
              re.similarity_score,
              r.ingredients,
              r.instructions,
              r.source_url,
              r.servings
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

        val rowMapper = RowMapper<RecipeMatch> { rs, _ ->
            RecipeMatch(
                id = rs.getString("recipe_id"),
                name = rs.getString("name"),
                description = rs.getString("description"),
                similarityScore = rs.getDouble("similarity_score"),
                ingredients = rs.getString("ingredients").toObject<List<Map<String, Any>>>(),
                instructions = rs.getString("instructions").toObject<List<Map<String, Any>>>(),
                sourceUrl = rs.getString("source_url"),
                servings = rs.getString("servings")
            )
        }

        val results = jdbcTemplate.query(sql,  params, rowMapper)
        return results
    }
}

fun objectMapper() =
    jsonMapper {
        addModule(kotlinModule())
        findAndAddModules()
        serializationInclusion(JsonInclude.Include.NON_NULL)
        defaultDateFormat(StdDateFormat())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        build()
    }

fun Any.toJson(): String = objectMapper().writeValueAsString(this)

inline fun <reified T> String.toObject(): T = objectMapper().readValue(this)
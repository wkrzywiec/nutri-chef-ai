package dev.wkrzywiec.mealplanner.search

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.springframework.ai.chat.client.ChatClient
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class RecipeSearchService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val embeddingEngine: EmbeddingEngine,
    private val builder: ChatClient.Builder
) {

    companion object {
        private val log = logger {}
        val objectMapper = objectMapper()
    }

    fun findRecipes(prompt: String?, limit: Int): List<RecipeMatch?> {
        if (prompt == null || prompt.isBlank()) return mutableListOf()

        val promptEmbedding: FloatArray = embeddingEngine.embed(prompt)
        log.info { "Fetched vectors: ${promptEmbedding.size}" }

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

        val results = jdbcTemplate.query(sql, params, rowMapper)
        return results
    }

    fun generateMealPlan(userPrompt: String): String {
        val queryAsVector = embeddingEngine.embed(userPrompt)
        val recipes = nearestRecipies(queryAsVector, 20)

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

    data class Recipe(
        val id: String,
        val name: String,
        val description: String?,
        val ingredients: List<Ingredients>,
        val tags: List<String>
    )

    data class Ingredients(val section: String, val ingredients: String)

    private fun nearestRecipies(vec: FloatArray, k: Int): List<Recipe> {
        val sql = """
            SELECT r.id, r.name, r.description, r.ingredients, r.tags
            FROM recipe_embeddings re
            LEFT JOIN recipe r ON r.id = re.recipe_id
            WHERE chunk_type IN ('name', 'description', 'ingredients')
            ORDER BY embedding <=> CAST(:v AS vector)
            LIMIT :k
        """
        val p = MapSqlParameterSource()
            .addValue("v", vec)  // "(0.12,-0.3 …)"
            .addValue("k", k)

        return jdbcTemplate.query(sql, p) { rs, _ ->
            Recipe(
                id = rs.getString("id"),
                name = rs.getString("name"),
                description = rs.getString("description"),
                ingredients = parseIngredientsFromJsonb(rs.getString("ingredients")),
                tags = parseTagsFromJsonb(rs.getString("tags"))
            )
        }
    }

    private fun parseIngredientsFromJsonb(jsonbString: String?): List<Ingredients> {
        if (jsonbString.isNullOrBlank()) return emptyList()

        return try {
            val typeRef = object : TypeReference<List<Ingredients>>() {}
            objectMapper.readValue(jsonbString, typeRef)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTagsFromJsonb(jsonbString: String?): List<String> {
        if (jsonbString.isNullOrBlank()) return emptyList()

        return try {
            val typeRef = object : TypeReference<List<String>>() {}
            objectMapper.readValue(jsonbString, typeRef)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun FloatArray.asPgVector() =
        joinToString(prefix = "(", postfix = ")", separator = ",")

    private fun buildPrompt(
        userPrompt: String,
        vectors: List<Pair<Long, FloatArray>>
    ): String = buildString {
        appendLine("USER_QUERY: \"$userPrompt\"")
        appendLine()
        appendLine("RECIPE_VECTORS_JSON:")
        appendLine("[")
        vectors.forEachIndexed { i, (id, vec) ->
            append("  {\"id\":$id,\"vec\":${vec.contentToString()}}")
            if (i != vectors.lastIndex) append(",")
            appendLine()
        }
        appendLine("]")
        appendLine()
        appendLine(
            "INSTRUCTIONS:\n" +
                    "- You are a nutrition assistant. Each recipe is identified only by its vector.\n" +
                    "- Use geometric relations among vectors (similarity to each other and to the query)\n" +
                    "  to craft a balanced daily meal plan (breakfast/lunch/dinner/snack).\n" +
                    "- Output JSON with keys: mealType, chosenRecipeVectorId, rationale."
        )
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
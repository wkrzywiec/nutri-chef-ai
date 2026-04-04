package dev.wkrzywiec.mealplanner.search

import dev.wkrzywiec.mealplanner.shared.config.toJson
import org.springframework.jdbc.core.JdbcTemplate

class TestRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun save(recipe: RecipeTestData) {
        jdbcTemplate.update(
            """
            INSERT INTO recipe (id, name, description, source, source_url, servings, ingredients, instructions, tags)
            VALUES (CAST(? AS uuid), ?, ?, 'test', ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb))
            """.trimIndent(),
        ) { ps ->
            ps.setString(1, recipe.getId().toString())
            ps.setString(2, recipe.getName())
            ps.setString(3, recipe.getDescription())
            ps.setString(4, recipe.getSourceUrl())
            ps.setString(5, recipe.getServings())
            ps.setString(6, recipe.getIngredients().toJson())
            ps.setString(7, recipe.getInstructions().toJson())
            ps.setString(8, recipe.getTags().toJson())
        }
        jdbcTemplate.update(
            "INSERT INTO recipe_embeddings (recipe_id, embedding) VALUES (CAST(? AS uuid), CAST(? AS vector))",
        ) { ps ->
            ps.setString(1, recipe.getId().toString())
            ps.setString(2, recipe.literalEmbedding())
        }
    }

    fun clean() {
        jdbcTemplate.execute("TRUNCATE recipe_scraper, recipe_ingredient, recipe_embeddings, recipe")
    }
}

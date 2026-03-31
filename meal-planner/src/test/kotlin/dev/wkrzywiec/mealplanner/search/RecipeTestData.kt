package dev.wkrzywiec.mealplanner.search

import dev.wkrzywiec.mealplanner.shared.config.toJson
import java.util.UUID

class RecipeTestData private constructor() {

    private var id: UUID = UUID.randomUUID()
    private var name: String = "Grilled Chicken Salad"
    private var description: String? = "A healthy grilled chicken salad"
    private var ingredients: List<Ingredients> = listOf(
        Ingredients(section = "Main", ingredients = listOf("chicken breast", "lettuce", "tomato"))
    )
    private var instructions: List<Instruction> = listOf(
        Instruction(section = "Cook", steps = listOf("Grill chicken", "Toss salad"))
    )
    private var sourceUrl: String? = "https://example.com/grilled-chicken-salad"
    private var servings: String? = "2 servings"
    private var tags: List<String> = listOf("healthy", "salad")
    private var similarityScore: Double? = null

    companion object {
        fun aRecipe(): RecipeTestData = RecipeTestData()
    }

    fun withId(id: UUID): RecipeTestData {
        this.id = id
        return this
    }

    fun withName(name: String): RecipeTestData {
        this.name = name
        return this
    }

    fun withDescription(description: String?): RecipeTestData {
        this.description = description
        return this
    }

    fun withIngredients(vararg ingredients: Ingredients): RecipeTestData {
        this.ingredients = ingredients.toList()
        return this
    }

    fun withInstructions(vararg instructions: Instruction): RecipeTestData {
        this.instructions = instructions.toList()
        return this
    }

    fun withSourceUrl(sourceUrl: String?): RecipeTestData {
        this.sourceUrl = sourceUrl
        return this
    }

    fun withServings(servings: String?): RecipeTestData {
        this.servings = servings
        return this
    }

    fun withTags(vararg tags: String): RecipeTestData {
        this.tags = tags.toList()
        return this
    }

    fun withSimilarityScore(similarityScore: Double?): RecipeTestData {
        this.similarityScore = similarityScore
        return this
    }

    fun domain(): Recipe = Recipe(
        id = id,
        name = name,
        description = description,
        ingredients = ingredients,
        instructions = instructions,
        sourceUrl = sourceUrl,
        servings = servings,
        tags = tags,
        similarityScore = similarityScore,
    )

    fun getId(): UUID = id

    fun toRecipeInsertSql(): String =
        """
        INSERT INTO recipe (id, name, description, source, source_url, servings, ingredients, instructions, tags)
        VALUES (
            '$id',
            '${name.replace("'", "''")}',
            '${description?.replace("'", "''") ?: ""}',
            'test',
            '${sourceUrl ?: ""}',
            '${servings ?: ""}',
            '${ingredients.toJson()}',
            '${instructions.toJson()}',
            '${tags.toJson()}'
        )
        """.trimIndent()

    fun toEmbeddingInsertSql(embeddingLiteral: String): String =
        """
        INSERT INTO recipe_embeddings (recipe_id, embedding)
        VALUES ('$id', '$embeddingLiteral')
        """.trimIndent()

    fun toRecipeDeleteSql(): String =
        "DELETE FROM recipe WHERE id = '$id'"

    fun toEmbeddingDeleteSql(): String =
        "DELETE FROM recipe_embeddings WHERE recipe_id = '$id'"
}

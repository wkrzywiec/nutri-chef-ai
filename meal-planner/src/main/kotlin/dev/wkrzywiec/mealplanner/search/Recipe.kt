package dev.wkrzywiec.mealplanner.search

import java.util.UUID

data class Recipe(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val ingredients: List<Ingredients>,
    val instructions: List<Instruction>,
    val sourceUrl: String? = null,
    val source: String? = null,
    val imageUrl: String? = null,
    val servings: String? = null,
    val tags: List<String> = emptyList(),
    val similarityScore: Double? = null,
)

data class Ingredients(
    val section: String,
    val ingredients: List<String>,
)

data class Instruction(
    val section: String,
    val steps: List<String>,
)

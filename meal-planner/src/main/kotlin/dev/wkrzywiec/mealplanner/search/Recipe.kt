package dev.wkrzywiec.mealplanner.search

import java.util.UUID

data class Recipe(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val ingredients: List<Ingredients>,
    val instructions: Any? = null,
    val sourceUrl: String? = null,
    val servings: String? = null,
    val tags: List<String> = emptyList(),
    val similarityScore: Double? = null,
)

data class Ingredients(val section: String, val ingredients: String)

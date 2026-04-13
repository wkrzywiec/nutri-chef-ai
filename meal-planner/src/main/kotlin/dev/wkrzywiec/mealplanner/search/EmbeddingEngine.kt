package dev.wkrzywiec.mealplanner.search

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Component

interface EmbeddingEngine {
    fun embed(prompt: String): FloatArray
}

@Component
class OpenAIEmbeddingEngine(
    private val embeddingModel: EmbeddingModel,
) : EmbeddingEngine {
    override fun embed(prompt: String): FloatArray {
        val response = embeddingModel.embedForResponse(listOf(prompt))
        return response.result.output
    }
}

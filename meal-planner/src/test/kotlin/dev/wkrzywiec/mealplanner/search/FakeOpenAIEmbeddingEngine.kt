package dev.wkrzywiec.mealplanner.search

import dev.mokksy.aimocks.openai.MockOpenai
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.api.OpenAiApi

class FakeOpenAIEmbeddingEngine(
    private val openaiMock: MockOpenai,
): EmbeddingEngine {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val embeddingModel: EmbeddingModel = OpenAiEmbeddingModel(
        OpenAiApi
            .builder()
            .apiKey("demo-key")
            .baseUrl(openaiMock.baseUrl())
            .build()
    )

    override fun embed(prompt: String): FloatArray {
        log.info {"Embedding OpenAI stub for prompt '$prompt'..."}
        val response = embeddingModel.embedForResponse(listOf(prompt))
        return response.result.output
    }

    fun stubEmbedding(testEmbedding: FloatArray) {
        log.info {"Stubbing OpenAI embedding..."}
        openaiMock.embeddings {
            model = "text-embedding-3-small"
        } responds {
            embeddings(testEmbedding.toList())
        }
    }
}
package dev.wkrzywiec.mealplanner.plan.application

import dev.mokksy.aimocks.openai.MockOpenai
import dev.wkrzywiec.mealplanner.IntegrationTest
import dev.wkrzywiec.mealplanner.search.FakeOpenAIEmbeddingEngine
import dev.wkrzywiec.mealplanner.search.RecipeTestData.Companion.aRecipe
import dev.wkrzywiec.mealplanner.search.TestRepository
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.time.Duration.Companion.milliseconds

class MealPlannerControllerComponentTest : IntegrationTest() {
    companion object {
        private const val EMBEDDING_DIM = 1536

        private val testEmbedding: FloatArray =
            FloatArray(EMBEDDING_DIM).also { it[0] = 1.0f }

        private val testEmbeddingLiteral: String =
            testEmbedding.joinToString(separator = ",", prefix = "[", postfix = "]")

        val testRecipe = aRecipe()
    }

    @Autowired
    private lateinit var testRepository: TestRepository

    @Autowired
    private lateinit var openai: MockOpenai

    @Autowired
    private lateinit var fakeEmbeddingEngine: FakeOpenAIEmbeddingEngine

    @Test
    fun `GET single returns JSON meal plan`() {
        testRepository.save(testRecipe, testEmbeddingLiteral)
        fakeEmbeddingEngine.stubEmbedding(testEmbedding)
        stubOpenAiChatCalls()

        val body =
            Given {
                accept("application/json")
                queryParam("prompt", "healthy meal")
            } When {
                get("/api/planner/single")
            } Then {
                statusCode(200)
                contentType("application/json")
            } Extract {
                body().asString()
            }

        assertThat(body).contains("Grilled Chicken Salad")
        assertThat(body).contains("Buy groceries")
    }

    @Test
    fun `GET single with SSE accept header streams events`() {
        testRepository.save(testRecipe, testEmbeddingLiteral)
        fakeEmbeddingEngine.stubEmbedding(testEmbedding)
        stubOpenAiChatCalls()

        Given {
            accept("text/event-stream")
            queryParam("prompt", "healthy meal")
        } When {
            get("/api/planner/single")
        } Then {
            statusCode(200)
            contentType("text/event-stream")
        }
    }

    @Test
    fun `GET single with ndjson accept header streams ndjson lines`() {
        testRepository.save(testRecipe, testEmbeddingLiteral)
        fakeEmbeddingEngine.stubEmbedding(testEmbedding)
        stubOpenAiChatCalls()

        val body =
            Given {
                accept("application/x-ndjson")
                queryParam("prompt", "healthy meal")
            } When {
                get("/api/planner/single")
            } Then {
                statusCode(200)
                contentType("application/x-ndjson")
            } Extract {
                body().asString()
            }

        val lines = body.lines().filter { it.isNotBlank() }
        assertThat(lines).isNotEmpty()
        lines.forEach { line -> assertThat(line).startsWith("{") }
    }

    // ---- MockOpenai stubs -------------------------------------------------

    private fun stubOpenAiChatCalls() {
        // Call 1: streaming narrative – system prompt contains the "explanation" phrase
        openai.completion {
            systemMessageContains("brief overall explanation")
            userMessageContains("healthy meal")
        } respondsStream {
            responseChunks = listOf("Here", " is", " a", " healthy", " meal", " plan.")
            finishReason = "stop"
            delayBetweenChunks = 5.milliseconds
        }

        // Call 2: batch JSON selection – system prompt contains "RESPONSE FORMAT"
        openai.completion {
            systemMessageContains("RESPONSE FORMAT")
            userMessageContains("healthy meal")
        } responds {
            assistantContent =
                """{"nextActions":["Buy groceries","Prep ingredients"],"recipeIds":["${testRecipe.getId()}"]}"""
            finishReason = "stop"
        }
    }
}

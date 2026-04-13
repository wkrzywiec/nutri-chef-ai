package dev.wkrzywiec.mealplanner.plan.application

import dev.mokksy.aimocks.openai.MockOpenai
import dev.wkrzywiec.mealplanner.IntegrationTest
import dev.wkrzywiec.mealplanner.search.FakeOpenAIEmbeddingEngine
import dev.wkrzywiec.mealplanner.search.RecipeTestData
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
    @Autowired
    private lateinit var testRepository: TestRepository

    @Autowired
    private lateinit var openai: MockOpenai

    @Autowired
    private lateinit var fakeEmbeddingEngine: FakeOpenAIEmbeddingEngine

    @Test
    fun `GET single returns JSON meal plan`() {
        val testRecipe = aRecipe()
        val prompt = "json meal plan"

        testRepository.save(testRecipe)
        fakeEmbeddingEngine.stubEmbedding(testRecipe.embedding())
        stubOpenAiChatCalls(testRecipe, prompt)

        val body =
            Given {
                accept("application/json")
                queryParam("prompt", prompt)
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
        val testRecipe = aRecipe()
        val prompt = "sse meal plan"

        testRepository.save(testRecipe)
        fakeEmbeddingEngine.stubEmbedding(testRecipe.embedding())
        stubOpenAiChatCalls(testRecipe, prompt)

        Given {
            accept("text/event-stream")
            queryParam("prompt", prompt)
        } When {
            get("/api/planner/single")
        } Then {
            statusCode(200)
            contentType("text/event-stream")
        }
    }

    @Test
    fun `GET single with ndjson accept header streams ndjson lines`() {
        val testRecipe = aRecipe()
        val prompt = "ndjson meal plan"

        testRepository.save(testRecipe)
        fakeEmbeddingEngine.stubEmbedding(testRecipe.embedding())
        stubOpenAiChatCalls(testRecipe, prompt)

        val body =
            Given {
                accept("application/x-ndjson")
                queryParam("prompt", prompt)
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

    private fun stubOpenAiChatCalls(
        testRecipe: RecipeTestData,
        prompt: String,
    ) {
        // Call 1: acknowledgement – system prompt asks to acknowledge receipt of the request
        openai.completion {
            systemMessageContains("searching for suitable")
            userMessageContains(prompt)
        } respondsStream {
            responseChunks = listOf("Got", " it!", " Searching", " for", " recipes", " now.")
            finishReason = "stop"
            delayBetweenChunks = 5.milliseconds
        }

        // Call 2: recipe selection – system prompt asks to select the best fitting recipes
        openai.completion {
            systemMessageContains("selects the best fitting recipes")
            userMessageContains(prompt)
        } responds {
            assistantContent = """{"recipeIds":["${testRecipe.getId()}"]}"""
            finishReason = "stop"
        }

        // Call 3: rationale – system prompt contains "brief overall explanation"
        openai.completion {
            systemMessageContains("brief overall explanation")
            userMessageContains(prompt)
        } respondsStream {
            responseChunks = listOf("Here", " is", " a", " healthy", " meal", " plan.")
            finishReason = "stop"
            delayBetweenChunks = 5.milliseconds
        }

        // Call 4: suggested follow-ups – system prompt asks to suggest follow-up actions
        openai.completion {
            systemMessageContains("suggesting follow-up actions")
            userMessageContains(prompt)
        } responds {
            assistantContent = """{"suggestedFollowUps":["Buy groceries","Prep ingredients"]}"""
            finishReason = "stop"
        }
    }
}

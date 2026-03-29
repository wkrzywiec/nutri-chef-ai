package dev.wkrzywiec.mealplanner.plan.application

import dev.mokksy.aimocks.openai.MockOpenai
import dev.wkrzywiec.mealplanner.search.EmbeddingEngine
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.restassured.RestAssured
import io.restassured.filter.log.RequestLoggingFilter
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
class MealPlannerControllerIT {

    companion object {

        private const val EMBEDDING_DIM = 1536

        private val testEmbedding: FloatArray =
            FloatArray(EMBEDDING_DIM).also { it[0] = 1.0f }

        private val testEmbeddingLiteral: String =
            testEmbedding.joinToString(separator = ",", prefix = "[", postfix = "]")

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer("pgvector/pgvector:pg17")
                .withDatabaseName("meal_planner")
                .withUsername("postgres")
                .withPassword("postgres")

        private val openai = MockOpenai()

        val recipeId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

        @JvmStatic
        @AfterAll
        fun stopMockServer() {
            openai.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideOpenAiBaseUrl(registry: DynamicPropertyRegistry) {
            // MockOpenai.baseUrl() returns "http://localhost:{port}/v1";
            // Spring AI appends "/v1" itself, so we strip the suffix to avoid doubling.
            registry.add("spring.ai.openai.base-url") { openai.baseUrl().removeSuffix("/v1") }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = port
        RestAssured.filters(RequestLoggingFilter(), ResponseLoggingFilter())
        insertTestRecipe()
        stubOpenAiEmbedding()
        stubOpenAiChatCalls()
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DELETE FROM recipe_embeddings WHERE recipe_id = '$recipeId'")
        jdbcTemplate.execute("DELETE FROM recipe WHERE id = '$recipeId'")
    }

    // ---- Tests ------------------------------------------------------------

    @Test
    fun `GET single returns JSON meal plan`() {
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

    // ---- DB helpers -------------------------------------------------------

    private fun insertTestRecipe() {
        jdbcTemplate.execute(
            """
            INSERT INTO recipe (id, name, description, source, source_url, servings, ingredients, instructions, tags)
            VALUES (
                '$recipeId',
                'Grilled Chicken Salad',
                'A healthy grilled chicken salad',
                'test',
                'https://example.com/grilled-chicken-salad',
                '2 servings',
                '[{"section":"Main","ingredients":["chicken breast","lettuce","tomato"]}]',
                '[{"section":"Cook","steps":["Grill chicken","Toss salad"]}]',
                '["healthy","salad"]'
            )
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            """
            INSERT INTO recipe_embeddings (recipe_id, embedding)
            VALUES ('$recipeId', '$testEmbeddingLiteral')
            """.trimIndent(),
        )
    }

    // ---- MockOpenai stubs -------------------------------------------------

    private fun stubOpenAiEmbedding() {
        // Embedding call: OpenAIEmbeddingEngine embeds the user prompt before the DB search.
        // We return the same vector stored in recipe_embeddings so cosine distance is 0.
        openai.embeddings {
            model = "text-embedding-3-small"
        } responds {
            embeddings(testEmbedding.toList())
        }
    }

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
                """{"nextActions":["Buy groceries","Prep ingredients"],"recipeIds":["$recipeId"]}"""
            finishReason = "stop"
        }
    }
}
package dev.wkrzywiec.mealplanner.plan.application

import dev.mokksy.aimocks.openai.MockOpenai
import dev.wkrzywiec.mealplanner.search.Ingredients
import dev.wkrzywiec.mealplanner.search.Instruction
import dev.wkrzywiec.mealplanner.search.Recipe
import dev.wkrzywiec.mealplanner.search.RecipeSearchFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MealPlannerControllerIT {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer("pgvector/pgvector:pg16")
                .withDatabaseName("meal_planner")
                .withUsername("postgres")
                .withPassword("postgres")

        private val openai = MockOpenai()

        private val recipeId = UUID.fromString("11111111-1111-1111-1111-111111111111")

        private val fakeRecipe =
            Recipe(
                id = recipeId,
                name = "Grilled Chicken Salad",
                description = "A healthy grilled chicken salad",
                ingredients = listOf(Ingredients("Main", listOf("chicken breast", "lettuce", "tomato"))),
                instructions = listOf(Instruction("Cook", listOf("Grill chicken", "Toss salad"))),
            )

        @JvmStatic
        @AfterAll
        fun stopMockServer() {
            openai.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideOpenAiBaseUrl(registry: DynamicPropertyRegistry) {
            // MockOpenai.baseUrl() returns "http://localhost:{port}/v1", but Spring AI appends "/v1"
            // itself, so we strip the trailing "/v1" to avoid a doubled path.
            registry.add("spring.ai.openai.base-url") { openai.baseUrl().removeSuffix("/v1") }
        }
    }

    @MockBean
    private lateinit var recipeSearchFacade: RecipeSearchFacade

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @BeforeEach
    fun setUp() {
        `when`(recipeSearchFacade.findRecipes(anyString(), anyInt())).thenReturn(listOf(fakeRecipe))

        // Call 1: streaming narrative tokens (system prompt mentions "explanation")
        openai.completion {
            systemMessageContains("brief overall explanation")
            userMessageContains("healthy meal")
        } respondsStream {
            responseChunks = listOf("Here", " is", " a", " healthy", " meal", " plan.")
            finishReason = "stop"
            delayBetweenChunks = 5.milliseconds
        }

        // Call 2: batch JSON selection (system prompt mentions "RESPONSE FORMAT")
        openai.completion {
            systemMessageContains("RESPONSE FORMAT")
            userMessageContains("healthy meal")
        } responds {
            assistantContent =
                """{"nextActions":["Buy groceries","Prep ingredients"],"recipeIds":["$recipeId"]}"""
            finishReason = "stop"
        }
    }

    @Test
    fun `GET single returns JSON meal plan`() {
        val response =
            restTemplate.getForEntity(
                "/api/planner/single?prompt=healthy+meal",
                String::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType?.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue()
        assertThat(response.body).contains("Grilled Chicken Salad")
        assertThat(response.body).contains("Buy groceries")
    }

    @Test
    fun `GET single with SSE accept header streams events`() {
        val request =
            RequestEntity
                .get(URI("/api/planner/single?prompt=healthy+meal"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .build()

        val response = restTemplate.exchange(request, String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType?.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue()
        assertThat(response.body).isNotNull()
    }

    @Test
    fun `GET single with ndjson accept header streams ndjson lines`() {
        val request =
            RequestEntity
                .get(URI("/api/planner/single?prompt=healthy+meal"))
                .accept(MediaType.parseMediaType("application/x-ndjson"))
                .build()

        val response = restTemplate.exchange(request, String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType?.isCompatibleWith(MediaType.parseMediaType("application/x-ndjson"))).isTrue()
        // Each non-blank line should be a JSON object
        val lines = response.body!!.lines().filter { it.isNotBlank() }
        assertThat(lines).isNotEmpty()
        lines.forEach { line ->
            assertThat(line).startsWith("{")
        }
    }
}

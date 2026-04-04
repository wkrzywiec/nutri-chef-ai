package dev.wkrzywiec.mealplanner

import dev.mokksy.aimocks.openai.MockOpenai
import dev.wkrzywiec.mealplanner.search.TestConfig
import dev.wkrzywiec.mealplanner.search.TestRepository
import io.restassured.RestAssured
import io.restassured.filter.log.RequestLoggingFilter
import io.restassured.filter.log.ResponseLoggingFilter
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestConfig::class, IntegrationTest.MockOpenaiConfig::class)
abstract class IntegrationTest {
    companion object {
        private val openai = MockOpenai()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer("pgvector/pgvector:pg17")
                .withDatabaseName("meal_planner")
                .withUsername("postgres")
                .withPassword("postgres")

        @JvmStatic
        @DynamicPropertySource
        fun overrideOpenAiBaseUrl(registry: DynamicPropertyRegistry) {
            // MockOpenai.baseUrl() returns "http://localhost:{port}/v1";
            // Spring AI appends "/v1" itself, so we strip the suffix to avoid doubling.
            registry.add("spring.ai.openai.base-url") { openai.baseUrl().removeSuffix("/v1") }
        }
    }

    @Autowired
    private lateinit var testRepository: TestRepository

    @LocalServerPort
    private var port: Int = 0

    @TestConfiguration
    class MockOpenaiConfig {
        @Bean
        fun mockOpenai(): MockOpenai = openai
    }

    @BeforeEach
    fun setUpIntegrationTest() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = port
        RestAssured.filters(RequestLoggingFilter(), ResponseLoggingFilter())

        testRepository.clean()
    }
}

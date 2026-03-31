package dev.wkrzywiec.mealplanner.search

import dev.mokksy.aimocks.openai.MockOpenai
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate

@TestConfiguration
class TestConfig(
    private val jdbcTemplate: JdbcTemplate,
    private val openai: MockOpenai,
) {

    @Bean
    fun testRepository(): TestRepository = TestRepository(jdbcTemplate)

    @Bean
    @Primary
    fun embeddingEngine(): FakeOpenAIEmbeddingEngine = FakeOpenAIEmbeddingEngine(openai)
}

package dev.wkrzywiec.mealplanner.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
class ExecutorConfig {
    @Bean(destroyMethod = "shutdown")
    fun mealPlannerExecutor(): ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
}

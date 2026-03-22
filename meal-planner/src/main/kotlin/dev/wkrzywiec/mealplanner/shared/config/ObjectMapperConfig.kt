package dev.wkrzywiec.mealplanner.shared.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ObjectMapperConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    companion object {
        val objectMapper = objectMapper()
    }
}

fun objectMapper() =
    jsonMapper {
        addModule(kotlinModule())
        findAndAddModules()
        serializationInclusion(JsonInclude.Include.NON_NULL)
        defaultDateFormat(StdDateFormat())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        build()
    }

fun Any.toJson(): String = objectMapper().writeValueAsString(this)

inline fun <reified T> String.toObject(): T = objectMapper().readValue(this)
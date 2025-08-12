package dev.wkrzywiec.mealplanner

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MealplannerApplication

fun main(args: Array<String>) {
	runApplication<MealplannerApplication>(*args)
}

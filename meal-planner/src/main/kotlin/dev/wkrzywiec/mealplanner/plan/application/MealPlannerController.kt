package dev.wkrzywiec.mealplanner.plan.application

import dev.wkrzywiec.mealplanner.plan.MealPlanner
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/planner")
class MealPlannerController(
    private val mealPlanner: MealPlanner
) {

    @GetMapping("/single")
    fun proposeMeal(
        @RequestParam prompt: String,
    ): ResponseEntity<String> {
        val plan = mealPlanner.proposeMeal(prompt)
        return ResponseEntity.ok(plan)
    }
}
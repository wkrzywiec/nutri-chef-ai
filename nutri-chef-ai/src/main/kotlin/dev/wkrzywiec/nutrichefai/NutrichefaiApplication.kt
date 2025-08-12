package dev.wkrzywiec.nutrichefai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NutrichefaiApplication

fun main(args: Array<String>) {
	runApplication<NutrichefaiApplication>(*args)
}

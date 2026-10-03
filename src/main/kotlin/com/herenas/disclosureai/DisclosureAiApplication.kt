package com.herenas.disclosureai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class DisclosureAiApplication

fun main(args: Array<String>) {
	runApplication<DisclosureAiApplication>(*args)
}

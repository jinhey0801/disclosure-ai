package com.herenas.disclosureai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DisclosureAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DisclosureAiApplication.class, args);
	}
}

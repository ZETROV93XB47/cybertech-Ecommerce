package com.novatech.cybertech;

import org.springframework.boot.SpringApplication;

public class TestCybertechApplication {

	public static void main(String[] args) {
		SpringApplication.from(CyberTechApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

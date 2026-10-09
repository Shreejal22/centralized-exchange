package com.shreejal.exchange;

import org.springframework.boot.SpringApplication;

public class TestCentralizedApplication {

	public static void main(String[] args) {
		SpringApplication.from(CentralizedApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

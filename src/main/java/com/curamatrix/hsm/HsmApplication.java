package com.curamatrix.hsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HsmApplication {

	public static void main(String[] args) {
		SpringApplication.run(HsmApplication.class, args);
	}

}

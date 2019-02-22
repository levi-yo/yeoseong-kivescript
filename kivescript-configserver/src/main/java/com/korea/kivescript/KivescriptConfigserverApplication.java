package com.korea.kivescript;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;


@EnableConfigServer
@SpringBootApplication
public class KivescriptConfigserverApplication {

	public static void main(String[] args) {
		SpringApplication.run(KivescriptConfigserverApplication.class, args);
	}

}

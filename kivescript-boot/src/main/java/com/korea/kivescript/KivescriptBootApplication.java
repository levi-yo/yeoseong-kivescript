package com.korea.kivescript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KivescriptBootApplication implements CommandLineRunner{
	
	private static Logger logger = LoggerFactory.getLogger(KivescriptBootApplication.class);
	
	@Value("${kivescript.version}")
	private String kiveScriptVersion;
	
	
	public static void main(String[] args) {
		SpringApplication.run(KivescriptBootApplication.class, args);
	}

	
	
	@Override
	public void run(String... args) throws Exception {
		// TODO Auto-generated method stub
		logger.info("=================================");
		logger.info("== kivescript's version is {} =",kiveScriptVersion);
		logger.info("=================================");
	}
	
}

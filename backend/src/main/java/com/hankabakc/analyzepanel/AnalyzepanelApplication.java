package com.hankabakc.analyzepanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class AnalyzepanelApplication {

	private static final Logger log = LoggerFactory.getLogger(AnalyzepanelApplication.class);

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(AnalyzepanelApplication.class);
		boolean isCliCommand = false;
		for (String arg : args) {
			if (arg.startsWith("--recover-admin") || arg.startsWith("--create-admin")) {
				isCliCommand = true;
				break;
			}
		}

		if (isCliCommand) {
			app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
			app.run(args);
		} else {
			app.run(args);
			log.info("#########################################");
			log.info("##         UYGULAMA BASLATILDI         ##");
			log.info("#########################################");
		}
	}

}

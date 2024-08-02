/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2021-2024 Armin Müller & Eric Wündisch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.trustdeck.ace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * This class is the main entry point for the pseudonymization service ACE.
 * 
 * @author Armin Müller
 *
 */
@SpringBootApplication
@EnableScheduling
public class Application {
	
	/** The logger object for this class. */
	private static Logger logger = LoggerFactory.getLogger(Application.class);
	
	/**
	 * The pseudonymization service's entry-point.
	 * Starts the database-controller and the controller for the REST-API.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// Starting the application. This also calls the necessary 
		// methods in other classes automatically.
		SpringApplication.run(Application.class, args);
		
		// Finished start-up
		logger.info("Start up procedure completed. Waiting for requests ...");
	}
}

/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller & Eric Wündisch
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

package org.trustdeck.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * This class represents an exception when a duplicate of an entity type was found.
 * 
 * @author Armin Müller
 *
 */
public class DuplicateEntityTypeException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = 1353355183765463010L;
	
	/** The name of the entity type that was duplicated. */
	@Getter
	@Setter
	private String name;
	
	/** 
	 * Constructor that also defines information of the entity type causing the exception.
	 * 
	 * @param name the name of the exception-causing entity type
	 */
	public DuplicateEntityTypeException(String name) {
		super("A duplicate of the entity type was found.");
		
		this.name = name;
	}
}

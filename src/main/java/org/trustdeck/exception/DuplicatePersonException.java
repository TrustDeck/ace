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
 * This class represents an exception when a duplicate of a person was found.
 * 
 * @author Armin Müller
 *
 */
public class DuplicatePersonException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = -5879079573152206083L;
	
	/** The identifier of the person that was duplicated. */
	@Getter
	@Setter
	private String identifier;
	
	/** The idType of the person that was duplicated. */
	@Getter
	@Setter
	private String idType;
	
	/** 
	 * Constructor that also defines information of the person causing the exception.
	 * 
	 * @param identifier the identifier of the exception-causing person
	 * @param idType the idType of the exception-causing person
	 */
	public DuplicatePersonException(String identifier, String idType) {
		super("A duplicate of the pseudonym was found.");
		
		this.identifier = identifier;
		this.idType = idType;
	}
}

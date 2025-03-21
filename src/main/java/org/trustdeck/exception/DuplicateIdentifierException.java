/*
 * Trust Deck Services
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
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
 * This class represents an exception when a duplicate of an identifier & idType combination was found.
 * 
 * @author Armin Müller
 *
 */

public class DuplicateIdentifierException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = 2995010909750546954L;

	/** The name of the domain where the duplicated pseudonym is in. */
	@Getter
	@Setter
	private String domainName;
	
	/** The identifier of the pseudonym that was duplicated. */
	@Getter
	@Setter
	private String identifier;
	
	/** The idType of the pseudonym that was duplicated. */
	@Getter
	@Setter
	private String idType;
	
	/** 
	 * Constructor that also defines information of the pseudonym causing the exception.
	 * @param domainName the name of the domain where the exception-causing pseudonym is in
	 * @param identifier the identifier of the exception-causing pseudonym
	 * @param idType the idType of the exception-causing pseudonym
	 */
	public DuplicateIdentifierException(String domainName, String identifier, String idType) {
		super("A duplicate of the identifier & idType combination was found.");
		
		this.domainName = domainName;
		this.identifier = identifier;
		this.idType = idType;
	}
}

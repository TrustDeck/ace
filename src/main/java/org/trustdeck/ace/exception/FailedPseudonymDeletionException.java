/*
 * ACE - Advanced Confidentiality Engine
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

package org.trustdeck.ace.exception;

/**
 * This class represents an exception when the deletion of a pseudonym failed.
 * 
 * @author Armin Müller
 *
 */
public class FailedPseudonymDeletionException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = -4508733941993130349L;
	
	/** The name of the domain where the pseudonym that can't be deleted is in. */
	private String domainName;
	
	/** The identifier of the pseudonym that can't be deleted. */
	private String identifier;
	
	/** The idType of the pseudonym that can't be deleted. */
	private String idType;
	
	/** 
	 * Constructor that also defines information about the pseudonym causing the exception.
	 * @param domainName the name of the domain where the exception-causing pseudonym is in
	 * @param identifier the identifier of the exception-causing pseudonym
	 * @param idType the idType of the exception-causing pseudonym
	 */
	public FailedPseudonymDeletionException(String domainName, String identifier, String idType) {
		super("The deletion of a pseudonym was unsuccessful.");
		
		this.domainName = domainName;
		this.identifier = identifier;
		this.idType = idType;
	}

	/**
	 * @return the domainName
	 */
	public String getDomainName() {
		return domainName;
	}

	/**
	 * @return the identifier
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * @return the idType
	 */
	public String getIdType() {
		return idType;
	}

	/**
	 * @param domainName the domainName to set
	 */
	public void setDomainName(String domainName) {
		this.domainName = domainName;
	}

	/**
	 * @param identifier the identifier to set
	 */
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	/**
	 * @param idType the idType to set
	 */
	public void setIdType(String idType) {
		this.idType = idType;
	}
}

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
 * This class represents an exception when a domain couldn't be found.
 * 
 * @author Armin Müller
 *
 */
public class DomainNotFoundException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = 5207053811632277880L;
	
	/** The name of the domain that was requested. */
	private String domainName;
	
	/** 
	 * Constructor that also defines the name of the domain causing the exception.
	 * @param domainName the name of the exception-causing domain
	 */
	public DomainNotFoundException(String domainName) {
		super("The requested domain couldn't be found.");
		
		this.domainName = domainName;
	}

	/**
	 * @return the domainName
	 */
	public String getDomainName() {
		return domainName;
	}

	/**
	 * @param domainName the domainName to set
	 */
	public void setDomainName(String domainName) {
		this.domainName = domainName;
	}
}

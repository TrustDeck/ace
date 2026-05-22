/*
 * Trust Deck Services
 * Copyright 2026 Armin Müller and Eric Wündisch
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
 * This class represents an exception when handling permissions.
 * 
 * @author Armin Müller
 */
public class PermissionManagementException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = 1560357341099711388L;

	/** The name or abbreviation of the resource (e.g. a domain or a project) where the error arose. */
	@Getter
	@Setter
	private String causingResource;

	/**
	 * Constructor that also defines the name of the domain causing the exception.
	 * @param domainName the name of the exception-causing domain
	 */
	public PermissionManagementException(String causingResource) {
		super("There was an error while handling permissions for resource \"" + causingResource + "\".");
		
		this.causingResource = causingResource;
	}
}

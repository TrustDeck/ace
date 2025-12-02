/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller and Eric Wündisch
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
 * This class represents an exception when handling OIDC 
 * rights and roles while handling a project went wrong.
 * 
 * @author Armin Müller
 */
public class ProjectOIDCException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = 1659840914095638965L;
	
	/** The name of the project where the error arose. */
	@Getter
	@Setter
	private String projectName;

	/**
	 * Constructor that also defines the name of the project causing the exception.
	 * @param projectName the name of the exception-causing domain
	 */
	public ProjectOIDCException(String projectName) {
		super("There was an error while handling OIDC rights and roles for the project.");
		
		this.projectName = projectName;
	}
}

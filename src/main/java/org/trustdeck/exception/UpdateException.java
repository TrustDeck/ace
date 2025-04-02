/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller
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
 * This class represents an exception when the update of an object failed.
 * 
 * @author Armin Müller
 *
 */
public class UpdateException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = -7316912262191823141L;
	
	/** The object for which the update failed and the exception was raised. */
	@Getter
	@Setter
	private Object object;
	
	/** 
	 * Constructor that also defines information about the cause of the exception.
	 * 
	 * @param object the object for which the update failed
	 */
	public UpdateException(Object object) {
		super("The update of a database object was unsuccessful.");
		
		this.object = object;
	}
}

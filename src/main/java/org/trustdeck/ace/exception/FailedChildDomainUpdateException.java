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
 * This class represents an exception when the update of a child domain failed.
 * 
 * @author Armin Müller
 *
 */
public class FailedChildDomainUpdateException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = -2780694585237683527L;
	
	/** The name of the child domain that couldn't be updated. */
	private String childName;
	
	/** 
	 * Constructor that also defines the name of the child domain causing the exception.
	 * @param childName the name of the exception-causing domain
	 */
	public FailedChildDomainUpdateException(String childName) {
		super("The update of a child domain was unsuccessful.");
		
		this.childName = childName;
	}

	/**
	 * @return the childName
	 */
	public String getChildName() {
		return childName;
	}

	/**
	 * @param childName the childName to set
	 */
	public void setChildName(String childName) {
		this.childName = childName;
	}
}

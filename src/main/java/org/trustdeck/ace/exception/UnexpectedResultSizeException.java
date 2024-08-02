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
 * This class represents an exception when the expected and actually received result sizes differ.
 * 
 * @author Armin Müller
 *
 */
public class UnexpectedResultSizeException extends RuntimeException {

	/** Exception UID. */
	private static final long serialVersionUID = 5077979347589252209L;
	
	/** The expected result size. */
	private Integer expected;
	
	/** The actual result size. */
	private Integer actual;
	
	/** 
	 * Constructor that also defines the expected and actual result sizes.
	 * @param expected the expected result size
	 * @param actual the actual result size
	 */
	public UnexpectedResultSizeException(Integer expected, Integer actual) {
		super("The result of an operation has an unexpected size.");
		
		this.expected = expected;
		this.actual = actual;
	}

	/**
	 * @return the expected
	 */
	public Integer getExpectedSize() {
		return expected;
	}

	/**
	 * @return the actual
	 */
	public Integer getActualSize() {
		return actual;
	}

	/**
	 * @param expected the expected to set
	 */
	public void setExpectedSize(Integer expected) {
		this.expected = expected;
	}

	/**
	 * @param actual the actual to set
	 */
	public void setActualSize(Integer actual) {
		this.actual = actual;
	}
}

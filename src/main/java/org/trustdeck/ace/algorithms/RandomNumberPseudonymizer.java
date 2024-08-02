/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2023-2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.algorithms;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;

import org.trustdeck.ace.jooq.generated.tables.pojos.Domain;

/**
 * This class provides a pseudonymization by assigning random numbers as pseudonyms.
 * 
 * @author Armin Müller
 *
 */
@Slf4j
public class RandomNumberPseudonymizer extends Pseudonymizer {
	
	/**
	 * Basic constructor.
	 * Padding is turned <b>off</b>.
	 * (The pseudonym value length is automatically set to the default, as well as the padding character.)
	 */
	public RandomNumberPseudonymizer() {
		super(false, Pseudonymizer.DEFAULT_VALUE_LENGTH, Pseudonymizer.DEFAULT_PADDING_CHAR, null);
	}
	
	/**
	 * Basic constructor.
	 * Padding is turned <b>off</b>.
	 * (The pseudonym value length is automatically set to the default, as well as the padding character.)
	 * 
	 * @param domainName the name of the domain to which the record belongs to
	 */
	public RandomNumberPseudonymizer(String domainName) {
		super(false, Pseudonymizer.DEFAULT_VALUE_LENGTH, Pseudonymizer.DEFAULT_PADDING_CHAR, domainName);
	}

	/**
	 * A constructor that allows to set whether or not the created pseudonyms should be padded 
	 * as well as the desired pseudonym value length and the character used for padding.
	 * 
	 * @param paddingWanted whether or not the pseudonyms should be padded to a certain length
	 * @param pseudonymValueLength the desired length of the pseudonym value
	 * @param paddingChar the character that should be used
	 * @param domainName the name of the domain to which the record belongs to
	 */
	public RandomNumberPseudonymizer(boolean paddingWanted, int pseudonymValueLength, char paddingChar, String domainName) {
		super(paddingWanted, pseudonymValueLength, paddingChar, domainName);
	}
	


	/**
	 * A constructor that allows to set whether or not the created pseudonyms should be padded 
	 * as well as the desired pseudonym value length and the character used for padding.
	 * 
	 * @param paddingWanted whether or not the pseudonyms should be padded to a certain length
	 * @param pseudonymValueLength the desired length of the pseudonym value
	 * @param paddingChar the character that should be used
	 * @param domainName the name of the domain to which the record belongs to
	 * @param numberOfRetries the number of times the method retries finding a random number that is not already in use
	 */
	public RandomNumberPseudonymizer(boolean paddingWanted, int pseudonymValueLength, char paddingChar, String domainName, int numberOfRetries) {
		super(paddingWanted, pseudonymValueLength, paddingChar, domainName);
	}
	
	/**
	 * Basic constructor.
	 * All necessary variables are directly retrieved from the domain object.
	 * 
	 * @param paddingWanted whether or not the pseudonyms should be padded to a certain length
	 * @param domain the domain object
	 */
	public RandomNumberPseudonymizer(boolean paddingWanted, Domain domain) {
		super(paddingWanted, domain);
	}
	
	/**
	 * Creates a random number pseudonym from the given identifier.
	 */
	@Override
	public String pseudonymize(String identifier, String domainPrefix) {
		SecureRandom rnd = new SecureRandom(identifier.getBytes());
		
		// Generate a random number
		String pseudonym = "";

		// Due to a Long being only 64 bit, this won't produce anything longer than a string with 19 numbers.
		// Append multiple random values to get the desired length.
		int psnLength = getPseudonymValueLength();
		while (psnLength > 0) {
			Double upperBound = psnLength >= 19 ? Long.MAX_VALUE : Math.pow(10, psnLength);
			String p = String.valueOf(rnd.nextLong(upperBound.longValue()));
			pseudonym += p;
			psnLength -= p.length();
		}
		
		return domainPrefix + correctPseudonymLength(pseudonym);
	}
}
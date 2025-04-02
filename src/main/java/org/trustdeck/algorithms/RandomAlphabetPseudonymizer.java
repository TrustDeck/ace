/*
 * Trust Deck Services
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

package org.trustdeck.algorithms;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;

import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
import org.trustdeck.jooq.generated.tables.pojos.Domain;

/**
 * This class provides a pseudonymization by assigning random letters as pseudonyms.
 * 
 * @author Armin Müller
 *
 */
@Slf4j
public class RandomAlphabetPseudonymizer extends Pseudonymizer {
	
	/**
	 * Basic constructor.
	 * Padding is turned <b>off</b>.
	 * (The pseudonym value length is automatically set to the default, as well as the padding character.)
	 */
	public RandomAlphabetPseudonymizer(String alphabet) {
		super(false, Pseudonymizer.DEFAULT_VALUE_LENGTH, Pseudonymizer.DEFAULT_PADDING_CHAR, null);
	}
	
	/**
	 * Basic constructor.
	 * Padding is turned <b>off</b>.
	 * (The pseudonym value length is automatically set to the default, as well as the padding character.)
	 * 
	 * @param domainName the name of the domain to which the record belongs to
	 */
	public RandomAlphabetPseudonymizer(String domainName, String alphabet) {
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
	public RandomAlphabetPseudonymizer(boolean paddingWanted, int pseudonymValueLength, char paddingChar, String domainName, String alphabet) {
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
	public RandomAlphabetPseudonymizer(boolean paddingWanted, int pseudonymValueLength, char paddingChar, String domainName, int numberOfRetries, String alphabet) {
		super(paddingWanted, pseudonymValueLength, paddingChar, domainName);
	}
	
	/**
	 * Basic constructor.
	 * Number of retries is set to default, all other variables are directly retrieved from the domain object.
	 * 
	 * @param paddingWanted whether or not the pseudonyms should be padded to a certain length
	 * @param domain the domain object
	 */
	public RandomAlphabetPseudonymizer(boolean paddingWanted, Domain domain) {
		super(paddingWanted, domain);
	}
	
	/**
	 * Basic constructor.
	 * Number of retries is set to default, all other variables are directly retrieved from the algorithm object.
	 * 
	 * @param paddingWanted whether or not the pseudonyms should be padded to a certain length
	 * @param algorithm the algorithm object
	 */
	public RandomAlphabetPseudonymizer(boolean paddingWanted, Algorithm algorithm) {
		super(paddingWanted, algorithm);
	}
	
	/**
	 * Creates a random character pseudonym for the given identifier.
	 * The identifier is not actively used in this method.
	 */
	@Override
	public String pseudonymize(String identifier, String domainPrefix) {
		String pseudonym = getRandomString(getPseudonymValueLength(), getAlphabet());
		
		// Check if successful
		if (!pseudonym.equals(PSEUDONYMIZATION_FAILED)) {
			return domainPrefix + pseudonym;
		}
		
		log.warn("Random-Alphabet-Pseudonymizer: pseudonym generation failed!");
		return null;
	}
	
	/**
	 * Method to generate a random string of a desired length using the alphabet provided for this instance.
	 * 
	 * @param length the desired length of the random character string
	 * @return a string containing a random sequence of characters from this instance's alphabet
	 */
	private String getRandomString(int length, String alphabet) {
		SecureRandom rnd = new SecureRandom();
		StringBuilder sb = new StringBuilder();
		
		if (alphabet == null || alphabet.isBlank()) {
			return PSEUDONYMIZATION_FAILED;
		}
			
		// Generate random string
		for (int j = 0; j < length; j++) {
            // Pick random characters from the provided alphabet and append
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
		
		return sb.toString();
	}
}
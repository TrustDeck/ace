/*
 * Trust Deck Services
 * Copyright 2024 Armin Müller & Eric Wündisch
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

/**
 * A class that provides a check digit calculator 
 * for arbitrary alphabets of even length.
 * 
 * @author Armin Müller
 */
@Slf4j
public class LuhnModNCheckDigit extends LuhnCheckDigit {

	public LuhnModNCheckDigit(String alphabet) {
		super((alphabet.length() % 2 == 0) ? alphabet : alphabet.substring(0, alphabet.length() - 1));
		
		if (alphabet.length() % 2 == 1) {
			log.debug("Since the alphabet was not of even length, the last character was removed.");
		}
	}
}

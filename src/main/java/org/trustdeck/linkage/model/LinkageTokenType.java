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

package org.trustdeck.linkage.model;

/**
 * This enum represents the different kinds of linkage tokens that can be
 * generated for record linkage.
 * 
 * @author Armin Müller
 */
public enum LinkageTokenType {

	/** Token type used for normalized values. */
    NORM,

    /** Token type used for phonetic encodings. */
    PHONETIC,

    /** Token type used for candidate blocking. */
    BLOCK,

	/** Token type used for HMAC-protected exact PPRL values. */
	PPRL_EXACT,

	/** Token type used for encoded n-gram Bloom filters. */
	PPRL_BLOOM,

	/** Token type used for HMAC-protected PPRL blocking values. */
	PPRL_BLOCK;

	/**
	 * Returns the name representation of the token type used for the database.
	 * 
	 * @return the lowercase representation of the enum name
	 */
	public String dbName() {
		return name().toLowerCase();
	}

	/**
	 * Checks whether this token type can be used for candidate generation.
	 * 
	 * @return {@code true} if the token type is a blocking token type, {@code false} otherwise
	 */
	public boolean isCandidateGenerationToken() {
		return this == BLOCK || this == PPRL_BLOCK;
	}

	/**
	 * Converts a stored database value into a linkage token type.
	 * 
	 * @param value the stored token type value
	 * @return the corresponding linkage token type
	 */
	public static LinkageTokenType fromDbValue(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Linkage token type is missing.");
		}

		return switch (value.trim().toLowerCase()) {
			case "norm" -> NORM;
			case "phonetic" -> PHONETIC;
			case "block" -> BLOCK;
			case "pprl_exact" -> PPRL_EXACT;
			case "pprl_bloom" -> PPRL_BLOOM;
			case "pprl_block" -> PPRL_BLOCK;
			default -> throw new IllegalArgumentException("Unknown linkage token type: " + value);
		};
	}
}

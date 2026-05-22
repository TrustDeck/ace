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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class contains the configuration for the privacy-preserving record linkage
 * encoding of one linkage-enabled field.
 * 
 * The configuration controls whether a value is encoded as an HMAC exact token
 * or as an n-gram Bloom filter. The Bloom filter can additionally be split into
 * bands that are used as blocking tokens during candidate generation.
 * 
 * The class uses defaults when not given.
 * 
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PPRLConfig {

	/** The default PPRL method for fuzzy matching string fields. */
	public static final String METHOD_NGRAM_BLOOM_FILTER = "ngramBloomFilter";

	/** The PPRL method for exact matching fields. */
	public static final String METHOD_HMAC_EXACT = "hmacExact";

	/** The PPRL method to use, e.g. ngramBloomFilter or hmacExact. */
	@Builder.Default
	private String method = METHOD_NGRAM_BLOOM_FILTER;

	/** The n-gram size used for Bloom filter generation. */
	@Builder.Default
	private int n = 2;

	/** The number of bits in the Bloom filter. */
	@Builder.Default
	private int length = 1024;

	/** The number of HMAC-derived bit positions set per n-gram. */
	@Builder.Default
	private int hashPositions = 10;

	/** The number of bits per Bloom filter band used for protected blocking. */
	@Builder.Default
	private int bandSize = 32;

	/** Indicates whether an additional HMAC exact token should be generated for n-gram Bloom filter fields. */
	@Builder.Default
	private boolean exact = false;
}

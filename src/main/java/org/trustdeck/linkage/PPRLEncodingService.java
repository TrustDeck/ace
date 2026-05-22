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

package org.trustdeck.linkage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.trustdeck.linkage.model.PPRLConfig;
import org.trustdeck.utils.Assertion;

import lombok.extern.slf4j.Slf4j;

/**
 * This service provides privacy-preserving encoding methods for record linkage.
 * It can create HMAC-protected exact tokens, HMAC-seeded n-gram Bloom filters,
 * and HMAC-protected Bloom filter band tokens for candidate generation.
 * 
 * @author Armin Müller
 */
@Service
@Slf4j
public class PPRLEncodingService {

	/** The HMAC algorithm used for token generation. */
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	/** The version prefix used for context separation of the generated PPRL values. */
	private static final String CONTEXT_PREFIX = "trustdeck|pprl|v1";

	/** The secret key used for HMAC-based PPRL token generation. */
	@Value("${app.linkage.pprl.secret:}")
	private String pprlSecret;

	/**
	 * Generates a protected HMAC exact token for a normalized value.
	 * 
	 * @param projectId the project ID used for context separation
	 * @param entityTypeId the entity type ID used for context separation
	 * @param tag the semantic linkage tag of the field
	 * @param normalized the normalized field value
	 * @return the Base64URL-encoded HMAC token
	 */
	public String hmacExact(int projectId, int entityTypeId, String tag, String normalized) {
		return hmacToken(projectId, entityTypeId, tag, "exact", normalized);
	}

	/**
	 * Builds an n-gram Bloom filter for a normalized value.
	 * Each n-gram sets multiple bit positions derived from HMAC-SHA-256.
	 * 
	 * @param projectId the project ID used for context separation
	 * @param entityTypeId the entity type ID used for context separation
	 * @param tag the semantic linkage tag of the field
	 * @param normalized the normalized value to encode
	 * @param config the PPRL configuration for the field
	 * @return the generated Bloom filter
	 */
	public BitSet buildBloomFilter(int projectId, int entityTypeId, String tag, String normalized, PPRLConfig config) {
		PPRLConfig conf = effectiveConfig(config);
		BitSet bloomFilter = new BitSet(conf.getLength());

		// Calculate the ngrams for the given normalized message
		for (String ngram : ngrams(normalized, conf.getN())) {
			// Set the Bloom filter bits at the hmac(ngram + i)-positions
			for (int i = 0; i < conf.getHashPositions(); i++) {
				String message = context(projectId, entityTypeId, tag, "ngram", ngram + "|" + i);
				byte[] digest = hmacBytes(message);
				int position = Math.floorMod(ByteBuffer.wrap(digest, 0, Integer.BYTES).getInt(), conf.getLength());

				bloomFilter.set(position);
			}
		}

		return bloomFilter;
	}

	/**
	 * Encodes a Bloom filter as a fixed-length Base64URL string.
	 * 
	 * @param bloomFilter the Bloom filter to encode
	 * @param bitLength the configured length of the Bloom filter in bits
	 * @return the Base64URL-encoded Bloom filter bytes
	 */
	public String encodeBloomFilter(BitSet bloomFilter, int bitLength) {
		// Ensure proper length
		int byteLength = (bitLength + 7) / 8;
		byte[] bytes = Arrays.copyOf(bloomFilter.toByteArray(), byteLength);

		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Decodes a Base64URL-encoded Bloom filter.
	 * 
	 * @param encodedBloomFilter the encoded Bloom filter
	 * @return the decoded Bloom filter
	 */
	public BitSet decodeBloomFilter(String encodedBloomFilter) {
		byte[] bytes = Base64.getUrlDecoder().decode(encodedBloomFilter);
		return BitSet.valueOf(bytes);
	}

	/**
	 * Generates blocking tokens from Bloom filter bands (buckets of config.bitLength size).
	 * Empty bands are skipped to avoid overly common blocking values.
	 * 
	 * @param projectId the project ID used for context separation
	 * @param entityTypeId the entity type ID used for context separation
	 * @param tag the semantic linkage tag of the field
	 * @param bloomFilter the Bloom filter whose bands should be encoded
	 * @param config the PPRL configuration for the field
	 * @return the list of HMAC-protected band tokens
	 */
	public List<String> buildBloomBandTokens(int projectId, int entityTypeId, String tag, BitSet bloomFilter, PPRLConfig config) {
		PPRLConfig effectiveConfig = effectiveConfig(config);
		List<String> tokens = new ArrayList<>();

		int bitLength = effectiveConfig.getLength();
		int bandSize = effectiveConfig.getBandSize();
		int bandIndex = 0;

		// Iterate over the Bloom filter in bitLength increments
		for (int start = 0; start < bitLength; start += bandSize) {
			int end = Math.min(start + bandSize, bitLength);
			String bandValue = bandValue(bloomFilter, start, end);

			// Skip fully empty bands, or otherwise many unrelated records could share the same empty band token
			if (bandValue.indexOf('1') >= 0) {
				tokens.add(hmacToken(projectId, entityTypeId, tag, "band", bandIndex + "|" + bandValue));
			}

			bandIndex++;
		}

		return tokens;
	}

	/**
	 * Calculates the Dice similarity between two encoded Bloom filters, which is the 
	 * number of overlapping elements in relation to the number of elements in both sets.
	 * (2 times the number of common elements divided by the size of both sets combined.)
	 * 
	 * @param originalEncodedBloomFilter the first encoded Bloom filter
	 * @param comparingEncodedBloomFilter the second encoded Bloom filter
	 * @return the Dice similarity between 0.0 and 1.0
	 */
	public double diceSimilarity(String originalEncodedBloomFilter, String comparingEncodedBloomFilter) {
		BitSet left = decodeBloomFilter(originalEncodedBloomFilter);
		BitSet right = decodeBloomFilter(comparingEncodedBloomFilter);

		// Calculate the set of overlapping elements
		BitSet intersection = (BitSet) left.clone();
		intersection.and(right);

		// Calculate the set sizes
		int leftCount = left.cardinality();
		int rightCount = right.cardinality();
		int commonCount = intersection.cardinality();

		// Avoid division by zero
		if (leftCount + rightCount == 0) {
			return 0.0;
		}

		return (2.0 * commonCount) / (leftCount + rightCount);
	}

	/**
	 * Generates padded character n-grams for a normalized string.
	 * 
	 * @param value the normalized value
	 * @param n the n-gram size
	 * @return a list of the generated n-grams
	 */
	public List<String> ngrams(String value, int n) {
		List<String> out = new ArrayList<>();

		if (Assertion.isNullOrEmpty(value)) {
			return out;
		}

		// Sanitize n and add start and end marker to the input value
		int effectiveN = Math.max(1, n);
		String padded = "^" + value + "$";

		// Handle short inputs
		if (padded.length() <= effectiveN) {
			out.add(padded);
			return out;
		}

		// Add ngrams to the output list
		for (int i = 0; i <= padded.length() - effectiveN; i++) {
			out.add(padded.substring(i, i + effectiveN));
		}

		return out;
	}

	/**
	 * Creates a protected HMAC token with explicit context separation.
	 * 
	 * @param projectId the project ID used for context separation
	 * @param entityTypeId the entity type ID used for context separation
	 * @param tag the semantic linkage tag of the field
	 * @param category the category/purpose of the generated token (e.g., "band", "exact")
	 * @param value the value to protect
	 * @return the Base64URL-encoded HMAC token
	 */
	private String hmacToken(int projectId, int entityTypeId, String tag, String category, String value) {
		String message = context(projectId, entityTypeId, tag, category, value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes(message));
	}

	/**
	 * Creates a context-separated HMAC input message.
	 * 
	 * @param projectId the project ID used for context separation
	 * @param entityTypeId the entity type ID used for context separation
	 * @param tag the semantic linkage tag of the field
	 * @param category the category/purpose of the generated token (e.g., "band", "exact")
	 * @param value the value to protect
	 * @return the HMAC input message
	 */
	private String context(int projectId, int entityTypeId, String tag, String category, String value) {
		return CONTEXT_PREFIX + "|" + projectId + "|" + entityTypeId + "|" + tag + "|" + category + "|" + value;
	}

	/**
	 * Calculates HMAC-SHA-256 for a message.
	 * 
	 * @param message the input message
	 * @return the HMAC byte array
	 */
	private byte[] hmacBytes(String message) {
		if (Assertion.isNullOrEmpty(pprlSecret)) {
			throw new IllegalStateException("PPRL is enabled, but app.linkage.pprl.secret is not configured.");
		}

		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(pprlSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not generate PPRL HMAC token.", e);
		}
	}

	/**
	 * Converts one Bloom filter band into a deterministic bit string.
	 * 
	 * @param bloomFilter the Bloom filter
	 * @param start the inclusive start bit index
	 * @param end the exclusive end bit index
	 * @return the bit string representation of the band
	 */
	private String bandValue(BitSet bloomFilter, int start, int end) {
		StringBuilder out = new StringBuilder(end - start);

		for (int i = start; i < end; i++) {
			out.append(bloomFilter.get(i) ? '1' : '0');
		}

		return out.toString();
	}

	/**
	 * Ensures safe PPRL defaults when no explicit configuration was provided.
	 * 
	 * @param config the configured PPRL settings
	 * @return the effective PPRL settings
	 */
	private PPRLConfig effectiveConfig(PPRLConfig config) {
		if (config == null) {
			return new PPRLConfig();
		}

		config.setN(Math.max(2, config.getN()));
		config.setLength(Math.max(128, config.getLength()));
		config.setHashPositions(Math.max(1, config.getHashPositions()));
		config.setBandSize(Math.max(8, config.getBandSize()));

		return config;
	}
}

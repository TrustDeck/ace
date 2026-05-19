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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.linkage.model.LinkageFieldRule;
import org.trustdeck.linkage.model.LinkageToken;
import org.trustdeck.linkage.model.LinkageTokenType;
import org.trustdeck.linkage.model.PPRLConfig;
import org.trustdeck.utils.Assertion;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * This service generates record linkage tokens from entity instance payloads.
 * It applies linkage field rules to raw JSON values and transforms them into
 * normalized, phonetic encoded, and blocking tokens that can be used for 
 * candidate generation and candidate scoring.
 * 
 * @author Armin Müller
 */
@Service
@Slf4j
public class LinkageTokenService {

	/** The normalization service used for value normalization and phonetic encoding. */
    @Autowired
    private LinkageNormalizationService normalizationService;
    
    /** The service used to generate privacy-preserving linkage tokens. */
    @Autowired
    private PPRLEncodingService pprlEncodingService;

    /**
     * Generates record linkage tokens for a payload according to the provided linkage field rules.
     * For each linkage-enabled field, the raw input value is normalized first and then transformed
     * into one or more tokens for exact matching, phonetic matching, and blocking.
     * 
     * @param rules the linkage field rules that determine how tokens should be generated
     * @param payload the JSON payload from which the linkage tokens should be derived
     * @param projectId the (internal) database ID of the project in which the record linkage takes place
     * @param entityTypeId the (internal) database ID of the entity type of the record for which RL is done
     * @return the list of generated linkage tokens
     */
    public List<LinkageToken> buildTokens(List<LinkageFieldRule> rules, JsonNode payload, int projectId, int entityTypeId) {
        List<LinkageToken> tokens = new ArrayList<>();

        // Apply rules
        for (LinkageFieldRule rule : rules) {
        	// Retrieve the node of interest from payload
        	List<JsonNode> valueNodes = getNodesByPath(payload, rule.getPath());
    		if (valueNodes.isEmpty()) {
    			continue;
    		}

    		for (JsonNode valueNode : valueNodes) {
	            // Transform into a string
	            String rawValue = valueNode.asText(null);
	            if (Assertion.isNullOrEmpty(rawValue)) {
	                continue;
	            }
	            
	            // Normalize with the given normalization rules
	            String normalized = 
	            		switch (rule.getType()) {
			                case "date" -> normalizationService.normalizeDate(rawValue);
			                default -> normalizationService.normalize(rawValue, rule.getNormalizers());
			            };
	
	            if (normalized == null) {
	                continue;
	            }
	            
				// Generate either plaintext linkage tokens or privacy-preserving linkage tokens
				// In PPRL mode, no plaintext normalized, phonetic, or prefix tokens are generated
				if (rule.usesPprl()) {
					tokens.addAll(buildPPRLTokens(rule, normalized, projectId, entityTypeId));
				} else {
					tokens.addAll(buildPlainTokens(rule, normalized));
				}
    		}
        }

        return tokens;
    }
    
    /**
     * Generates the plaintext linkage tokens for a normalized value.
     * This includes normalized tokens, optional phonetic tokens, and blocking tokens.
     * 
     * @param rule the linkage field rule
     * @param normalized the normalized field value
     * @return the generated plaintext linkage tokens
     */
	private List<LinkageToken> buildPlainTokens(LinkageFieldRule rule, String normalized) {
		List<LinkageToken> tokens = new ArrayList<>();
	
		// Create and add normalized-linkage token
		tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.NORM, normalized, rule.getWeight()));
	
		// Phonetically encode the normalized string if encoders are configured
		if (rule.getEncoders() != null) {
			for (String encoder : rule.getEncoders()) {
				String encoded = normalizationService.phoneticEncode(normalized, encoder);
	
				if (Assertion.isNotNullOrEmpty(encoded)) {
                    // Add phonetically encoded-linkage token
					tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.PHONETIC, encoded, rule.getWeight()));
				}
			}
		}
	
		// Generate blocking tokens if blocking strategies are configured
		if (rule.getBlocking() != null) {
			for (String block : rule.getBlocking()) {
				tokens.addAll(buildBlockingTokens(rule, normalized, block));
			}
		}
	
		return tokens;
	}
	
	/**
	 * Generates privacy-preserving linkage tokens for a normalized value.
	 * Depending on the PPRL method, this creates either an HMAC exact token or an
	 * n-gram Bloom filter with protected band tokens for candidate generation.
	 * 
	 * @param rule the linkage field rule
	 * @param normalized the normalized field value
	 * @param projectId the project ID used for PPRL context separation
	 * @param entityTypeId the entity type ID used for PPRL context separation
	 * @return the generated PPRL linkage tokens
	 */
	private List<LinkageToken> buildPPRLTokens(LinkageFieldRule rule, String normalized, int projectId, int entityTypeId) {
		List<LinkageToken> tokens = new ArrayList<>();
		
		// Get or create a PPRL config
		PPRLConfig config = rule.getPprlConfig() == null ? new PPRLConfig() : rule.getPprlConfig();

		// Check if the project and type context are given
		if (projectId <= 0 || entityTypeId <= 0) {
			throw new IllegalArgumentException("PPRL token generation requires projectId and entityTypeId.");
		}

		// Generate tokens based on the generation method defined in the config or use default
		String method = config.getMethod() == null ? PPRLConfig.METHOD_NGRAM_BLOOM_FILTER : config.getMethod();
		switch (method.trim().toLowerCase()) {
			case "hmacexact" -> {
				// Exact PPRL mode: only an HMAC-protected value is stored
				String exact = pprlEncodingService.hmacExact(projectId, entityTypeId, rule.getTag(), normalized);
				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.PPRL_EXACT, exact, rule.getWeight()));

				// The exact HMAC is also usable as a protected blocking key
				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.PPRL_BLOCK, exact, rule.getWeight()));
			}
			case "ngrambloomfilter" -> {
				// Fuzzy PPRL mode: n-grams are encoded into a protected Bloom filter
				BitSet bloomFilter = pprlEncodingService.buildBloomFilter(projectId, entityTypeId, rule.getTag(), normalized, config);
				String encodedBloom = pprlEncodingService.encodeBloomFilter(bloomFilter, config.getLength());

				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.PPRL_BLOOM, encodedBloom, rule.getWeight()));

				// Bloom filter bands are used for fast candidate generation
				for (String bandToken : pprlEncodingService.buildBloomBandTokens(projectId, entityTypeId, rule.getTag(), bloomFilter, config)) {
					tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.PPRL_BLOCK, bandToken, rule.getWeight()));
				}

				// Optional exact HMAC support for fields where exact agreement should provide a strong signal
				if (config.isExact()) {
					String exact = pprlEncodingService.hmacExact(projectId, entityTypeId, rule.getTag(), normalized);
					tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.PPRL_EXACT, exact, rule.getWeight()));
				}
			}
			default -> log.debug("Unknown PPRL method \"" + method + "\" for field \"" + rule.getPath() + "\".");
		}

		return tokens;
	}

    /**
     * Generates blocking tokens for a normalized field value according to the 
     * requested blocking strategy. Blocking tokens are used to reduce the number 
     * of candidate records for which a detailed score needs to be calculated.
     * 
     * @param rule the linkage field rule that defines the source field and linkage token properties
     * @param normalized the normalized field value from which the blocking tokens should be derived
     * @param block the blocking strategy that should be applied
     * @return the list of generated blocking tokens
     */
    private List<LinkageToken> buildBlockingTokens(LinkageFieldRule rule, String normalized, String block) {
    	List<LinkageToken> tokens = new ArrayList<>();
    	
    	switch (block.trim().toLowerCase()) {
    		case "exact" -> 
    			// Uses the full normalized value itself as the blocking key
    			tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.BLOCK, 
    					normalized, rule.getWeight()));
    		case "prefix3" -> {
    			// Uses the first three characters of the normalized value as the blocking key
    			if (normalized.length() >= 3) {
    				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.BLOCK, 
    						normalized.substring(0, 3), rule.getWeight()));
    			}
    		}
    		case "prefix4" -> {
    			// Uses the first four characters of the normalized value as the blocking key
    			if (normalized.length() >= 4) {
    				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.BLOCK, 
    						normalized.substring(0, 4), rule.getWeight()));
    			}
    		}
    		case "prefix6" -> {
    			// Uses the first six characters of the normalized value as the blocking key
    			if (normalized.length() >= 6) {
    				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.BLOCK, 
    						normalized.substring(0, 6), rule.getWeight()));
    			}
    		}
    		// Uses one or more phonetic encodings of the normalized value so 
    		// that similarly sounding values can fall into the same candidate group
    		case "phonetic" -> {
    			if (rule.getEncoders() == null || rule.getEncoders().isEmpty()) {
    				log.trace("Skipping phonetic blocking for field \"" + rule.getPath() + "\" because no phonetic encoder is configured.");
    				break;
    			}

    			for (String encoder : rule.getEncoders()) {
    				String phonetic = normalizationService.phoneticEncode(normalized, encoder);

    				if (Assertion.isNotNullOrEmpty(phonetic)) {
    					tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.BLOCK, 
    							phonetic, rule.getWeight()));
    				}
    			}
    		}
    		case "year" -> {
    			// Uses only the year component of a normalized ISO date as the blocking key
    			if ("date".equals(rule.getType())) {
    				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.BLOCK,
    						normalizationService.yearFromDate(normalized), rule.getWeight()));
    			}
    		}
    		case "yearmonth" -> {
    			// Uses the year and month component of a normalized ISO date as the blocking key
    			if ("date".equals(rule.getType())) {
    				tokens.add(new LinkageToken(rule.getPath(), rule.getTag(), LinkageTokenType.BLOCK,
    						normalizationService.yearMonthFromDate(normalized), rule.getWeight()));
    			}
    		}
    	}
    	
    	return tokens;
    }

    /**
     * Resolves all JSON nodes that match a logical dot-separated path.
     * If an array is encountered while traversing the path, the remaining path
     * is applied to each array element and all matching result nodes are collected.
     * 
     * @param root the root JSON node from which the values should be retrieved
     * @param path the logical dot-separated path of the desired field
     * @return the list of all JSON nodes that match the given path
     */
    private List<JsonNode> getNodesByPath(JsonNode root, String path) {
    	if (root == null || Assertion.isNullOrEmpty(path)) {
    		return List.of();
    	}
    	
    	return collectNodesByPath(root, path.split("\\."), 0);
    }

    /**
     * Recursively traverses a JSON structure and collects all nodes that match
     * the given path parts starting at the provided index.
     * 
     * When an array node is encountered, the same path index is used for each array
     * element so that the remaining path is applied to all entries of the array.
     * When the full path has been consumed, the current node is added to the result.
     * If the final node is an array, all of its elements are added individually.
     * 
     * @param current the JSON node currently inspected during traversal
     * @param pathParts the split logical path that should be resolved
     * @param pathIndex the current index inside the path parts array
     * @return the target list that receives all matching result nodes
     */
    private List<JsonNode> collectNodesByPath(JsonNode current, String[] pathParts, int pathIndex) {
    	List<JsonNode> out = new ArrayList<>();
    	
    	if (current == null) {
    		return out;
    	}
    	
    	// Check the leaf-element 
    	if (pathIndex >= pathParts.length) {
    		if (current.isArray()) {
    			for (JsonNode element : current) {
    				out.add(element);
    			}
    		} else {
    			out.add(current);
    		}
    		
    		return out;
    	}
    	
    	// Check the current node (which is not a leaf-node)
    	if (current.isArray()) {
    		for (JsonNode element : current) {
    			out.addAll(collectNodesByPath(element, pathParts, pathIndex));
    		}
    		
    		return out;
    	}

    	if (current.isObject()) {
    		out.addAll(collectNodesByPath(current.get(pathParts[pathIndex]), pathParts, pathIndex + 1));
    	}
    	
    	return out;
    }
}

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
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.model.LinkageFieldRule;
import org.trustdeck.model.LinkageToken;
import org.trustdeck.model.LinkageTokenType;
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

    /**
     * Generates record linkage tokens for a payload according to the provided linkage field rules.
     * For each linkage-enabled field, the raw input value is normalized first and then transformed
     * into one or more tokens for exact matching, phonetic matching, and blocking.
     * 
     * @param rules the linkage field rules that determine how tokens should be generated
     * @param payload the JSON payload from which the linkage tokens should be derived
     * @return the list of generated linkage tokens
     */
    public List<LinkageToken> buildTokens(List<LinkageFieldRule> rules, JsonNode payload) {
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
    		}
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

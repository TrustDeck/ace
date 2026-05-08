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

package org.trustdeck.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.dto.EntityInstanceDTO;
import org.trustdeck.dto.EntityTypeDTO;
import org.trustdeck.dto.RecordLinkageCandidateDTO;
import org.trustdeck.linkage.LinkageTokenService;
import org.trustdeck.model.CandidateStatus;
import org.trustdeck.model.LinkageFieldRule;
import org.trustdeck.model.LinkageToken;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * This service performs record linkage candidate search for entity instances.
 * It resolves the effective linkage field rules for an entity type, generates
 * linkage tokens for an input payload, retrieves candidate entity instances by
 * blocking tokens, and scores the resulting candidates.
 * 
 * @author Armin Müller
 */
@Service
@Slf4j
public class RecordLinkageService {

	/** Service used to resolve effective linkage field rules from type definitions. */
    @Autowired
    private JsonSchemaService jsonSchemaService;

    /** Used to retrieve base types for given entity types. */
    @Autowired
    private EntityTypeDBService entityTypeService;

    /** The service used to generate linkage tokens from input payloads. */
    @Autowired
    private LinkageTokenService linkageTokenService;

    /** Used to retrieve candidate entity instances from the database. */
    @Autowired
    private EntityInstanceDBService entityInstanceService;
    
    /** A factor to adjust the weight of a phonetic match. As it is less accurate than an exact match on a normalized string, the factor is usually < 1.0. */
    private static final double PHONETIC_MATCH_WEIGHT_FACTOR = 0.6;
    
    /** A factor to adjust the weight of a blocking match. As it is less accurate than an exact match on a normalized string, the factor is usually < 1.0. */
    private static final double BLOCKING_MATCH_WEIGHT_FACTOR = 0.25;
    
    /** The maximum number of candidates that should be pulled from the database (before scoring). */
    private static final int CANDIDATE_LIMIT = 250;
    
    /**
     * Searches for record linkage candidates for a given input payload.
     * The method resolves the effective linkage field rules, generates linkage tokens
     * for the payload, retrieves candidate records via blocking, scores them, and
     * returns the best-scoring candidates ordered by descending score.
     * 
     * @param projectId the ID of the project in which the search should be performed
     * @param entityType the entity type for which record linkage candidates should be searched
     * @param payload the JSON payload whose potential matching candidates should be determined
     * @param limit the maximum number of candidates that should be returned
     * @param includeDeleted whether soft-deleted entity instances should be included as candidates
     * @return a list of matching record linkage candidates sorted by descending score
     */
    public List<RecordLinkageCandidateDTO> findCandidates(int projectId, EntityTypeDTO entityType, JsonNode payload, int limit, boolean includeDeleted) {
    	// Retrieve the base type if the current entity type is based on one
        EntityTypeDTO baseType = entityType.getBaseTypeName() == null ? null : entityTypeService.getEntityTypeByName(entityType.getBaseTypeName(), null);

        // Resolve the effective linkage field rules for the concrete entity type
        JsonNode baseTypeDef = baseType == null ? null : baseType.getTypeDefinition();
        List<LinkageFieldRule> rules = jsonSchemaService.resolveLinkageFieldRules(entityType.getTypeDefinition(), baseTypeDef);

        // Generate linkage tokens for the input payload
        List<LinkageToken> payloadTokens = linkageTokenService.buildTokens(rules, payload);
        if (payloadTokens.isEmpty()) {
        	log.trace("Could not create tokens for the given payload.");
            return null;
        }

        // Use blocking tokens to determine a reduced set of candidate entity instance IDs
        List<Long> candidateIds = entityInstanceService.findCandidateIdsByBlockingTokens(projectId, entityType.getId(), 
        		payloadTokens, CANDIDATE_LIMIT, includeDeleted);
        
        if (candidateIds.isEmpty()) {
        	log.trace("After reducing candidates by comparing blocking tokens, no candidates remained.");
            return List.of();
        }

        // Retrieve the entity instance DTOs and their stored linkage tokens
        List<EntityInstanceDTO> instances = entityInstanceService.getEntityInstancesByIDs(candidateIds, entityType.getId(), includeDeleted);
    	if (instances == null) {
    		return null;
    	} else if (instances.isEmpty()) {
    		return List.of();
    	}
    	
        Map<Long, List<LinkageToken>> tokensByInstance = entityInstanceService.getLinkageTokensForInstances(candidateIds, entityType.getId());

        // Score all retrieved candidates and keep only those with a positive score
        List<RecordLinkageCandidateDTO> candidates = new ArrayList<>();
        for (EntityInstanceDTO instance : instances) {
            LinkageScoreResult score = score(payloadTokens, tokensByInstance.getOrDefault(instance.getId(), List.of()));
            
            if (score.score() > 0.0) {
            	boolean deleted = Boolean.TRUE.equals(instance.getIsDeleted());

    			candidates.add(RecordLinkageCandidateDTO.builder()
    					.entityInstance(instance)
    					.score(score.score())
    					.matchedOn(score.matchedOn())
    					.candidateStatus(deleted ? CandidateStatus.DELETED : CandidateStatus.ACTIVE)
    					.build());
            }
        }

        // Sort candidates by descending score and limit the number of returned results.
        return candidates.stream()
                .sorted(Comparator.comparingDouble(RecordLinkageCandidateDTO::getScore).reversed())
                .limit(limit)
                .toList();
    }
    
    /**
     * Searches for active record linkage candidates for a given input payload.
     * Soft-deleted entity instances are excluded.
     * 
     * @param projectId the ID of the project in which the search should be performed
     * @param entityType the entity type for which record linkage candidates should be searched
     * @param payload the JSON payload whose potential matching candidates should be determined
     * @param limit the maximum number of candidates that should be returned
     * @return a list of active matching record linkage candidates sorted by descending score
     */
    public List<RecordLinkageCandidateDTO> findCandidates(int projectId, EntityTypeDTO entityType, JsonNode payload, int limit) {
    	return findCandidates(projectId, entityType, payload, limit, false);
    }

    /**
     * Calculates the linkage score between the payload tokens and the tokens of one candidate.
     * Exact matches on normalized tokens contribute the full weight, phonetic token matches
     * contribute a reduced weight, and blocking token matches contribute only a small amount.
     * 
     * @param payloadTokens the linkage tokens derived from the input payload
     * @param candidateTokens the linkage tokens stored for one candidate entity instance
     * @return the total linkage score for the given candidate
     */
    private LinkageScoreResult score(List<LinkageToken> payloadTokens, List<LinkageToken> candidateTokens) {
        double score = 0.0;
    	Set<String> matchedOn = new LinkedHashSet<>();

        // Group the candidate tokens by semantic tag and token type to make matching more efficient
        Map<String, List<LinkageToken>> candidateTokensByTagAndType = new HashMap<>();
        for (LinkageToken token : candidateTokens) {
        	// Store the token under a specific key
            candidateTokensByTagAndType.computeIfAbsent(token.getTag() + "|" + token.getTokenType(), x -> new ArrayList<>()).add(token);
        }

        // Compare each payload token against candidate tokens of the same semantic tag and token type
        for (LinkageToken payloadToken : payloadTokens) {
            // Build the token key from the payload and retrieve candidate tokens with the same key
        	String key = payloadToken.getTag() + "|" + payloadToken.getTokenType();
            List<LinkageToken> candidateTokenMatches = candidateTokensByTagAndType.getOrDefault(key, List.of());

            // Calculate the score based on the type of token
            for (LinkageToken candidateToken : candidateTokenMatches) {
            	if (!payloadToken.getTokenValue().equals(candidateToken.getTokenValue())) {
    				continue;
    			}
                
            	if ("norm".equals(payloadToken.getTokenType())) {
                	// Exact normalized matches contribute the full configured weight for this attribute
                    score += payloadToken.getWeight();
                } else if ("phonetic".equals(payloadToken.getTokenType())) {
                	// Phonetic matches are weaker than exact normalized matches
                    score += payloadToken.getWeight() * PHONETIC_MATCH_WEIGHT_FACTOR;
                } else if ("block".equals(payloadToken.getTokenType())) {
                	// Blocking matches only provide a weak supporting signal
                    score += payloadToken.getWeight() * BLOCKING_MATCH_WEIGHT_FACTOR;
                }
            	
            	matchedOn.add(formatMatchedOn(payloadToken));
            }
        }

        return new LinkageScoreResult(score, List.copyOf(matchedOn));
    }
    
    /**
     * Formats a matched linkage token into a response-safe explanation string.
     * The token value itself is intentionally not included because it may contain
     * identifying information.
     * 
     * @param token the linkage token that contributed to the score
     * @return a response-safe explanation of the matched token
     */
    private String formatMatchedOn(LinkageToken token) {
    	return token.getFieldPath() + " [" + token.getTag() + ", " + token.getTokenType() + "]";
    }
    
    /**
     * Internal result object for record linkage scoring.
     * It contains the calculated score and the fields or tags that contributed to it.
     * 
     * @param score the calculated linkage score
     * @param matchedOn the list of fields or tags on which the candidate matched
     */
    private record LinkageScoreResult(double score, List<String> matchedOn) {}
}

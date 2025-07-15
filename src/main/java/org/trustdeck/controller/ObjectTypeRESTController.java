/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller & Eric Wündisch
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

package org.trustdeck.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trustdeck.dto.AlgorithmDTO;
import org.trustdeck.dto.ObjectTypeDTO;
import org.trustdeck.dto.PersonDTO;
import org.trustdeck.exception.DuplicatePersonException;
import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
import org.trustdeck.jooq.generated.tables.pojos.Person;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.utils.Assertion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class offers a REST API for interacting with project entities.
 *
 * @author Armin Müller
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api/management")
public class ObjectTypeRESTController {
	
	/**
     * Endpoint to create a new object type.
     * 
     * @param objectTypeDTO the DTO containing all the information about the object type that is to be registered
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>201-CREATED</b> status when the person was 
     * 				successfully created</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when non-optional 
     * 				information from the person object is missing 
     * 				(such as the first and last names or the 
     * 				administrative gender)</li>
     * 			<li>a <b>409-CONFLICT</b> when the data entered was an 
     * 				exact match to already existing data </li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when an algorithm 
     * 				object could not be created or when the person 
     * 				creation step itself failed</li>
     */
    @PostMapping("/object-type")
    @PreAuthorize("hasRole('object-type-create')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> createObjectType(@RequestParam(name = "createDomain", required = false, defaultValue = "") String createDomain,
    										  @RequestBody ObjectTypeDTO objectTypeDTO,
            							      @RequestHeader(name = "accept", required = false) String responseContentType,
            							      HttpServletRequest request) {
    	// Extract algorithm DTO
    	
    	
    	// Create and sanitize algorithm object, if necessary
    	Algorithm algorithm = null;
    	if (algorithmDTO.getId() != null && algorithmDTO.getId() >= 1) {
    		// Valid algorithm ID given, retrieve the algorithm
    		algorithm = algorithmDBService.getAlgorithmByID(algorithmDTO.getId());
    		if (algorithm == null) {
    			log.debug("Could not retrieve the algorithm by the given ID (" + algorithmDTO.getId() + "). Trying to find it by its attributes.");
    		}
    	}
    	
    	if (algorithm == null) {
    		// There either was no (valid) ID given, or the given ID was not found in the database
    		// Try finding the algorithm object
			algorithm = algorithmDBService.getAlgorithmByValues(algorithmDTO.getName(), algorithmDTO.getAlphabet(), algorithmDTO.getRandomAlgorithmDesiredSize(), algorithmDTO.getRandomAlgorithmDesiredSuccessProbability(), algorithmDTO.getPseudonymLength(), algorithmDTO.getPaddingCharacter(), algorithmDTO.isAddCheckDigit(), algorithmDTO.isLengthIncludesCheckDigit(), algorithmDTO.getSalt(), algorithmDTO.getSaltLength());
			
			// Check if something was found
			if (algorithm == null) {
				log.debug("The algorithm information provided did not lead to a known algorithm in the database. A new algorithm object will be created.");
			}
    		
    		// Check if there's still no algorithm object found
    		if (algorithm == null) {
    			// There was nothing found in the database that fits the given algorithm information, create a new algorithm object
    			Integer algoID = algorithmDBService.createAlgorithm(algorithmDTO.convertToPOJO());
    			
    			if (algoID == null) {
    				log.error("Could neither find nor create an algorithm object. Aborting person creation.");
    				return responseService.unprocessableEntity(responseContentType);
    			}
    			
    			// Successfully created new algorithm object, assign algorithm
    			algorithm = algorithmDBService.getAlgorithmByID(algoID);
    		}
    	}
    	
    	// Create and sanitize person object
    	Boolean pseudonymCreated = false;
    	if (Assertion.isNullOrEmpty(personDTO.getFirstName())) {
    		log.debug("Cannot create person object: first name is missing.");
    		return responseService.badRequest(responseContentType);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getLastName())) {
    		log.debug("Cannot create person object: last name is missing.");
    		return responseService.badRequest(responseContentType);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getBirthName())) {
    		personDTO.setBirthName(null);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getAdministrativeGender())) {
    		log.debug("Cannot create person object: administrative gender is missing.");
    		return responseService.badRequest(responseContentType);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getDateOfBirth())) {
    		personDTO.setDateOfBirth(null);
    	} else if (!isValidDate(personDTO.getDateOfBirth())) {
    		log.debug("Invalid date for DOB: " + personDTO.getDateOfBirth() + ". Should be formatted like \"yyyy-MM-dd\". Using \"null\" instead.");
    		personDTO.setDateOfBirth(null);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getStreet())) {
    		personDTO.setStreet(null);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getPostalCode())) {
    		personDTO.setPostalCode(null);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getCity())) {
    		personDTO.setCity(null);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getCountry())) {
    		personDTO.setCountry(null);
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getIdentifier())) {
    		// Create new pseudonym-identifier
    		String psnIdentifier = pseudonymize(personDTO.getFirstName() + personDTO.getLastName() + personDTO.getBirthName() + personDTO.getDateOfBirth(), "generatedFromPersonData", algorithm);
    		personDTO.setIdentifier(psnIdentifier);
    		pseudonymCreated = true;
    	} else {
    		// Store a given identifier
    		personDTO.setIdentifier(personDTO.getIdentifier().trim());
    		pseudonymCreated = false;
    	}
    	if (Assertion.isNullOrEmpty(personDTO.getIdType())) {
    		// Set idType
    		if (!pseudonymCreated && Assertion.isNullOrEmpty(personDTO.getIdType())) {
    			// The user provided an identifier but no idType
    			personDTO.setIdType("userProvidedIdentifier");
    		} else if (!pseudonymCreated && Assertion.isNotNullOrEmpty(personDTO.getIdType())) {
    			// The user provided an identifier and an idType; nothing to do
    		} else {
    			// The user did not provide an identifier, so one was generated --> overwrite everything currently in idType
    			personDTO.setIdType("generatedFromPersonData");
    		}
    	}

    	// Create the object in the database
    	Person p = null;
    	try {
    		p = personDBService.createPerson(personDTO.convertToPOJO(), algorithm, request);
    	} catch (DuplicatePersonException e) {
    		// Found a duplicate
    		log.debug("The person object was already in the database.");
    		return responseService.conflict(responseContentType);
    	}
    	
    	if (p != null) {
    		// Creation was successful
    		log.debug("Successfully created person object with ID: " + p.getId());
    		return responseService.created(responseContentType);
    	} else {
    		// Creation failed
    		log.info("Creating person object failed: " + personDTO.toRepresentationString());
    		return responseService.unprocessableEntity(responseContentType);
    	}   		
    }
}

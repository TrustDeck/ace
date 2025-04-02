/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trustdeck.service.ResponseService;
import org.trustdeck.utils.Assertion;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.algorithms.PseudonymizationFactory;
import org.trustdeck.algorithms.Pseudonymizer;
import org.trustdeck.dto.AlgorithmDTO;
import org.trustdeck.dto.PersonDTO;
import org.trustdeck.exception.DuplicatePersonException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
import org.trustdeck.jooq.generated.tables.pojos.Person;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.service.AlgorithmDBService;
import org.trustdeck.service.PersonDBService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class represents a REST-API controller handling requests for person registration.
 *
 * @author Armin Müller
 */
@Slf4j
@RestController
@EnableMethodSecurity
@RequestMapping("/api/registration")
public class PersonRESTContoller {
    
	/** Enables the access to the algorithm specific database access methods. */
    @Autowired
	private AlgorithmDBService algorithmDBService;
	
	/** Enables the access to the person specific database access methods. */
	@Autowired
	private PersonDBService personDBService;

    /** Enables services for better working with responses. */
    @Autowired
    private ResponseService responseService;

    /**
     * Endpoint to create a new person object.
     * Creates an algorithm object if necessary.
     * 
     * @param personDTO the DTO containing all the information about the person that is to be registered
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>201-CREATED</b> status when the person was 
     * 				successfully created</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when non-optional 
     * 				information from the person object is missing 
     * 				(such as the first and last names or the 
     * 				administrative gender)</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when an algorithm 
     * 				object could not be created or when the person 
     * 				creation step itself failed</li>
     */
    @PostMapping("/person")
    @PreAuthorize("hasRole('person-create')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> createPerson(@RequestBody PersonDTO personDTO,
            							  @RequestHeader(name = "accept", required = false) String responseContentType,
            							  HttpServletRequest request) {
    	// Extract algorithm DTO
    	AlgorithmDTO algorithmDTO = personDTO.getAlgorithm();
    	
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
    
    /**
     * Method that encapsulates the pseudonym generation.
     * 
     * @param identifier the identifier that should be pseudonymized 
     * @param idType the identifier's type
     * @param algorithm the algorithm with which the identifier should be pseudonymized
     * @return the generated pseudonym
     */
    private String pseudonymize(String identifier, String idType, Algorithm algorithm) {
            // Generate a new pseudonym            
            Pseudonymizer pseudonymizer = new PseudonymizationFactory().getPseudonymizer(algorithm);
            String pseudonym = pseudonymizer.pseudonymize(identifier + idType + algorithm.getSalt(), "");
            return algorithm.getAddcheckdigit() ? pseudonymizer.addCheckDigit(pseudonym, algorithm.getLengthincludescheckdigit(), algorithm.getName(), "") : pseudonym;
    }

    /**
     * Endpoint to update a person object.
     * Null values in the person DTO will retain the old attribute's value.
     * 
     * @param identifier the identifier needed to identify the person you want to update
     * @param idType the idType of the person you want to update
     * @param updatePersonDTO the DTO containing all the updated information about the person
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>200-OK</b> status when the person was 
     * 				successfully updated</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no person object was
     * 				found for the given identifier and idType</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the person 
     * 				update step itself failed</li>
     */
    @PutMapping("/person")
    @PreAuthorize("hasRole('person-update')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> updatePerson(@RequestParam String identifier, 
		    						      @RequestParam String idType, 
		    						      @RequestBody PersonDTO updatePersonDTO, 
		    						      @RequestHeader(name = "accept", required = false) String responseContentType,
		 							      HttpServletRequest request) {
    	// Get old person DTO
    	PersonDTO oldPerson = personDBService.getPersonByIdentifier(identifier, idType, request);
    	if (oldPerson == null) {
    		log.debug("For the given identifier and idType there was no person object found, so nothing to update.");
    		return responseService.notFound(responseContentType);
    	}
    	
    	// CURRENTLY, NO ALGORTIHM UPDATE IS ALLOWED
//    	// Get old algorithm
//    	Algorithm oldAlgorithm = algorithmDBService.getAlgorithmByID(oldPerson.getAlgorithm().getId());
//    	
//    	// Extract updated algorithm DTO
//    	AlgorithmDTO updateAlgorithmDTO = updatePersonDTO.getAlgorithm();
//    	
//    	// Update or create new algorithm object, if necessary
//    	Algorithm algorithm = null;
//    	if (updateAlgorithmDTO == null) {
//    		// Use old algorithm object
//    		algorithm = oldAlgorithm;
//    	} else if (updateAlgorithmDTO.getId() != oldAlgorithm.getId()) {
//    		// Use another algorithm; check if the ID is actually in the database
//    		Algorithm tmp = algorithmDBService.getAlgorithmByID(updateAlgorithmDTO.getId());
//    		if (tmp != null) {
//    			algorithm = tmp;
//    		} else {
//    			log.debug("Could not find an algorithm object to the given ID. For now, the old algorithm will be kept.");
//    			algorithm = oldAlgorithm;
//    		}
//    	} else if (updateAlgorithmDTO.getId() == oldAlgorithm.getId()) {
//    		// Update currently used algorithm or create new one if the previously used one is also used by other objects
//    		if (algorithmDBService.getNumberOfPersonsUsingAlgorithm(oldAlgorithm.getId()) > 1) {
//    			// Algorithm is used by other objects too --> create a new object
//    			Integer newID = algorithmDBService.createAlgorithm(updateAlgorithmDTO.convertToPOJO());
//    			
//    			if (newID == null) {
//    				log.debug("Due to multiple persons having assigned this algorithm, creating a new algorithm instead of updating the old one was initiated and failed. Using the old one.");
//    				algorithm = oldAlgorithm;
//    			}
//    			
//    			// At this point creating the new object was successful
//    			algorithm = algorithmDBService.getAlgorithmByID(newID);
//    		} else if (algorithmDBService.getNumberOfPersonsUsingAlgorithm(oldAlgorithm.getId()) == 1) {
//    			// Update the object
//    			Integer newID = algorithmDBService.updateAlgorithm(oldAlgorithm, updateAlgorithmDTO.convertToPOJO());
//    			
//    			if (newID == null) {
//    				log.debug("Updating the algorithm object failed. Using the old one.");
//    				algorithm = oldAlgorithm;
//    			}
//    			
//    			// At this point creating the new object was successful
//    			algorithm = algorithmDBService.getAlgorithmByID(newID);
//    		} else {
//    			// Should not happen, but we play it safe
//    			algorithm = oldAlgorithm;
//    		}
//    	}
    	
    	// Sanitize attributes
    	if (Assertion.isNotNullOrEmpty(updatePersonDTO.getDateOfBirth()) && !isValidDate(updatePersonDTO.getDateOfBirth())) {
    		log.debug("The birth date provided for the update was invalid and will therefore be ignored.");
    		updatePersonDTO.setDateOfBirth(null);
    	}
    	
//    	// If algorithm changed, re-generate identifier
//    	Boolean pseudonymCreated = false;
//    	if (!oldAlgorithm.equals(updateAlgorithmDTO.convertToPOJO())) {
//    		String psnIdentifier = pseudonymize(updatePersonDTO.getFirstName() + updatePersonDTO.getLastName() + updatePersonDTO.getBirthName() + updatePersonDTO.getDateOfBirth(), "generatedFromPersonData", algorithm);
//    		updatePersonDTO.setIdentifier(psnIdentifier);
//    		pseudonymCreated = true;
//    	}
//    	
//    	// If a new identifier was generated, ensure that the idType is set accordingly
//    	if (pseudonymCreated) {
//    		if (Assertion.isNotNullOrEmpty(updatePersonDTO.getIdType())) {
//    			log.debug("An idType was given while the changed algorithm resulted in a newly generated identifier. Therefore the given idType will be ignored.");
//    		}
//    		
//    		updatePersonDTO.setIdType("generatedFromPersonData");
//    	} else if (Assertion.isNotNullOrEmpty(updatePersonDTO.getIdentifier()) && Assertion.isNullOrEmpty(updatePersonDTO.getIdType())) {
//    		// The identifier was given by the user, but no idType --> set one accordingly
//    		updatePersonDTO.setIdType("userProvidedIdentifier");
//    	}
    	
    	// Assign algorithm object
    	updatePersonDTO.setAlgorithm(oldPerson.getAlgorithm());
    	
    	// Update person object
    	if (personDBService.updatePerson(identifier, idType, updatePersonDTO, request) == null) {
    		log.debug("Updating the person object with ID " + oldPerson.getId() + " failed.");
    		return responseService.unprocessableEntity(responseContentType);
    	} else {
    		log.debug("Successfully updated the person object with ID: " + oldPerson.getId());
    		return responseService.ok(responseContentType);
    	}
    }

    /**
     * Endpoint to retrieve a person object.
     * 
     * @param identifier the identifier needed to identify the person you want to retrieve
     * @param idType the idType of the person you want to retrieve
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>200-OK</b> status and the found person when 
     * 				the retrieval was successful</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when the identifier or 
     * 				idType are missing or blank</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no person object was
     * 				found for the given identifier and idType</li>
     */
    @GetMapping(value = "/person", params = {"identifier", "idType"})
    @PreAuthorize("hasRole('person-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getPerson(@RequestParam String identifier, 
									   @RequestParam String idType, 
									   @RequestHeader(name = "accept", required = false) String responseContentType,
									   HttpServletRequest request) {
    	// Sanitize
    	if (Assertion.isNullOrEmpty(identifier)) {
    		log.debug("The given identifier was null or empty.");
    		return responseService.badRequest(responseContentType);
    	}
    	
    	if (Assertion.isNullOrEmpty(idType)) {
    		log.debug("The given idType was null or empty.");
    		return responseService.badRequest(responseContentType);
    	}
    	
    	// Get person object from database
    	PersonDTO person = personDBService.getPersonByIdentifier(identifier, idType, request);
    	
    	if (person == null) {
    		log.debug("Could not find a person with the given identifier \"" + identifier + "\" and idType \"" + idType + "\".");
    		return responseService.notFound(responseContentType);
    	}
    	
    	return responseService.ok(responseContentType, person);
    }

    /**
     * Endpoint to search for person objects.
     * 
     * @param query the string to search for in order to identify the person you want to retrieve
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>200-OK</b> status and the persons found when 
     * 				the search was successful</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no person object was
     * 				found for the given search query</li>
     */
    @GetMapping(value = "/person", params = "q")
    @PreAuthorize("hasRole('person-search')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> searchPersons(@RequestParam(name = "q", required = true) String query, 
									       @RequestHeader(name = "accept", required = false) String responseContentType,
										   HttpServletRequest request) {
        // Search the database for the given search terms
    	List<Person> results = personDBService.searchPersons(query.trim(), request);
    	
    	if (results == null || results.size() == 0) {
    		log.debug("No results were found for the given query string (" + query + ").");
    		return responseService.notFound(responseContentType);
    	} else {
    		// Transform POJOs to DTOs and return
    		List<PersonDTO> resultDTOs = new ArrayList<PersonDTO>();
    		for (Person p : results) {
    			resultDTOs.add(new PersonDTO().assignPojoValues(p));
    		}
    		
    		log.debug("The search for persons with query \"" + query + "\" returned " + resultDTOs.size() + (resultDTOs.size() == 1 ? " result." : " results."));
    		return responseService.ok(responseContentType, resultDTOs);
    	}
    }

    /**
     * Endpoint to delete a person object.
     * This will also delete the algorithm object if it would otherwise be orphaned.
     * 
     * @param identifier the identifier needed to identify the person you want to delete
     * @param idType the idType of the person you want to delete
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>200-OK</b> status when no person object was 
     * 				found and therefore, nothing needed to be deleted</li>
     * 			<li>a <b>204-NO_CONTENT</b> status when the person was 
     * 				successfully deleted</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the deletion
     * 				would affect more than one person object</li>
     */
    @DeleteMapping("/person")
    @PreAuthorize("hasRole('person-delete')")
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> deletePerson(@RequestParam String identifier, 
									      @RequestParam String idType, 
									      @RequestHeader(name = "accept", required = false) String responseContentType,
									      HttpServletRequest request) {
    	// Delete the person object identified by the given identifier and idType
    	boolean successfullyDeleted = false;
    	try {
    		successfullyDeleted = personDBService.deletePerson(identifier, idType, request);
    	} catch (UnexpectedResultSizeException e) {
    		log.debug("The deletion would have affected more than one person, so it was aborted.");
    		return responseService.unprocessableEntity(responseContentType);
    	}
    	
    	if (successfullyDeleted) {
    		log.debug("Successfully deleted the person object with identifier \"" + identifier + "\" and idType \"" + idType + "\".");
    		return responseService.noContent(responseContentType);
    	} else {
    		log.debug("For the given identifier and idType there was no person object found, so nothing was deleted.");
    		return responseService.notFound(responseContentType);
    	}
    }
    
    /**
     * Helper method to verify if a given date string is in the correct format.
     * 
     * @param date the date as a string
     * @return {@code true} if the given date is correctly formatted as "yyyy-MM-dd", {@code false} otherwise
     */
    private boolean isValidDate(String date) {
    	// Try parsing the date: if an exception occurs, the formatting is invalid
        try {
            LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return true;
        } catch (DateTimeParseException e) {
        	// Parsing failed, incorrect format or invalid date
            return false;
        }
    }
}

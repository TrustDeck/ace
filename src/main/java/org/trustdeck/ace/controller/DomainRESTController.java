/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.*;
import org.trustdeck.ace.jooq.generated.tables.pojos.Domain;
import org.trustdeck.ace.model.dto.DomainDto;
import org.trustdeck.ace.security.audittrail.annotation.Audit;
import org.trustdeck.ace.security.audittrail.event.AuditEventType;
import org.trustdeck.ace.security.audittrail.usertype.AuditUserType;
import org.trustdeck.ace.service.AuthorizationService;
import org.trustdeck.ace.service.DomainDBAccessService;
import org.trustdeck.ace.service.ResponseService;
import org.trustdeck.ace.utils.Assertion;
import org.trustdeck.ace.utils.Utility;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * This class encapsulates the requests for domains in a controller for the REST-API.
 * This REST-API offers full access to the data items.
 *
 * @author Armin Müller & Eric Wündisch
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api/pseudonymization")
public class DomainRESTController {

	/** The default value for adding a check digit to the pseudonym. */
	private static final boolean DEFAULT_ADD_CHECK_DIGIT = true;
	
	/** The default value for allowing multiple pseudonyms per id&idType pair. */
	private static final boolean DEFAULT_ALLOW_MULTIPLE_PSN = false;
	
    /** The default value for enforcing the validTo date correctness. */
    private static final boolean DEFAULT_ENFORCE_END_DATE_VALIDITY = true;

    /** The default value for enforcing the validFrom date correctness. */
    private static final boolean DEFAULT_ENFORCE_START_DATE_VALIDITY = true;
    
    /** The default value for determining if the check digit should be included in the defined pseudonym length. */
    private static final boolean DEFAULT_LENGTH_INCLUDES_CHECK_DIGIT = false;
    
	/** Determines the default number of retries when a generated random number is already in use. */
	private static final int DEFAULT_NUMBER_OF_RETRIES = 3;

    /** The default character used for padding pseudonyms if necessary. */
    private static final char DEFAULT_PADDING_CHARACTER = '0';

    /** The default value for performing a recursive update of possible child domains. */
    private static final boolean DEFAULT_PERFORM_RECURSIVE_CHANGES = true;

    /** The default pseudonymization algorithm. */
    private static final String DEFAULT_PSEUDONYMIZATION_ALGO = "RANDOM_LET";
    
    /** The default alphabet (A-Z0-9) used for the pseudonymization process. */
    private static final String DEFAULT_PSEUDONYMIZATION_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** The default pseudonym length. */
    private static final int DEFAULT_PSEUDONYM_LENGTH = 16;
    
    /** The default number of pseudonyms a user wants to be able to create. */
    private static final long DEFAULT_RANDOM_ALGORITHM_DESIRED_SIZE = 100000000L;
    
    /** The default success probability with which a user wants to create a new pseudonym. */
    private static final double DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY = 0.99999998d;

    /** The default length of a salt. */
    private static final int DEFAULT_SALT_LENGTH = 32;

    /** The default validity time in seconds. */
    private static final long DEFAULT_VALIDITY_TIME_IN_SECONDS = 30 * 365 * 86400; // Ignores leap days.

    /** Enables the access to the domain specific database access methods. */
    @Autowired
    private DomainDBAccessService domainDBAccessService;
    
    /** The maximum length for the salt. */
    private static final int MAXIMUM_SALT_LENGTH = 256;

    /** The minimum length for pseudonyms. */
    private static final int MINIMUM_PSEUDONYM_LENGTH = 2;
    
    /** The minimum length for the salt. */
    private static final int MINIMUM_SALT_LENGTH = 8;

    /** Enables services for better working with responses. */
    @Autowired
    private ResponseService responseService;

    /** Provides functionality to ensure proper rights and roles when accessing the endpoints. */
    @Autowired
    private AuthorizationService authorizationService;

    /**
     * Method to create a new domain. Creates the record inside the
     * domain table.
     *
     * @param domainDto (required) the domain object
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>201-CREATED</b> status and the location to the
     * 				domain inside the response header on success</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when both, the super-domain
     * 				name and the super-domain ID were given</li>
     * 			<li>a <b>404-NOT_FOUND</b> when the provided parent 
     * 				domain could not be found</li>
     * 			<li>a <b>406-NOT_ACCEPTABLE</b> status when the domain
     * 				name is violating the URI-validity</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the addition 
     * 				of the domain meta-information failed or when a check
     * 				digit should be calculated and the provided alphabet 
     * 				length is not an even number</li>
     */
    @PostMapping("/domain/complete")
    @PreAuthorize("hasRole('domain-create-complete')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL, message = "Wants to create a new domain.")
    public ResponseEntity<?> createDomainComplete(@RequestBody DomainDto domainDto,
                                                  @RequestHeader(name = "accept", required = false) String responseContentType,
                                                  HttpServletRequest request) {
        String domainName = domainDto.getName();
        String domainPrefix = domainDto.getPrefix();
        Timestamp validFrom = domainDto.getValidFrom() != null ? Timestamp.valueOf(domainDto.getValidFrom()) : null;
        Timestamp validTo = domainDto.getValidTo() != null ? Timestamp.valueOf(domainDto.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(domainDto.getValidityTime());
        Boolean enforceStartDateValidity = domainDto.getEnforceStartDateValidity();
        Boolean enforceEndDateValidity = domainDto.getEnforceEndDateValidity();
        String algorithm = domainDto.getAlgorithm();
        String alphabet = generateAlphabet(domainDto.getAlgorithm(), domainDto.getAlphabet());
        Long randomAlgorithmDesiredSize = domainDto.getRandomAlgorithmDesiredSize();
        Double randomAlgorithmDesiredSuccessProbability = domainDto.getRandomAlgorithmDesiredSuccessProbability();
        Long consecVal = domainDto.getConsecutiveValueCounter();
        Boolean multiplePsnAllowed = domainDto.getMultiplePsnAllowed();
        Integer psnLength = domainDto.getPseudonymLength();
        Character paddingChar = domainDto.getPaddingCharacter();
        Boolean addCheckDigit = domainDto.getAddCheckDigit();
        Boolean lengthIncludesCheckDigit = domainDto.getLengthIncludesCheckDigit();
        String salt = domainDto.getSalt();
        Integer saltLength = domainDto.getSaltLength();
        String description = domainDto.getDescription();
        String superDomainName = domainDto.getSuperDomainName();

        if (Assertion.assertNullAll(domainName, domainPrefix, validFrom, validTo, validityTime, enforceStartDateValidity,
                enforceEndDateValidity, algorithm, alphabet, randomAlgorithmDesiredSize, randomAlgorithmDesiredSuccessProbability, 
                consecVal, multiplePsnAllowed, psnLength, paddingChar, addCheckDigit, lengthIncludesCheckDigit, salt, 
                saltLength, description, superDomainName)) {
            // An empty object was passed, so there is nothing to create.
            log.debug("The domain DTO passed by the user was empty. Nothing to create.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Check if the name is valid in an URI. If not, tell the user and abort
        URI location;
        try {
            location = new URI("/api/pseudonymization/domain?name=" + domainName);
        } catch (URISyntaxException e) {
            log.debug("The domain name is not suitable to be used in a URI. Please choose another name.");
            return responseService.notAcceptable(responseContentType);
        }

        // Get parent information if provided
        Domain parent = null;
        if (superDomainName != null) {
            parent = domainDBAccessService.getDomainByName(superDomainName, null);

            // Parent domain name was provided but not found, return a 404-NOT_FOUND 
            if (parent == null) {
                log.debug("Parent domain name was provided but not found.");
                return responseService.notFound(responseContentType);
            }
        }
        
        // Ensure a proper salt length
        saltLength = (saltLength != null && saltLength >= MINIMUM_SALT_LENGTH && saltLength <= MAXIMUM_SALT_LENGTH) ? saltLength : DEFAULT_SALT_LENGTH;

        // Create a salt if not already given
        if (salt == null || salt.trim().length() < MINIMUM_SALT_LENGTH || salt.trim().length() > MAXIMUM_SALT_LENGTH) {
        	String saltAlphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_+";
	        SecureRandom rnd = new SecureRandom();
	        StringBuilder sb = new StringBuilder(saltLength);
	
	        for (int i = 0; i < saltLength; i++) {
	            sb.append(saltAlphabet.charAt(rnd.nextInt(saltAlphabet.length())));
	        }
	
	        salt = sb.toString();
        }
        
        // Sanitize the alphabet
 		if (alphabet.contains(";")) {
 			log.error("The alphabet provided for the pseudonymization in domain contained a semicolon (\";\"), which is not allowed.");
 			return responseService.unprocessableEntity(responseContentType);
 		}
		
		// Ensure that the alphabet has a valid length, when a check digit is requested
		if (addCheckDigit != null && addCheckDigit && (alphabet.length() % 2) == 1) {
			log.warn("The alphabet provided for the pseudonymization in domain does not have an even length, "
					+ "which is necessary for the requested check-digit-calculation. The last character was "
					+ "therefore removed.");
			alphabet = alphabet.substring(0, alphabet.length() - 1);
		}
        
        // Ensure a valid desired success probability, if the selected algorithm is of the RANDOM-family
        if (algorithm != null && algorithm.trim().toUpperCase().startsWith("RANDOM") && randomAlgorithmDesiredSuccessProbability != null) {
        	if (randomAlgorithmDesiredSuccessProbability > 1.0d) {
        		// The success probability was probably provided as a number between 0 and 100, so we transform it into [0,1] 
        		randomAlgorithmDesiredSuccessProbability /= 100.0d;
        	}
        	
        	if (randomAlgorithmDesiredSuccessProbability >= 1.0d) {
        		// The provided number is still too big, switch to default value
        		log.warn("The provided success probability for the pseudonymization process was too big. Switching to default value.");
        		randomAlgorithmDesiredSuccessProbability = DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY;
        	} else if (randomAlgorithmDesiredSuccessProbability <= 0.0d) {
	    		// The provided number is zero or negative, switch to default value
	    		log.warn("The provided success probability for the pseudonymization process was too small. Switching to default value.");
	    		randomAlgorithmDesiredSuccessProbability = DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY;
        	}
        } else if (algorithm != null && algorithm.toUpperCase().startsWith("RANDOM") && randomAlgorithmDesiredSuccessProbability == null) {
        	// Use default
        	randomAlgorithmDesiredSuccessProbability = DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY;
        } else {
        	// Discard any provided information
        	randomAlgorithmDesiredSuccessProbability = null;
        }
        
        // Ensure a proper desired domain size
        if (algorithm != null && algorithm.trim().toUpperCase().startsWith("RANDOM") && randomAlgorithmDesiredSize == null) {
        	randomAlgorithmDesiredSize = DEFAULT_RANDOM_ALGORITHM_DESIRED_SIZE;
        }
        
        // Ensure that the check digit algorithm can work if it is requested
        if (addCheckDigit != null && addCheckDigit && (alphabet.length() % 2) != 0) {
        	// The calculation of a check digit was requested. For the algorithm that calculates the check digit to 
        	// work, the alphabet length must be an even number.
        	log.debug("The calculation of a check digit was requested. For the algorithm that calculates the check digit "
        			+ "to work, the alphabet length must be an even number.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Ensure that a valid counter for the consecutive numbering pseudonymization algorithm is present
        if (consecVal == null || consecVal <= 0) {
            consecVal = 1L;
        }

        // Ensure that the pseudonyms are long enough and not null
        if (algorithm != null && algorithm.trim().toUpperCase().startsWith("RANDOM")) {
        	int neededPsnLength = calculatePseudonymLength(randomAlgorithmDesiredSize, randomAlgorithmDesiredSuccessProbability, alphabet);
        	if (psnLength != null && neededPsnLength > psnLength) {
        		log.debug("The pseudonym length needed to store the desired number of pseudonyms is smaller than the provided pseudonym length. The calculated size will be used.");
        		psnLength = neededPsnLength;
        	} else if (psnLength != null && neededPsnLength < psnLength) {
        		// A length longer than the needed one was given
        		// Nothing to do
        	} else {
        		psnLength = neededPsnLength;
        	}
        } else if (randomAlgorithmDesiredSize != null && randomAlgorithmDesiredSuccessProbability != null) {
        	// Calculate minimal needed length for all non-random algorithms
        	psnLength = calculatePseudonymLength(randomAlgorithmDesiredSize, randomAlgorithmDesiredSuccessProbability, alphabet);
        }
        
        // If no info about the desired size was given and no length was provided, use the default
        if (psnLength == null) {
            psnLength = DEFAULT_PSEUDONYM_LENGTH;
        }
        
        // Ensure that the pseudonyms are never too short
        if (psnLength < MINIMUM_PSEUDONYM_LENGTH) {
            log.warn("The requested length for pseudonyms in the domain \"" + domainName + "\" was too short "
                    + "and therefore set to the minimum length (" + MINIMUM_PSEUDONYM_LENGTH + ").");
            psnLength = MINIMUM_PSEUDONYM_LENGTH;
        }

        // Gather the information needed for a new domain object
        Domain domain = new Domain();

        // Start creating a new domain object
        domain.setName(domainName);
        domain.setPrefix(domainPrefix);

        domain.setConsecutivevaluecounter(consecVal);
        
        domain.setDescription(description);
        
        domain.setSalt(salt);
        domain.setSaltlength(salt.length());

        if (parent != null) {
            // Determine validFrom date if not given by the user
            domain.setValidfrom((validFrom != null) ? validFrom.toLocalDateTime() : parent.getValidfrom());
            domain.setValidfrominherited(validFrom == null);

            // Determine validTo date
            LocalDateTime vTo;
            Boolean vToInh = false;
            if (validTo != null) {
                vTo = validTo.toLocalDateTime();
            } else if (validTo == null && validityTime != null) {
                vTo = domain.getValidfrom().plusSeconds(validityTime);
            } else {
                vTo = parent.getValidto();
                vToInh = true;
            }

            domain.setValidto(vTo);
            domain.setValidtoinherited(vToInh);

            // Determine if the validFrom date correctness should be enforced
            domain.setEnforcestartdatevalidity((enforceStartDateValidity != null) ? enforceStartDateValidity : parent.getEnforcestartdatevalidity());
            domain.setEnforcestartdatevalidityinherited(enforceStartDateValidity == null);

            // Determine if the validTo date correctness should be enforced
            domain.setEnforceenddatevalidity((enforceEndDateValidity != null) ? enforceEndDateValidity : parent.getEnforceenddatevalidity());
            domain.setEnforceenddatevalidityinherited(enforceEndDateValidity == null);

            // Determine pseudonymization algorithm
            domain.setAlgorithm((algorithm != null) ? algorithm : parent.getAlgorithm());
            domain.setAlgorithminherited(algorithm == null);
            
            // Determine the alphabet used for pseudonymization
            domain.setAlphabet((alphabet != null) ? alphabet : parent.getAlphabet());
            domain.setAlphabetinherited(alphabet == null);
            
            // Determine the number of pseudonyms the user wants to create
            domain.setRandomalgorithmdesiredsize((randomAlgorithmDesiredSize != null) ? randomAlgorithmDesiredSize : parent.getRandomalgorithmdesiredsize());
            domain.setRandomalgorithmdesiredsizeinherited(randomAlgorithmDesiredSize == null);
            
            // Determine the minimum success probability for the generation of new pseudonyms
            domain.setRandomalgorithmdesiredsuccessprobability((randomAlgorithmDesiredSuccessProbability != null) ? randomAlgorithmDesiredSuccessProbability : parent.getRandomalgorithmdesiredsuccessprobability());
            domain.setRandomalgorithmdesiredsuccessprobabilityinherited(randomAlgorithmDesiredSuccessProbability == null);
            
            // Determine if multiple pseudonyms per identifier&idType combination are allowed
            domain.setMultiplepsnallowed((multiplePsnAllowed != null) ? multiplePsnAllowed : parent.getMultiplepsnallowed());
            domain.setMultiplepsnallowedinherited(multiplePsnAllowed == null);
            
            // Determine pseudonym length
            domain.setPseudonymlength((psnLength != null) ? psnLength : parent.getPseudonymlength());
            domain.setPseudonymlengthinherited(psnLength == null);

            // Determine padding character
            domain.setPaddingcharacter((paddingChar != null) ? paddingChar.toString() : parent.getPaddingcharacter());
            domain.setPaddingcharacterinherited(paddingChar == null);

            // Determine if a check digit should be created
            domain.setAddcheckdigit((addCheckDigit != null) ? addCheckDigit : parent.getAddcheckdigit());
            domain.setAddcheckdigitinherited(addCheckDigit == null);

            // Determine if the check digit should be included in the length of the pseudonym
            domain.setLengthincludescheckdigit((lengthIncludesCheckDigit != null) ? lengthIncludesCheckDigit : parent.getLengthincludescheckdigit());
            domain.setLengthincludescheckdigitinherited(lengthIncludesCheckDigit == null);
            
            domain.setSuperdomainid(parent.getId());
        } else {
            // No parent information provided

            // Check if either validTo or validityTime is given
            if (validTo == null && validityTime == null) {
                // Both values are missing; inform user and use default
                log.debug("For the domain \"" + domainName + "\" there was neither an end date nor a validity period given."
                        + " The default of 30 years is used.");
                validityTime = DEFAULT_VALIDITY_TIME_IN_SECONDS;
            }

            // Determine validFrom date if not given by the user
            domain.setValidfrom((validFrom != null) ? validFrom.toLocalDateTime() : LocalDateTime.now());
            domain.setValidfrominherited(false);

            // Determine expiration date
            domain.setValidto((validTo != null) ? validTo.toLocalDateTime() : domain.getValidfrom().plusSeconds(validityTime));
            domain.setValidtoinherited(false);

            // Determine if the validFrom date correctness should be enforced
            domain.setEnforcestartdatevalidity((enforceStartDateValidity != null) ? enforceStartDateValidity : DEFAULT_ENFORCE_START_DATE_VALIDITY);
            domain.setEnforcestartdatevalidityinherited(false);

            // Determine if the validTo date correctness should be enforced
            domain.setEnforceenddatevalidity((enforceEndDateValidity != null) ? enforceEndDateValidity : DEFAULT_ENFORCE_END_DATE_VALIDITY);
            domain.setEnforceenddatevalidityinherited(false);

            // Determine pseudonymization algorithm
            domain.setAlgorithm((algorithm != null) ? algorithm : DEFAULT_PSEUDONYMIZATION_ALGO);
            domain.setAlgorithminherited(false);
            
            // Determine the alphabet used for pseudonymization
            domain.setAlphabet((alphabet != null) ? alphabet : DEFAULT_PSEUDONYMIZATION_ALPHABET);
            domain.setAlphabetinherited(false);
            
            // Determine the number of pseudonyms the user wants to create
            domain.setRandomalgorithmdesiredsize((randomAlgorithmDesiredSize != null) ? randomAlgorithmDesiredSize : DEFAULT_RANDOM_ALGORITHM_DESIRED_SIZE);
            domain.setRandomalgorithmdesiredsizeinherited(false);
            
            // Determine the minimum success probability for the generation of new pseudonyms
            domain.setRandomalgorithmdesiredsuccessprobability((randomAlgorithmDesiredSuccessProbability != null) ? randomAlgorithmDesiredSuccessProbability : DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY);
            domain.setRandomalgorithmdesiredsuccessprobabilityinherited(false);

            // Determine if multiple pseudonyms per identifier&idType combination are allowed
            domain.setMultiplepsnallowed((multiplePsnAllowed != null) ? multiplePsnAllowed : DEFAULT_ALLOW_MULTIPLE_PSN);
            domain.setMultiplepsnallowedinherited(false);
            
            // Determine pseudonym length
            domain.setPseudonymlength((psnLength != null) ? psnLength : DEFAULT_PSEUDONYM_LENGTH);
            domain.setPseudonymlengthinherited(false);

            // Determine padding character
            domain.setPaddingcharacter((paddingChar != null) ? paddingChar.toString() : String.valueOf(DEFAULT_PADDING_CHARACTER));
            domain.setPaddingcharacterinherited(false);

            // Determine if a check digit should be created
            domain.setAddcheckdigit((addCheckDigit != null) ? addCheckDigit : DEFAULT_ADD_CHECK_DIGIT);
            domain.setAddcheckdigitinherited(false);

            // Determine if the check digit should be included in the length of the pseudonym
            domain.setLengthincludescheckdigit((lengthIncludesCheckDigit != null) ? lengthIncludesCheckDigit : DEFAULT_LENGTH_INCLUDES_CHECK_DIGIT);
            domain.setLengthincludescheckdigitinherited(false);
            
            domain.setSuperdomainid(0);
        }

        // Insert into database
        String result = domainDBAccessService.insertDomain(domain, request);
        
        if (result.equals(DomainDBAccessService.INSERTION_SUCCESS)) {
            // Return a 201-CREATED status and the location to the domain inside the response header.
            log.info("Successfully created the domain \"" + domainName + "\".");
            return responseService.created(responseContentType, location);
        } else if (result.equals(DomainDBAccessService.INSERTION_DUPLICATE)) {
            // Nothing added since the entry is a duplicate. Return an 200-OK status.
            log.info("The domain requested to be inserted was skipped because it is already in the database.");
            return responseService.ok(responseContentType);
        } else {
            // Creating the domain failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("Adding the domain \"" + domainName + "\" was unsuccessful.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * Method to create a new domain with a reduced set of attributes.
     * Creates the record inside the domain table.
     *
     * @param domainDto (required) the domain object
     * @param responseContentType(optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status when the domain was already
     * 				in the database</li>
     * 			<li>a <b>201-CREATED</b> status and the location to the
     * 				domain inside the response header on success</li>
     * 			<li>a <b>404-NOT_FOUND</b> when the provided parent 
     * 				domain could not be found</li>
     * 			<li>a <b>406-NOT_ACCEPTABLE</b> status when the domain
     * 				name is violating the URI-validity</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the addition
     * 				of the domain failed</li>
     */
    @PostMapping("/domain")
    @PreAuthorize("hasRole('domain-create')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL, message = "Wants to create a new domain.")
    public ResponseEntity<?> createDomain(@RequestBody DomainDto domainDto,
                                          @RequestHeader(name = "accept", required = false) String responseContentType,
                                          HttpServletRequest request) {

        if (!domainDto.validate() || !domainDto.isValidStandardView()) {
            return responseService.unprocessableEntity(responseContentType);
        }

        String domainName = domainDto.getName();
        String domainPrefix = domainDto.getPrefix();
        Timestamp validFrom = domainDto.getValidFrom() != null ? Timestamp.valueOf(domainDto.getValidFrom()) : null;
        Timestamp validTo = domainDto.getValidTo() != null ? Timestamp.valueOf(domainDto.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(domainDto.getValidityTime());
        String algorithm = domainDto.getAlgorithm();
        String alphabet = generateAlphabet(algorithm, null);
        Boolean multiplePsnAllowed = domainDto.getMultiplePsnAllowed();
        String description = domainDto.getDescription();
        String superDomainName = domainDto.getSuperDomainName();

        if (Assertion.assertNullAll(domainName, domainPrefix, validFrom, validTo, validityTime, algorithm, 
        		multiplePsnAllowed, description, superDomainName)) {
            // An empty object was passed, so there is nothing to create.
            log.debug("The domain DTO passed by the user was empty. Nothing to create.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Check if the name is valid in an URI. If not, tell the user and abort
        URI location;
        try {
            location = new URI("/api/pseudonymization/domain?name=" + domainName);
        } catch (URISyntaxException e) {
            log.debug("The domain name is not suitable to be used in a URI. Please choose another name.");
            return responseService.notAcceptable(responseContentType);
        }

        // Get parent information if provided
        Domain parent = null;
        if (superDomainName != null) {
            parent = domainDBAccessService.getDomainByName(superDomainName, null);

            // Parent domain name was provided but not found, return a 404-NOT_FOUND 
            if (parent == null) {
                log.debug("Parent domain name was provided but nothing was not found.");
                return responseService.notFound(responseContentType);
            }
        }
        
        // Create a salt
        String saltAlphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_+";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(DEFAULT_SALT_LENGTH);

        for (int i = 0; i < DEFAULT_SALT_LENGTH; i++) {
            sb.append(saltAlphabet.charAt(rnd.nextInt(saltAlphabet.length())));
        }

        String salt = sb.toString();

        // Ensure that a valid counter for the consecutive numbering pseudonymization algorithm is present
        long consecVal = 1L;

        // Gather the information needed for a new domain object
        Domain domain = new Domain();

        // Start creating the new domain object
        domain.setName(domainName);
        domain.setPrefix(domainPrefix);
        
        domain.setConsecutivevaluecounter(consecVal);
        
        domain.setDescription(description);
        
        domain.setSalt(salt);
        domain.setSaltlength(salt.length());

        if (parent != null) {
            // Determine validFrom date if not given by the user
            domain.setValidfrom((validFrom != null) ? validFrom.toLocalDateTime() : parent.getValidfrom());
            domain.setValidfrominherited(validFrom == null);

            // Determine validTo date
            LocalDateTime vTo;
            Boolean vToInh = false;
            if (validTo != null) {
                vTo = validTo.toLocalDateTime();
            } else if (validTo == null && validityTime != null) {
                vTo = domain.getValidfrom().plusSeconds(validityTime);
            } else {
                vTo = parent.getValidto();
                vToInh = true;
            }

            domain.setValidto(vTo);
            domain.setValidtoinherited(vToInh);

            // Determine if the validFrom date correctness should be enforced
            domain.setEnforcestartdatevalidity(parent.getEnforcestartdatevalidity());
            domain.setEnforcestartdatevalidityinherited(true);

            // Determine if the validTo date correctness should be enforced
            domain.setEnforceenddatevalidity(parent.getEnforceenddatevalidity());
            domain.setEnforceenddatevalidityinherited(true);

            // Determine pseudonymization algorithm
            domain.setAlgorithm((algorithm != null) ? algorithm : parent.getAlgorithm());
            domain.setAlgorithminherited(algorithm == null);
            
            // Determine the alphabet used for pseudonymization
            domain.setAlphabet((alphabet != null) ? alphabet : parent.getAlphabet());
            domain.setAlphabetinherited(true);
            
            // Determine the number of pseudonyms the user wants to create
            domain.setRandomalgorithmdesiredsize(parent.getRandomalgorithmdesiredsize());
            domain.setRandomalgorithmdesiredsizeinherited(true);
            
            // Determine the minimum success probability for the generation of new pseudonyms
            domain.setRandomalgorithmdesiredsuccessprobability(parent.getRandomalgorithmdesiredsuccessprobability());
            domain.setRandomalgorithmdesiredsuccessprobabilityinherited(true);
            
            domain.setMultiplepsnallowed((multiplePsnAllowed != null) ? multiplePsnAllowed : parent.getMultiplepsnallowed());
            domain.setMultiplepsnallowedinherited(multiplePsnAllowed == null);
            
            // Determine pseudonym length
            domain.setPseudonymlength(parent.getPseudonymlength());
            domain.setPseudonymlengthinherited(true);

            // Determine padding character
            domain.setPaddingcharacter(parent.getPaddingcharacter());
            domain.setPaddingcharacterinherited(true);

            // Determine if a check digit should be created
            domain.setAddcheckdigit(parent.getAddcheckdigit());
            domain.setAddcheckdigitinherited(true);

            // Determine if the check digit should be included in the length of the pseudonym
            domain.setLengthincludescheckdigit(parent.getLengthincludescheckdigit());
            domain.setLengthincludescheckdigitinherited(true);
            
            domain.setSuperdomainid(parent.getId());
        } else {
            // No parent information provided

            // Check if either validTo or validityTime is given
            if (validTo == null && validityTime == null) {
                // Both values are missing; inform user and use default
                log.debug("For the domain \"" + domainName + "\" there was neither an end date nor a validity period given."
                        + " The default of 30 years is used.");
                validityTime = DEFAULT_VALIDITY_TIME_IN_SECONDS;
            }

            // Determine validFrom date if not given by the user
            domain.setValidfrom((validFrom != null) ? validFrom.toLocalDateTime() : LocalDateTime.now());
            domain.setValidfrominherited(false);

            // Determine expiration date
            domain.setValidto((validTo != null) ? validTo.toLocalDateTime() : domain.getValidfrom().plusSeconds(validityTime));
            domain.setValidtoinherited(false);

            // Determine if the validFrom date correctness should be enforced
            domain.setEnforcestartdatevalidity(DEFAULT_ENFORCE_START_DATE_VALIDITY);
            domain.setEnforcestartdatevalidityinherited(false);

            // Determine if the validTo date correctness should be enforced
            domain.setEnforceenddatevalidity(DEFAULT_ENFORCE_END_DATE_VALIDITY);
            domain.setEnforceenddatevalidityinherited(false);

            // Determine pseudonymization algorithm
            domain.setAlgorithm((algorithm != null) ? algorithm : DEFAULT_PSEUDONYMIZATION_ALGO);
            domain.setAlgorithminherited(false);
            
            // Determine the alphabet used for pseudonymization
            domain.setAlphabet((alphabet != null) ? alphabet : DEFAULT_PSEUDONYMIZATION_ALPHABET);
            domain.setAlphabetinherited(false);
            
            // Determine the number of pseudonyms the user wants to create
            domain.setRandomalgorithmdesiredsize(DEFAULT_RANDOM_ALGORITHM_DESIRED_SIZE);
            domain.setRandomalgorithmdesiredsizeinherited(false);
            
            // Determine the minimum success probability for the generation of new pseudonyms
            domain.setRandomalgorithmdesiredsuccessprobability(DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY);
            domain.setRandomalgorithmdesiredsuccessprobabilityinherited(false);
            
            domain.setMultiplepsnallowed((multiplePsnAllowed != null) ? multiplePsnAllowed : DEFAULT_ALLOW_MULTIPLE_PSN);
            domain.setMultiplepsnallowedinherited(false);
            
            // Determine pseudonym length
            domain.setPseudonymlength(DEFAULT_PSEUDONYM_LENGTH);
            domain.setPseudonymlengthinherited(false);

            // Determine padding character
            domain.setPaddingcharacter(String.valueOf(DEFAULT_PADDING_CHARACTER));
            domain.setPaddingcharacterinherited(false);

            // Determine if a check digit should be created
            domain.setAddcheckdigit(DEFAULT_ADD_CHECK_DIGIT);
            domain.setAddcheckdigitinherited(false);

            // Determine if the check digit should be included in the length of the pseudonym
            domain.setLengthincludescheckdigit(DEFAULT_LENGTH_INCLUDES_CHECK_DIGIT);
            domain.setLengthincludescheckdigitinherited(false);
            
            domain.setSuperdomainid(0);
        }

        // Insert into database
        String result = domainDBAccessService.insertDomain(domain, request);
        
        if (result.equals(DomainDBAccessService.INSERTION_SUCCESS)) {
            // Return a 201-CREATED status and the location to the domain inside the response header.
            log.info("Successfully created the domain \"" + domainName + "\".");
            return responseService.created(responseContentType, location);
        } else if (result.equals(DomainDBAccessService.INSERTION_DUPLICATE)) {
            // Nothing added since the entry is a duplicate. Return an 200-OK status.
            log.info("The domain requested to be inserted was skipped because it is already in the database.");
            return responseService.ok(responseContentType);
        } else {
            // Creating the domain failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("Adding the domain \"" + domainName + "\" was unsuccessful.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method deletes a domain. A domain name must be provided.
     *
     * @param domainName (required) the name of the domain that should be deleted
     * @param performRecursiveChanges (optional) specifies whether or not changes should be cascaded to sub-domains
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>204-NO_CONTENT</b> status when the deletion was
     * 				successful</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no domain was found for the given name</li>
     * 			<li>a <b>500-INTERNAL_SERVER_ERROR</b> status when the domain
     * 				could not be deleted</li>
     */
    @DeleteMapping("/domain")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'domain-delete')")
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL, message = "Wants to delete a domain.")
    public ResponseEntity<?> deleteDomain(@RequestParam(name = "name", required = true) String domainName,
                                          @RequestParam(name = "recursive", required = false) Boolean performRecursiveChanges,
                                          @RequestHeader(name = "accept", required = false) String responseContentType,
                                          HttpServletRequest request) {
        // Get domain
        Domain domain = domainDBAccessService.getDomainByName(domainName, null);

        // Check if a domain was found. If not, return a 404-NOT_FOUND
        if (domain == null) {
            log.debug("The domain that should be deleted couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Check if all necessary values are given. If not, use defaults.
        if (performRecursiveChanges == null) {
            performRecursiveChanges = DEFAULT_PERFORM_RECURSIVE_CHANGES;
        }

        // Perform deletion
        if (domainDBAccessService.deleteDomain(domain.getName(), performRecursiveChanges, request)) {
            // Successfully deleted a domain, return a 204-NO_CONTENT
            log.info("Successfully deleted the domain \"" + domain.getName() + "\".");
            return responseService.noContent(responseContentType);
        } else {
            // Deletion was unsuccessful, return a 500-INTERNAL_SERVER_ERROR
            log.error("The domain \"" + domain.getName() + "\" could not be deleted.");
            return responseService.internalServerError(responseContentType);
        }
    }
    
    /**
     * This method allows to read only a single attribute of a domain and nothing else.
     * 
     * @param domainName (required) the name of the domain
     * @param attributeName (required) the name of the attribute that should be retrieved from the domain
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>attribute</b> when
     * 				it was found</li>
     * 			<li>a <b>403-FORBIDDEN</b> when the rights for accessing 
     * 				the attribute were not found in the token</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no domain was found for
     * 				the given name or the when given attribute name 
     * 				wasn't found</li>
     */
    @GetMapping("/domains/{domain}/{attribute}")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to read a specific attribute from a domain.")
    public ResponseEntity<?> getDomainAttribute(@PathVariable("domain") String domainName,
    											@PathVariable("attribute") String attributeName,
    		                                    @RequestHeader(name = "accept", required = false) String responseContentType,
    		                                    HttpServletRequest request) {
    	Domain domain = domainDBAccessService.getDomainByName(domainName, request);
    	Domain temp = new Domain();
    	
    	// Check if the domain was found
    	if (domain == null) {
    		return responseService.notFound(responseContentType);
    	}
    	
    	// Get required attribute
    	switch (attributeName.trim().toLowerCase()) {
		case "id": {
			temp.setId(domain.getId());
			break;
		} case "name": {
			temp.setName(domain.getName());
			break;
		} case "prefix": {
			temp.setPrefix(domain.getPrefix());
			break;
		} case "validfrom": {
			temp.setValidfrom(domain.getValidfrom());
			break;
		} case "validfrominherited": {
			temp.setValidfrominherited(domain.getValidfrominherited());
			break;
		} case "validto": {
			temp.setValidto(domain.getValidto());
			break;
		} case "validtoinherited": {
			temp.setValidtoinherited(domain.getValidtoinherited());
			break;
		} case "enforcestartdatevalidity": {
			temp.setEnforcestartdatevalidity(domain.getEnforcestartdatevalidity());
			break;
		} case "enforcestartdatevalidityinherited": {
			temp.setEnforcestartdatevalidityinherited(domain.getEnforcestartdatevalidityinherited());
			break;
		} case "enforceenddatevalidity": {
			temp.setEnforceenddatevalidity(domain.getEnforceenddatevalidity());
			break;
		} case "enforceenddatevalidityinherited": {
			temp.setEnforceenddatevalidityinherited(domain.getEnforceenddatevalidityinherited());
			break;
		} case "algorithm": {
			temp.setAlgorithm(domain.getAlgorithm());
			break;
		} case "algorithminherited": {
			temp.setAlgorithminherited(domain.getAlgorithminherited());
			break;
		} case "alphabet": {
			temp.setAlphabet(domain.getAlphabet());
			break;
		} case "alphabetinherited": {
			temp.setAlphabetinherited(domain.getAlphabetinherited());
			break;
		} case "randomalgorithmdesiredsize": {
			temp.setRandomalgorithmdesiredsize(domain.getRandomalgorithmdesiredsize());
			break;
		} case "randomalgorithmdesiredsizeinherited": {
			temp.setRandomalgorithmdesiredsizeinherited(domain.getRandomalgorithmdesiredsizeinherited());
			break;
		} case "randomalgorithmdesiredsuccessprobability": {
			temp.setRandomalgorithmdesiredsuccessprobability(domain.getRandomalgorithmdesiredsuccessprobability());
			break;
		} case "randomalgorithmdesiredsuccessprobabilityinherited": {
			temp.setRandomalgorithmdesiredsuccessprobabilityinherited(domain.getRandomalgorithmdesiredsuccessprobabilityinherited());
			break;
		} case "multiplepsnallowed": {
			temp.setMultiplepsnallowed(domain.getMultiplepsnallowed());
			break;
		} case "multiplepsnallowedinherited": {
			temp.setMultiplepsnallowedinherited(domain.getMultiplepsnallowedinherited());
			break;
		} case "consecutivevaluecounter": {
			temp.setConsecutivevaluecounter(domain.getConsecutivevaluecounter());
			break;
		} case "pseudonymlength": {
			temp.setPseudonymlength(domain.getPseudonymlength());
			break;
		} case "pseudonymlengthinherited": {
			temp.setPseudonymlengthinherited(domain.getPseudonymlengthinherited());
			break;
		} case "paddingcharacter": {
			temp.setPaddingcharacter(domain.getPaddingcharacter());
			break;
		} case "paddingcharacterinherited": {
			temp.setPaddingcharacterinherited(domain.getPaddingcharacterinherited());
			break;
		} case "addcheckdigit": {
			temp.setAddcheckdigit(domain.getAddcheckdigit());
			break;
		} case "addcheckdigitinherited": {
			temp.setAddcheckdigitinherited(domain.getAddcheckdigitinherited());
			break;
		} case "lengthincludescheckdigit": {
			temp.setLengthincludescheckdigit(domain.getLengthincludescheckdigit());
			break;
		} case "lengthincludescheckdigitinherited": {
			temp.setLengthincludescheckdigitinherited(domain.getLengthincludescheckdigitinherited());
			break;
		} case "salt": {
			temp.setSalt(domain.getSalt());
			break;
		} case "saltlength": {
			temp.setSaltlength(domain.getSaltlength());
			break;
		} case "description": {
			temp.setDescription(domain.getDescription());
			break;
		} case "superdomainid": {
			temp.setSuperdomainid(domain.getSuperdomainid());
			break;
		} default:
			log.debug("The requested attribute was not found.");
			return responseService.notFound(responseContentType);
		}
    	
    	// Check if the user has the rights to access the requested attribute
    	boolean canSeeComplete = authorizationService.currentRequestHasRole("complete-view");
    	
    	if (!"name, prefix, validfrom, validto, multiplepsnallowed, description".contains(attributeName.trim().toLowerCase()) && !canSeeComplete) {
    		log.debug("The user is trying to read protected attributes without the necessary permission.");
    		return responseService.forbidden(responseContentType);
    	}
    	
    	return responseService.ok(responseContentType, new DomainDto().assignPojoValues(temp));
    }

    /**
     * This method retrieves a domain through its name.
     *
     * @param domainName (required) the name of the domain to search for
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>domain</b> when
     * 				it was found</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no domain was found for
     * 				the given name</li>
     */
    @GetMapping("/domain")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to read a domain.")
    public ResponseEntity<?> getDomain(@RequestParam(name = "name", required = true) String domainName,
                                       @RequestHeader(name = "accept", required = false) String responseContentType,
                                       HttpServletRequest request) {
        // Get domain
        Domain domain = domainDBAccessService.getDomainByName(domainName, request);

        if (domain != null) {
            // Successfully retrieved a domain, return it to the user
            DomainDto domainDto = new DomainDto().assignPojoValues(domain);

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                domainDto = domainDto.toReducedStandardView();
            }

            log.debug("Successfully retrieved the domain \"" + domainName + "\".");
            return responseService.ok(responseContentType, domainDto);
        } else {
            // Nothing found, return a 404-NOT_FOUND
            log.debug("No domain with the name \"" + domainName + "\" was found.");
            return responseService.notFound(responseContentType);
        }
    }

    /**
     * This method returns all domains from the database in a minimal version.
     * This endpoint is marked as experimental.
     *
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>list of minimal
     * 				domains</b> when the query was successful</li>
     */
    @GetMapping(value = "/experimental/domains/hierarchy")
    @PreAuthorize("hasRole('domain-list-all')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to read all domains in a minimal representation.")
    public ResponseEntity<?> listDomainHierarchy(@RequestHeader(name = "accept", required = false) String responseContentType,
                                                 HttpServletRequest request) {
        List<Domain> domains = domainDBAccessService.listDomains(request);

        // Determine whether or not a reduced standard view or a complete view is requested
        boolean canSeeComplete = authorizationService.currentRequestHasRole("complete-view");

        List<DomainDto> domainDtos = new ArrayList<>();
        // Create a list of domains
        for (Domain domain : domains) {
            DomainDto domainDto = new DomainDto().assignPojoValues(domain);
            
            if (canSeeComplete) {
                domainDtos.add(domainDto);
            } else {
                domainDtos.add(domainDto.toReducedStandardView());
            }
        }

        return responseService.ok(responseContentType, domainDtos);
    }

    /**
     * Method to update a domain.
     *
     * @param oldDomainName (required) the name of the domain that is to be updated
     * @param performRecursiveChanges (required) specifies whether or not changes should be cascaded to sub-domains
     * @param domainDto (required) the domain object
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status when the update was successful</li>
     * 			<li>a <b>404-NOT_FOUND</b> when the domain that should be
     * 				updated couldn't be found</li>
     * 			<li>a <b>406-NOT_ACCEPTABLE</b> status when the new domain name
     * 				is violating the URI-validity</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when updating the domain
     * 				failed</li>
     */
    @PutMapping("/domain/complete")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #oldDomainName, 'domain-update-complete')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update a complete domain.")
    public ResponseEntity<String> updateDomainComplete(@RequestParam(name = "name", required = true) String oldDomainName,
                                                       @RequestParam(name = "recursive", required = true) Boolean performRecursiveChanges,
                                                       @RequestBody DomainDto domainDto,
                                                       @RequestHeader(name = "accept", required = false) String responseContentType,
                                                       HttpServletRequest request) {
        String newDomainName = domainDto.getName();
        String prefix = domainDto.getPrefix();
        Timestamp validFrom = domainDto.getValidFrom() != null ? Timestamp.valueOf(domainDto.getValidFrom()) : null;
        Timestamp validTo = domainDto.getValidTo() != null ? Timestamp.valueOf(domainDto.getValidTo()) : null;
        Boolean enforceStartDateValidity = domainDto.getEnforceStartDateValidity();
        Boolean enforceEndDateValidity = domainDto.getEnforceEndDateValidity();
        String algorithm = domainDto.getAlgorithm();
        String alphabet = generateAlphabet(algorithm, null);
        Long randomAlgorithmDesiredSize = domainDto.getRandomAlgorithmDesiredSize();
        Double randomAlgorithmDesiredSuccessProbability = domainDto.getRandomAlgorithmDesiredSuccessProbability();
        Boolean multiplePsnAllowed = domainDto.getMultiplePsnAllowed();
        Long consecVal = domainDto.getConsecutiveValueCounter();
        Integer psnLength = domainDto.getPseudonymLength();
        Character paddingChar = domainDto.getPaddingCharacter();
        Boolean addCheckDigit = domainDto.getAddCheckDigit();
        Boolean lengthIncludesCheckDigit = domainDto.getLengthIncludesCheckDigit();
        String salt = domainDto.getSalt();
        Integer saltLength = domainDto.getSaltLength();
        String description = domainDto.getDescription();
        
        if (domainDBAccessService.getAmountOfRecordsInDomain(oldDomainName, null) > 0) {
        	log.warn("Changes to the domain configuration can introduce inconsistencies when creating further pseudonyms.");
        }

        if (salt != null && !this.validateSalt(salt, false)) {
        	// A non-valid salt value was encountered. Return a 400-BAD_REQUEST.
            return responseService.badRequest(responseContentType);
        }

        if (Assertion.assertNullAll(newDomainName, prefix, validFrom, validTo, enforceStartDateValidity,
                enforceEndDateValidity, algorithm, alphabet, randomAlgorithmDesiredSize, 
                randomAlgorithmDesiredSuccessProbability, multiplePsnAllowed, consecVal, psnLength, paddingChar, 
                addCheckDigit, lengthIncludesCheckDigit, salt, saltLength, description)) {
            // An empty object was passed, so there is nothing to update.
            log.debug("The domain DTO passed by the user was empty. Nothing to update.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Get old domain object
        Domain old = domainDBAccessService.getDomainByName(oldDomainName, null);

        // Ensure that the old domain was found
        if (old == null) {
            // The old domain wasn't found
            log.error("The domain that should be updated wasn't found.");
            return responseService.notFound(responseContentType);
        }

        String domainName = (newDomainName != null && !newDomainName.trim().equals("")) ? newDomainName : old.getName();

        // Check if the (new) name is valid in an URI. If not, tell the user and abort
        try {
            @SuppressWarnings("unused")
            URI location = new URI("/api/pseudonymization/domain?name=" + domainName);
        } catch (URISyntaxException e) {
            log.debug("The new domain name is not suitable to be used in a URI. Please choose another name.");
            return responseService.notAcceptable(responseContentType);
        }
        
        // Ensure a valid desired success probability, if the selected algorithm is of the RANDOM-family
        if (algorithm.toUpperCase().startsWith("RANDOM") && randomAlgorithmDesiredSuccessProbability != null) {
        	
        	if (randomAlgorithmDesiredSuccessProbability > 1.0d) {
        		// The success probability was probably provided as a number between 0 and 100, so we transform it into [0,1] 
        		randomAlgorithmDesiredSuccessProbability /= 100.0d;
        	}
        	
        	if (randomAlgorithmDesiredSuccessProbability >= 1.0d) {
        		// The provided number is still too big, switch to default value
        		log.warn("The provided success probability for the pseudonymization process was too big. Will not be updated");
        		randomAlgorithmDesiredSuccessProbability = null;
        	} else if (randomAlgorithmDesiredSuccessProbability <= 0.0d) {
	    		// The provided number is zero or negative, switch to default value
	    		log.warn("The provided success probability for the pseudonymization process was too small. Will not be updated.");
	    		randomAlgorithmDesiredSuccessProbability = null;
	        }
        } else {
        	// Do not update and discard any provided information
        	randomAlgorithmDesiredSuccessProbability = null;
        }

        // Create the updated domain object
        Domain updated = new Domain();
        updated.setName(domainName);
        updated.setPrefix((prefix != null && !prefix.trim().equals("")) ? prefix.trim() : null);
        updated.setValidfrom((validFrom != null) ? validFrom.toLocalDateTime() : null);
        updated.setValidfrominherited((validFrom != null) ? false : null);
        updated.setValidto((validTo != null) ? validTo.toLocalDateTime() : null);
        updated.setValidtoinherited((validTo != null) ? false : null);
        updated.setEnforcestartdatevalidity(enforceStartDateValidity);
        updated.setEnforcestartdatevalidityinherited((enforceStartDateValidity != null) ? false : null);
        updated.setEnforceenddatevalidity(enforceEndDateValidity);
        updated.setEnforceenddatevalidityinherited((enforceEndDateValidity != null) ? false : null);
        updated.setAlgorithm((algorithm != null && !algorithm.trim().equals("")) ? algorithm.trim() : null);
        updated.setAlgorithminherited((algorithm != null && !algorithm.trim().equals("")) ? false : null);
        updated.setAlphabet((alphabet != null && !alphabet.trim().equals("")) ? alphabet.trim() : null);
        updated.setAlphabetinherited((alphabet != null) ? false : null);
        updated.setRandomalgorithmdesiredsize((randomAlgorithmDesiredSize != null) ? randomAlgorithmDesiredSize : null);
        updated.setRandomalgorithmdesiredsizeinherited((randomAlgorithmDesiredSize != null) ? false : null);
        updated.setRandomalgorithmdesiredsuccessprobability((randomAlgorithmDesiredSuccessProbability != null) ? randomAlgorithmDesiredSuccessProbability : null);
        updated.setRandomalgorithmdesiredsuccessprobabilityinherited((randomAlgorithmDesiredSuccessProbability != null) ? false : null);
        updated.setMultiplepsnallowed(multiplePsnAllowed);
        updated.setMultiplepsnallowedinherited((multiplePsnAllowed != null) ? false : null);
        updated.setConsecutivevaluecounter((consecVal != null && consecVal > 0L) ? consecVal : null);
        updated.setPseudonymlength((psnLength != null && psnLength > 0) ? psnLength : null);
        updated.setPseudonymlengthinherited((psnLength != null && psnLength > 0) ? false : null);
        updated.setPaddingcharacter((paddingChar != null && !Character.isWhitespace(paddingChar)) ? paddingChar.toString() : null);
        updated.setPaddingcharacterinherited((paddingChar != null && !Character.isWhitespace(paddingChar)) ? false : null);
        updated.setAddcheckdigit(addCheckDigit);
        updated.setAddcheckdigitinherited((addCheckDigit != null) ? false : null);
        updated.setLengthincludescheckdigit(lengthIncludesCheckDigit);
        updated.setLengthincludescheckdigitinherited((lengthIncludesCheckDigit != null) ? false : null);
        updated.setSalt(salt);
        updated.setSaltlength((saltLength != null && saltLength >= MINIMUM_SALT_LENGTH && saltLength <= MAXIMUM_SALT_LENGTH) ? saltLength : null);
        updated.setDescription(description);

        // Execute update
        if (domainDBAccessService.updateDomain(old, updated, performRecursiveChanges, request)) {
            // Success. Return a 200-OK status.
            log.info("Successfully updated the domain \"" + domainName + "\".");
            return responseService.ok(responseContentType);
        } else {
            // Updating the meta-information failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("Updating the domain \"" + domainName + "\" was unsuccessful.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * Method to update a domain with a reduced set of updatable attributes.
     * The attributes newPrefix, algorithm, consecVal, psnLength, and paddingChar are
     * only updatable when the domain is still empty.
     *
     * @param oldDomainName (required) the name of the domain that is to be updated
     * @param domainDto (required) The domain object
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li> a <b>200-OK</b> status when the update was successful</li>
     * 			<li> a <b>404-NOT_FOUND</b> when the domain that should be
     * 				updated couldn't be found</li>
     * 			<li> a <b>422-UNPROCESSABLE_ENTITY</b> when updating the domain
     * 				failed</li>
     */
    @PutMapping("/domain")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #oldDomainName, 'domain-update')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update a domain.")
    public ResponseEntity<String> updateDomain(@RequestParam(name = "name", required = true) String oldDomainName,
                                               @RequestBody DomainDto domainDto,
                                               @RequestHeader(name = "accept", required = false) String responseContentType,
                                               HttpServletRequest request) {
        // Get old domain object
        Domain old = domainDBAccessService.getDomainByName(oldDomainName, null);

        String newName = domainDto.getName().trim();
        String prefix = domainDto.getPrefix();
        Timestamp validFrom = domainDto.getValidFrom() != null ? Timestamp.valueOf(domainDto.getValidFrom()) : null;
        Timestamp validTo = domainDto.getValidTo() != null ? Timestamp.valueOf(domainDto.getValidTo()) : null;
        String algorithm = domainDto.getAlgorithm();
        Boolean multiplePsnAllowed = domainDto.getMultiplePsnAllowed();
        String description = domainDto.getDescription();

        if (Assertion.assertNullAll(newName, prefix, validFrom, validTo, algorithm, multiplePsnAllowed, description)) {
            // An empty object was passed, so there is nothing to update.
            log.debug("The domain DTO passed by the user was empty. Nothing to update.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Ensure that the old domain was found
        if (old == null) {
            // The old domain wasn't found
            log.error("The domain that should be updated wasn't found.");
            return responseService.notFound(responseContentType);
        }

        // Create the updated domain object
        Domain updated = new Domain();
        updated.setName(newName);
        updated.setValidfrom((validFrom != null) ? validFrom.toLocalDateTime() : null);
        updated.setValidfrominherited((validFrom != null) ? false : null);
        updated.setValidto((validTo != null) ? validTo.toLocalDateTime() : null);
        updated.setValidtoinherited((validTo != null) ? false : null);
        updated.setDescription(description);

        // Allow the following changes only when the domain does not contain any records yet
        if (domainDBAccessService.getAmountOfRecordsInDomain(old.getName(), null) == 0) {
            updated.setPrefix((prefix != null && !prefix.trim().equals("")) ? prefix.trim() : null);
            updated.setAlgorithm((algorithm != null && !algorithm.trim().equals("")) ? algorithm : null);
            updated.setAlgorithminherited((algorithm != null && !algorithm.trim().equals("")) ? false : null);
            updated.setMultiplepsnallowed(multiplePsnAllowed);
            updated.setMultiplepsnallowedinherited((multiplePsnAllowed != null) ? false : null);
        } else {
            log.info("Since the domain \"" + newName != null ? newName : old.getName() + "\" isn't empty, updates of the prefix, the algorithm, "
                    + "the consecutive value, the pseudonym-length, or the padding character can't be processed and are ignored.");
        }
        
        // All other domain attributes are null and are therefore correctly left as they are

        // Execute update
        if (domainDBAccessService.updateDomain(old, updated, DEFAULT_PERFORM_RECURSIVE_CHANGES, request)) {
            // Success. Return a 200-OK status.
            log.info("Successfully updated the domain \"" + newName + "\".");
            return responseService.ok(responseContentType);
        } else {
            // Updating the meta-information failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("Updating the domain \"" + newName + "\" was unsuccessful.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method updates the salt variable of a domain.
     *
     * @param domainName (required) the name of the domain for which the salt value should be updated
     * @param newSalt (required) the new salt value
     * @param allowEmpty (optional) determines whether or not the given salt is allowed to be empty
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status when the update was successful</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when the given salt value was
     * 				empty</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no domain was found for the
     * 				given name</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when updating the salt
     * 				value failed</li>
     */
    @PutMapping("/domains/{domain}/salt")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'domain-update-salt')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update the salt from a domain.")
    public ResponseEntity<?> updateSalt(@PathVariable("domain") String domainName,
                                        @RequestParam(name = "salt", required = true) String newSalt,
                                        @RequestParam(name = "allowEmpty", required = false, defaultValue = "false") Boolean allowEmpty,
                                        @RequestHeader(name = "accept", required = false) String responseContentType,
                                        HttpServletRequest request) {

    	// Validate the given salt value
        if (!this.validateSalt(newSalt, allowEmpty)) {
        	// A non-valid salt value was encountered. Return a 400-BAD_REQUEST.
            return responseService.badRequest(responseContentType);
        }

        // Retrieve old domain
        Domain old = domainDBAccessService.getDomainByName(domainName, null);

        // Check if the retrieved domain is null
        if (old == null) {
            // Couldn't find the domain that should be updated. Return a 404-NOT_FOUND.
            log.info("The domain for which the updated salt-value was given, couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Create update-domain
        Domain updated = new Domain();
        updated.setSalt(newSalt);
        updated.setSaltlength((allowEmpty && newSalt.isBlank()) ? 0 : newSalt.length());

        // Execute update
        if (domainDBAccessService.updateDomain(old, updated, false, request)) {
            // Success. Return a 200-OK status.
            log.info("Successfully updated the salt for the domain \"" + domainName + "\". It is now \"" + newSalt + "\".");
            return responseService.ok(responseContentType);
        } else {
            // Updating the salt failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("Updating the salt for the domain \"" + domainName + "\" was unsuccessful.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }
	
	/**
	 * Calculate the length of pseudonyms needed so that the desired number of pseudonyms can be stored.
	 * 
	 * @param desiredSize the desired number of pseudonyms that can be generated for the domain in question
	 * @param desiredSuccessProbability the desired probability of successful pseudonym generation
	 * @param alphabet the alphabet used for generating the pseudonyms
	 * @return the minimal length the pseudonyms must have so that the desired amount can be generated
	 */
	private int calculatePseudonymLength(Long desiredSize, Double desiredSuccessProbability, String alphabet) {
		// Collect variables
		int m = DEFAULT_NUMBER_OF_RETRIES;
		double T = desiredSuccessProbability;
		long n = desiredSize;
		
		// Calculate the amount of possible pseudonyms needed so that the desired amount of pseudonyms will be 
		// reasonably probable created: (1-(1-((k-n)/k))^m)>T -->
		// k > n/((1-T)^(1/m)) --> k = ⌈n/((1-T)^(1/m))⌉
		double k = Math.ceil(n/Math.pow((1.0-T), (1.0/m)));
		
		// Calculate length of the pseudonyms: k = alphabet.length^psn_length
		double psnLength = Math.ceil(Math.log(k)/Math.log(alphabet.length()));
		
		return (int) psnLength;
	}
	
	/**
	 * Method to generate the alphabet depending on the given algorithm.
	 * 
	 * @param algorithm the user-given algorithm
	 * @param alphabet the alphabet provided by the user, if available
	 * @return the alphabet that matches the algorithm as a String
	 */
	private String generateAlphabet(String algorithm, String alphabet) {
		// The possible alphabets
		String HEXADECIMAL_ALPHABET = "ABCDEF0123456789";
		String NUMBERS_ONLY_ALPHABET = "0123456789";
		String LETTERS_ONLY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		String LETTERS_AND_NUMBERS_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		String LETTERS_AND_NUMBERS_WITHOUT_BIOS_ALPHABET = "ACDEFGHJKLMNPQRTUVWXYZ0123456789";
		
		// If nothing was provided, return the default alphabet
		if (algorithm == null) {
			return DEFAULT_PSEUDONYMIZATION_ALPHABET;
		}
		
		// Generate alphabet depending on the used algorithm
		switch (algorithm.trim().toUpperCase()) {
	        case "MD5":
	        case "SHA1":
	        case "SHA2":
	        case "SHA3":
	        case "BLAKE3":
	        case "XXHASH": {
	        	return HEXADECIMAL_ALPHABET;
	        }
	        case "CONSECUTIVE":
	        case "RANDOM_NUM": {
	        	return NUMBERS_ONLY_ALPHABET;
	        }
	        case "RANDOM": {
	        	// If "RANDOM" was selected, use the user-provided alphabet or A-Z0-9 if nothing was provided
	        	return (alphabet != null && !alphabet.isBlank()) ? alphabet : LETTERS_AND_NUMBERS_ALPHABET;
	        }
	        case "RANDOM_HEX": {
	        	return HEXADECIMAL_ALPHABET;
	        }
	        case "RANDOM_LET": {
	        	return LETTERS_ONLY_ALPHABET;
	        }
	        case "RANDOM_SYM": {
	        	return LETTERS_AND_NUMBERS_ALPHABET;
	        }
	        case "RANDOM_SYM_BIOS": {
	        	return LETTERS_AND_NUMBERS_WITHOUT_BIOS_ALPHABET;
	        }
	        default: {
	            // Unrecognized algorithm
	            log.warn("The pseudonymization algorithm that was requested (" + algorithm + ") wasn't recognized.");
	            return null;
	        }
		}
	}

    /**
     * Used to validate the salt value given during an update request.
     *
     * @param salt the salt as a string
     * @param allowEmpty this flag indicates whether an empty salt value is okay
     * @return {@code true} if the input is a valid salt value, {@code false} if not.
     */
    private boolean validateSalt(String salt, Boolean allowEmpty){
        if (salt.isBlank() && !allowEmpty) {
            // The salt was empty when a non-empty salt was expected.
            log.debug("The new salt given by the user was empty.");
            return false;
        }

        if (salt.length() < MINIMUM_SALT_LENGTH && !allowEmpty) {
            // The new salt is too short.
            log.debug("The new salt given by the user was too short. It should be at least " + MINIMUM_SALT_LENGTH + " characters long.");
            return false;
        }

        if (salt.length() > MAXIMUM_SALT_LENGTH) {
            // The new salt is too long.
            log.debug("The new salt given by the user was too long. It should be no more than " + MAXIMUM_SALT_LENGTH + " characters long.");
            return false;
        }
        
        return true;
    }
}

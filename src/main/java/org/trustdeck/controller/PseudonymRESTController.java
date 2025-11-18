/*
 * Trust Deck Services
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

package org.trustdeck.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.*;
import org.trustdeck.algorithms.LuhnCheckDigit;
import org.trustdeck.algorithms.LuhnMod10CheckDigit;
import org.trustdeck.algorithms.LuhnMod16CheckDigit;
import org.trustdeck.algorithms.LuhnMod26CheckDigit;
import org.trustdeck.algorithms.LuhnMod32CheckDigit;
import org.trustdeck.algorithms.LuhnMod36CheckDigit;
import org.trustdeck.algorithms.PseudonymizationFactory;
import org.trustdeck.algorithms.Pseudonymizer;
import org.trustdeck.algorithms.RandomNumberPseudonymizer;
import org.trustdeck.dto.PseudonymDTO;
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.model.IdentifierItem;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.service.AuthorizationService;
import org.trustdeck.service.DomainDBAccessService;
import org.trustdeck.service.PseudonymDBAccessService;
import org.trustdeck.service.ResponseService;
import org.trustdeck.utils.Assertion;
import org.trustdeck.utils.Utility;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class represents a REST-API controller encapsulating the requests for pseudonym-records.
 * This REST-API offers full access to the data items.
 *
 * @author Armin Müller & Eric Wündisch
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api/pseudonymization")
public class PseudonymRESTController {

    /** The default maximum allowed batch size. */
    private static final int DEFAULT_PSEUDONYM_BATCH_LENGTH = 50000;

    /** Enables the access to the domain specific database access methods. */
    @Autowired
    private DomainDBAccessService domainDBAccessService;

    /** Enables the access to the pseudonym specific database access methods. */
    @Autowired
    private PseudonymDBAccessService pseudonymDBAccessService;

    /** Enables services for better working with responses. */
    @Autowired
    private ResponseService responseService;

    /** Provides functionality to ensure proper rights and roles when accessing the endpoints. */
    @Autowired
    private AuthorizationService authorizationService;
	
    /**
     * Method to check if the current domain's filling rate exceeds a point where the generation of unseen
     * pseudonyms is not anymore reasonably probable.
     * 
     * @param domain the domain to check
     */
	private void checkDomainFillingRate(Domain domain) {
		/* A high filling rate of the domain can result in too few possible pseudonyms left for generation.
		* We can calculate a threshold for which we can reasonably probable generate pseudonyms using this formula: 
		* 1-(1-((k-n)/k))^m > T => n < k*(1-T)^(1/m); with k being the number of theoretically possible pseudonyms 
		* (here number of alphabet-symbols a and psnLength characters, so k=a^psnLength); n being the number of 
		* reasonable probable pseudonyms; m being the number of retries; and T being the probability threshold which 
		* we deem to be reasonable probable (here: 99.999998% (probability of not getting struck by lightning), 
		* meaning that we want to successfully generate a pseudonym in a maximum of m=3 tries in 99.999998% of all 
		* cases).
		* --> Warn the user, if the domain is too full
		*/
		Double T = domain.getRandomalgorithmdesiredsuccessprobability();
		Double k = Math.pow(10.0d, (double) domain.getPseudonymlength());
		Double n = k * Math.pow((1.0d - T), (1.0d / (double) Pseudonymizer.DEFAULT_NUMBER_OF_RETRIES));
		
		Integer existingPseudonyms = domainDBAccessService.getAmountOfRecordsInDomain(domain.getName(), null);
		existingPseudonyms = existingPseudonyms == null ? 0 : existingPseudonyms; // Ignore unsuccessful database retrieval
		
		if (existingPseudonyms > n) {
			// The domain reached it's filling point (~49%) at which the probability to generate a 
			// previously unseen pseudonym in m tries is no longer greater than 99.999998%.
			log.warn("The number of reasonably probable generated pseudonyms for this domain is exhausted. The probability "
					+ "to generate collisions will rise and at some point prevent the generation of non-colliding pseudonyms "
					+ "in a certain number of retries.");
		}
	}

    /**
     * This method creates new pseudonymization-records in batches.
     * When an external created pseudonym is given, no new pseudonym
     * is created but the given one is stored.
     *
     * @param domainName (required) the name of the domain the pseudonyms should be in
     * @param omitPrefix (optional) determines whether or not the prefix should be added to the pseudonym
     * @param recordDtoList (required) the list of necessary information, formatted as a JSON to match the recordDto
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>201-CREATED</b> status and a list of the created
     * 				<b>pseudonym-objects</b> on success</li>
     * 			<li>a <b>404-NOT_FOUND</b> when the domain wasn't found</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the insertion into
     * 				the database failed (e.g. due to an expired domain validity
     * 				period or a batch that exceeds the maximum allowed batch size).</li>
     * 			<li>a <b>500-INTERNAL_SERVER_ERROR</b> when the pseudonymization
     * 				failed</li>
     * 			<li>a <b>507-INSUFFICICENT_STORAGE</b> when the domain reached its 
     * 				filling point and we therefore only generated collisions.</li>
     */
    @PostMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-create-batch')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> createRecordBatch(@PathVariable("domain") String domainName,
                                               @RequestParam(name = "omitPrefix", required = false, defaultValue = "false") Boolean omitPrefix,
                                               @RequestBody List<PseudonymDTO> recordDtoList,
                                               @RequestHeader(name = "accept", required = false) String responseContentType,
                                               HttpServletRequest request) {
        // Check that the batch size isn't too big.
        if (recordDtoList.size() > DEFAULT_PSEUDONYM_BATCH_LENGTH) {
            // The batch size exceeded the limit. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("The given list of objects is too big. The maximum allowed batch size is: " + DEFAULT_PSEUDONYM_BATCH_LENGTH);
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the records belong to
        Domain domain = domainDBAccessService.getDomainByName(domainName, null);

        if (domain == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain in which the records should be created couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Check if the domain still allows adding pseudonyms
        if (domain.getValidto().isBefore(LocalDateTime.now())) {
            // Expired validity period. No changes allowed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.debug("The validity period of the domain has already expired. No changes allowed.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Transform the user-given inputs into a list of pseudonym objects.
        List<PseudonymDTO> pseudonyms = new ArrayList<>();
        for (PseudonymDTO pseudonymDTO : recordDtoList) {
            // Start creating the record
            PseudonymDTO p = new PseudonymDTO();
            p.setIdentifierItem(pseudonymDTO.getIdentifierItem());
            p.setDomainName(domain.getName());

            // Pseudonymize the identifier and store it in the object
            String pseudonym = null;
            if (pseudonymDTO.getPsn() != null && !pseudonymDTO.getPsn().trim().equals("")) {
                // A pseudonym was already given --> store it instead of creating a new one if it's in the correct format
                String psn = pseudonymDTO.getPsn().trim();
                if (omitPrefix != null && omitPrefix) {
                    pseudonym = psn;
                } else {
                    pseudonym = psn.startsWith(domain.getPrefix()) ? psn : domain.getPrefix() + psn;
                }
            } else {
                // Generate a new pseudonym
                String prefix = (omitPrefix != null && omitPrefix) ? "" : domain.getPrefix(); // Omitting the prefix here shouldn't be the norm
                Pseudonymizer pseudonymizer = new PseudonymizationFactory().getPseudonymizer(domain);
                pseudonym = pseudonymizer.pseudonymize(pseudonymDTO.getIdentifierItem().getIdentifier() + pseudonymDTO.getIdentifierItem().getIdType() + domain.getSalt(), prefix);
                pseudonym = domain.getAddcheckdigit() ? pseudonymizer.addCheckDigit(pseudonym, domain.getLengthincludescheckdigit(), domain.getName(), prefix) : pseudonym;

                if (domain.getAlgorithm().toUpperCase().startsWith("RANDOM") && pseudonym.equals(RandomNumberPseudonymizer.DOMAIN_FULL)) {
                	// Pseudonymization failed due to too many collisions. The domain reached it's filling point (~49%) 
                	// at which the probability to generate a previously unseen pseudonym in 25 tries is no longer greater
                	// than 99.999998%.
                	log.warn("Couldn't generate a new pseudonym due to too many pseudonyms being already in the database. ");
                	return responseService.insufficientStorage(responseContentType);
                } else if (pseudonym == null && domain.getAlgorithm().toUpperCase().startsWith("RANDOM")) {
                	// Pseudonymization failed: probably no non-colliding pseudonym was found.
                	log.warn("Pseudonymization failed for identifier \"" + pseudonymDTO.getIdentifierItem().getIdentifier() + "\" and idType \"" + pseudonymDTO.getIdentifierItem().getIdType() + "\". "
                			+ "Probably due to collisions with other pseudonyms. Try a greater pseudonym-length.");
                	return responseService.unprocessableEntity(responseContentType);
            	} else if (pseudonym == null) {
                    // Pseudonymization failed. Return a 500-INTERNAL_SERVER_ERROR.
                    log.error("Pseudonymization failed for identifier \"" + pseudonymDTO.getIdentifierItem().getIdentifier() + "\" and idType \"" + pseudonymDTO.getIdentifierItem().getIdType() + "\".");
                    return responseService.internalServerError(responseContentType);
                }
            }
            p.setPsn(pseudonym);

            // Determine validFrom date if (not) given by the user
            if (pseudonymDTO.getValidFrom() != null) {
                if (domain.getEnforcestartdatevalidity()) {
                    // Ensure that the given start date isn't before the start date of the domain
                    p.setValidFrom(pseudonymDTO.getValidFrom().isAfter(domain.getValidfrom()) ? pseudonymDTO.getValidFrom() : domain.getValidfrom());
                    p.setValidFromInherited(!pseudonymDTO.getValidFrom().isAfter(domain.getValidfrom()));
                } else {
                    p.setValidFrom(pseudonymDTO.getValidFrom());
                    p.setValidFromInherited(false);
                }
            } else {
                // Nothing given by the user. Use domain information.
                p.setValidFrom(domain.getValidfrom());
                p.setValidFromInherited(true);
            }

            // Determine validTo date if (not) given by the user
            if (pseudonymDTO.getValidTo() != null) {
                // End date of validity period is given
                if (domain.getEnforceenddatevalidity()) {
                    // Ensure that the given end date isn't after the end date of the domain
                    p.setValidTo((pseudonymDTO.getValidTo().isBefore(domain.getValidto())) ? pseudonymDTO.getValidTo() : domain.getValidto());
                    p.setValidToInherited(!pseudonymDTO.getValidTo().isBefore(domain.getValidto()));
                } else {
                    p.setValidTo(pseudonymDTO.getValidTo());
                    p.setValidToInherited(false);
                }
            } else if (pseudonymDTO.getValidTo() == null && pseudonymDTO.getValidityTime() != null) {
                // A validity period was given
                Long vTime = Utility.validityTimeToSeconds(pseudonymDTO.getValidityTime());

                if (domain.getEnforceenddatevalidity()) {
                    // Ensure that the given validity period ends before the end date of the domain
                    p.setValidTo((p.getValidFrom().plusSeconds(vTime).isBefore(domain.getValidto())) ? p.getValidFrom().plusSeconds(vTime) : domain.getValidto());
                    p.setValidToInherited(!p.getValidFrom().plusSeconds(vTime).isBefore(domain.getValidto()));
                } else {
                    p.setValidTo(p.getValidFrom().plusSeconds(vTime));
                    p.setValidToInherited(false);
                }
            } else {
                // Nothing was given: use date from domain
                p.setValidTo(domain.getValidto());
                p.setValidToInherited(true);
            }

            // Add the newly created pseudonym to the list
            pseudonyms.add(p);
        }

        // Insert the list of pseudonyms in one batch
        String result = pseudonymDBAccessService.createPseudonymBatch(pseudonyms, domain.getId(), request);

        // Evaluate the result
        if (result.equals(PseudonymDBAccessService.INSERTION_SUCCESS)) {
            // Success. Return a status code 201-CREATED and the pseudonym as payload.
            List<PseudonymDTO> pseudonymDTOs = new ArrayList<>();
            List<String> pseudonymDtoStrings = new ArrayList<>();

            for (PseudonymDTO p : pseudonyms) {
                // Determine whether or not a reduced standard view or a complete view is requested
                if (!authorizationService.currentRequestHasRole("complete-view")) {
                    p.toReducedStandardView();
                }

                // Process the DTO depending on the response`s media type
                if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
                    pseudonymDtoStrings.add(p.toRepresentationString());
                } else {
                    pseudonymDTOs.add(p);
                }
            }

            // Log success message and return status
            log.debug("Successfully inserted the batch of " + pseudonyms.size() + " pseudonym-records.");

            if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
                return responseService.created(responseContentType, pseudonymDtoStrings);
            } else {
                return responseService.created(responseContentType, pseudonymDTOs);
            }
        } else {
            // Nothing added. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("Insertion of a batch of pseudonym-records failed.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method creates a new pseudonymization-record.
     * When an external created pseudonym is given, no new pseudonym
     * is created but the given one is stored.
     * This method functions as a get-method if the record already exists.
     *
     * @param domainName (required) the name of the domain the pseudonym should be in
     * @param pseudonymDTO (required) the Record object
     * @param omitPrefix (optional) determines whether or not the prefix should be added to the pseudonym
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>200-OK</b> status and the <b>pseudonym</b> when the
     * 				requested insertion would be a duplicate</li>
     * 			<li>a <b>201-CREATED</b> status and the created record on
     * 				success</li>
     * 			<li>a <b>404-NOT_FOUND</b> when the domain wasn't found</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the insertion into
     * 				the database failed (e.g. due to an expired domain validity
     * 				period).</li>
     * 			<li>a <b>500-INTERNAL_SERVER_ERROR</b> when the pseudonymization
     * 				failed</li>
     * 			<li>a <b>507-INSUFFICICENT_STORAGE</b> when the domain reached its 
     * 				filling point and we therefore only generated collisions.</li>
     */
    @PostMapping("/domains/{domain}/pseudonym")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-create')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> createRecord(@PathVariable("domain") String domainName,
                                          @RequestBody PseudonymDTO pseudonymDTO,
                                          @RequestParam(name = "omitPrefix", required = false, defaultValue = "false") Boolean omitPrefix,
                                          @RequestHeader(name = "accept", required = false) String responseContentType,
                                          HttpServletRequest request) {

        if (!pseudonymDTO.validate() || !pseudonymDTO.isValidStandardView()) {
        	log.debug("The given PseudonymDTO was either invalid or had information that is not allowed in it.");
            return responseService.unprocessableEntity(responseContentType);
        }

        String identifier = pseudonymDTO.getIdentifierItem().getIdentifier();
        String idType = pseudonymDTO.getIdentifierItem().getIdType();
        String psn = pseudonymDTO.getPsn();
        Timestamp validFrom = pseudonymDTO.getValidFrom() != null ? Timestamp.valueOf(pseudonymDTO.getValidFrom()) : null;
        Timestamp validTo = pseudonymDTO.getValidTo() != null ? Timestamp.valueOf(pseudonymDTO.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(pseudonymDTO.getValidityTime());

        if (Assertion.assertNullAll(identifier, idType, psn, validFrom, validTo, validityTime, domainName)) {
            // An empty object was passed, so there is nothing to create.
            log.debug("The record DTO passed by the user was empty. Nothing to create.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the record belongs to
        Domain domain = domainDBAccessService.getDomainByName(domainName, null);

        if (domain == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain in which the record should be created couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Check if the domain still allows adding pseudonyms
        if (domain.getValidto().isBefore(LocalDateTime.now())) {
            // Expired validity period. No changes allowed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.debug("The validity period of the domain has already expired. No changes allowed.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Start creating the record
        PseudonymDTO p = new PseudonymDTO();
        p.setIdentifierItem(pseudonymDTO.getIdentifierItem());
        p.setDomainName(domainName);

        // Determine validFrom date if (not) given by the user
        if (validFrom != null) {
            if (domain.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                p.setValidFrom((validFrom.after(Timestamp.valueOf(domain.getValidfrom()))) ? validFrom.toLocalDateTime() : domain.getValidfrom());
                p.setValidFromInherited(!validFrom.after(Timestamp.valueOf(domain.getValidfrom())));
            } else {
                p.setValidFrom(validFrom.toLocalDateTime());
                p.setValidFromInherited(false);
            }
        } else {
            p.setValidFrom(domain.getValidfrom());
            p.setValidFromInherited(true);
        }

        // Determine validTo date if (not) given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (domain.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                p.setValidTo((validTo.before(Timestamp.valueOf(domain.getValidto()))) ? validTo.toLocalDateTime() : domain.getValidto());
                p.setValidToInherited(!validTo.before(Timestamp.valueOf(domain.getValidto())));
            } else {
                p.setValidTo(validTo.toLocalDateTime());
                p.setValidToInherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (domain.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                p.setValidTo((p.getValidFrom().plusSeconds(validityTime).isBefore(domain.getValidto())) ? p.getValidFrom().plusSeconds(validityTime) : domain.getValidto());
                p.setValidToInherited(!p.getValidFrom().plusSeconds(validityTime).isBefore(domain.getValidto()));
            } else {
                p.setValidTo(p.getValidFrom().plusSeconds(validityTime));
                p.setValidToInherited(false);
            }
        } else {
            // Nothing was given: use date from domain
            p.setValidTo(domain.getValidto());
            p.setValidToInherited(true);
        }

        // Pseudonymize the identifier and store it in the object
        String pseudonym = null;
        if (psn != null && !psn.isBlank()) {
            // A pseudonym was already given --> store it instead of creating a new one if it's in the correct format
            if (omitPrefix != null && omitPrefix) {
                pseudonym = psn.trim();
            } else {
                pseudonym = psn.trim().startsWith(domain.getPrefix()) ? psn.trim() : domain.getPrefix() + psn.trim();
            }
        } else {
        	// Generate a new pseudonym
            pseudonym = pseudonymize(identifier, idType, domain, omitPrefix);
        }
        
        if (pseudonym == null) {
            // Pseudonymization failed. Return a 500-INTERNAL_SERVER_ERROR.
            log.error("Pseudonymization failed for identifier \"" + identifier + "\" and idType \"" + idType + "\".");
            return responseService.internalServerError(responseContentType);
        }
        
        p.setPsn(pseudonym);
        
        // Insert the pseudonym-record into the database
        String result = pseudonymDBAccessService.createPseudonym(p, domain.getId(), domain.getMultiplepsnallowed(), request);
        
        // If a random algorithm is used, check if we generated a duplicate. If so, retry.
        if (domain.getAlgorithm().toUpperCase().startsWith("RANDOM")) {
	        // Retry DEFAULT_NUMBER_OF_RETRIES - 1 times
        	for (int i = 1; i < Pseudonymizer.DEFAULT_NUMBER_OF_RETRIES; i++) {
	        	// Check if its actually a duplicate
        		if (result.equals(PseudonymDBAccessService.INSERTION_DUPLICATE_PSEUDONYM)) {
					// Check the domain's filling rate
        			checkDomainFillingRate(domain);
	        		
	        		// Retry
	        		p.setPsn(pseudonymize(identifier, idType, domain, omitPrefix));
	        		result = pseudonymDBAccessService.createPseudonym(p, domain.getId(), domain.getMultiplepsnallowed(), request);
				} else {
					// Not a duplicate
					break;
				}
			}
        	
        	// Re-check if it's still a duplicate
        	if (result.equals(PseudonymDBAccessService.INSERTION_DUPLICATE_PSEUDONYM)) {
        		log.warn("Couldn't generate a new pseudonym due to collisions due to too many pseudonyms being already in the database.");
            	return responseService.insufficientStorage(responseContentType);
        	}
    	}

        // Evaluate the result
        if (result.equals(PseudonymDBAccessService.INSERTION_SUCCESS)) {
            // Success. Return a status code 201-CREATED and the pseudonym as payload.
            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                p.toReducedStandardView();
            }

            log.debug("Successfully inserted a new pseudonym-record (" + pseudonym + ").");
            return responseService.created(responseContentType, p);
        } else if (result.equals(PseudonymDBAccessService.INSERTION_DUPLICATE_IDENTIFIER)) {
            // Nothing added since the entry is a duplicate. Return an 200-OK status.
        	List<PseudonymDTO> ps = pseudonymDBAccessService.getPseudonym(domainName, p.getIdentifierItem(), psn, null);
            if (ps == null || ps.isEmpty()) {
            	// Could not find the pseudonym object that was just created
            	log.error("Insertion of a new pseudonym-record failed.");
            	return responseService.unprocessableEntity(responseContentType);
            }
            
            // Determine whether or not a reduced standard view or a complete view is requested
            PseudonymDTO psnDTO = ps.getFirst();
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                psnDTO.toReducedStandardView();
            }

            log.debug("The pseudonym-record requested to be inserted was skipped because it is already in the database.");
            return responseService.ok(responseContentType, psnDTO);
        } else {
            // Nothing added. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("Insertion of a new pseudonym-record failed.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method deletes all records in the given domain.
     *
     * @param domainName (required) the name of the domain the pseudonyms are in
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>204-NO_CONTENT</b> status when the deletion was
     * 				successful</li>
     * 			<li>a <b>206-PARTIAL_CONTENT</b> status and a list of deletion 
     * 				results matching the input list</li>
     * 			<li>a <b>404-NOT_FOUND</b> when the given domain wasn't
     * 				found</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the
     * 				records could not be deleted</li>
     */
    @DeleteMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-delete-batch')")
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> deleteRecordBatch(@PathVariable("domain") String domainName,
                                               @RequestHeader(name = "accept", required = false) String responseContentType,
                                               HttpServletRequest request) {
        // Retrieve the domain the records belong to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);
        if (d == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain where the records should be searched couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Retrieve the pseudonym-records
        List<PseudonymDTO> records = domainDBAccessService.getAllRecordsInDomain(domainName, null);

        // Check if anything was found
        if (records == null) {
            // Something went wrong, return a 422-UNPROCESSABLE_ENTITY
            log.error("Retrieving the records for the deletion in the domain \"" + domainName + "\" failed.");
            return responseService.unprocessableEntity(responseContentType);
        } else if (records.size() == 0) {
            // Nothing was found. Return a 204-NO_CONTENT status.
            log.debug("No records were found. Nothing to delete");
            return responseService.noContent(responseContentType);
        }

        // Perform deletion
        List<Boolean> results = pseudonymDBAccessService.deletePseudonymBatch(records, d.getId(), request);
        if (results != null && results.contains(true) && !results.contains(false)) {
            // Successfully deleted the batch, return a 204-NO_CONTENT.
            log.debug("Successfully deleted all pseudonyms from domain \"" + domainName + "\".");
            return responseService.noContent(responseContentType);
        } else if (results != null && results.contains(true) && results.contains(false)) {
        	// Partially successful deletion
        	log.debug("Partially deleted the batch of pseudonyms.");
        	return responseService.partialContent(responseContentType, results);
    	} else {
            // Deletion was unsuccessful, return a 422-UNPROCESSABLE_ENTITY
            log.error("Deletion of the records in domain \"" + domainName + "\" was unsuccessful.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method deletes a pseudonym-record. It must be given an identifier and its
     * type or a pseudonym.
     *
     * @param domainName (required) the name of the domain the record is in
     * @param identifier (optional) the identifier to search for
     * @param idType (optional) the type of the identifier
     * @param psn (optional) the pseudonym to search for
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>204-NO_CONTENT</b> status when the deletion was
     * 				successful</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when neither an identifier, nor
     * 				a pseudonym were given</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no record was found for the
     * 				given identifier/pseudonym</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the
     * 				record could not be deleted</li>
     */
    @DeleteMapping("/domains/{domain}/pseudonym")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-delete')")
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> deleteRecord(@PathVariable("domain") String domainName,
                                          @RequestParam(name = "id", required = false) String identifier,
                                          @RequestParam(name = "idType", required = false) String idType,
                                          @RequestParam(name = "psn", required = false) String psn,
                                          @RequestHeader(name = "accept", required = false) String responseContentType,
                                          HttpServletRequest request) {
        // Check if any record-identifying information was given
        // If so, perform deletion
        IdentifierItem idItem = IdentifierItem.builder().identifier(identifier).idType(idType).build();
        boolean deletionSuccessful = false;
        if (Assertion.assertNotNullAll(identifier, idType, psn)) {
            // All necessary info was given, perform deletion
            deletionSuccessful = pseudonymDBAccessService.deletePseudonym(domainName, idItem, psn, request);
        } else if (Assertion.assertNotNullAll(identifier, idType) && psn == null) {
            // Delete through identifier
            List<PseudonymDTO> pList = pseudonymDBAccessService.getPseudonym(domainName, idItem, null, null);

            // Check if a record with the given identifier exists
            if (pList == null || pList.size() == 0) {
                log.debug("There was no pseudonym-record found for the given identifier and idType.");
                return responseService.notFound(responseContentType);
            } else if (pList.size() > 1) {
            	log.debug("The deletion for the given identifier and idType would have affected more than one record which is not allowed.");
            	return responseService.unprocessableEntity(responseContentType);
            }

            // Perform deletion
            deletionSuccessful = pseudonymDBAccessService.deletePseudonym(domainName, idItem, pList.get(0).getPsn(), request);
        } else if (Assertion.assertNullAll(identifier, idType) && psn != null) {
            // Delete through pseudonym
            List<PseudonymDTO> p = pseudonymDBAccessService.getPseudonym(domainName, null, psn, null);

            // Check if a record with the given pseudonym exists
            if (p == null || p.size() == 0) {
                log.debug("There was no pseudonym-record found for the given identifier and idType.");
                return responseService.notFound(responseContentType);
            }

            // Perform deletion
            deletionSuccessful = pseudonymDBAccessService.deletePseudonym(domainName, p.get(0).getIdentifierItem(), psn, request);
        } else {
            // Invalid configuration of parameters encountered, return an error 400-BAD_REQUEST
            log.debug("Invalid configuration of parameters. At least an id and idType or the psn is needed. Ideally all three.");
            return responseService.badRequest(responseContentType);
        }

        // Evaluate success
        if (deletionSuccessful) {
            // Successfully deleted a record, return a 204-NO_CONTENT
            log.debug("Successfully deleted the pseudonym-record.");
            return responseService.noContent(responseContentType);
        } else {
            // Deletion was unsuccessful, return a 422 unprocessable entity
            log.debug("The pseudonym-record could not be deleted.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }
    
    /**
     * Method to search and link pseudonyms along the pseudonym-chain in the tree.
     * 
     * @param sourceDomain the starting domain for the search
     * @param targetDomain the target domain for the search
     * @param sourceIdentifier (optional) the identifier of the record to start the search from
     * @param sourceIdType (optional) the idType of the record to start the search from
     * @param sourcePsn (optional) the pseudonym of the record to start the search from
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>list of linked pseudonyms</b> 
     * 				when linkable pseudonyms were found</li>
     * 			<li>a <b>403-FORBIDDEN</b> when the requester does not have 
     * 				the rights to access the source and/or target domain</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no linkable pseudonyms were found for the
     * 				given parameters</li>
     */
    @GetMapping(value = "/domains/linked-pseudonyms", params = {"sourceDomain", "targetDomain"})
    @PreAuthorize("hasRole('record-read') and hasRole('link-pseudonyms')")
    // Since the domains are given via query-parameters and not via path-parameters, 
    // the rights to access those are determined inside the method.
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getLinkedRecords(@RequestParam(name = "sourceDomain", required = true) String sourceDomain,
    										  @RequestParam(name = "targetDomain", required = true) String targetDomain,
    										  @RequestParam(name = "sourceIdentifier", required = false) String sourceIdentifier,
    										  @RequestParam(name = "sourceIdType", required = false) String sourceIdType,
    										  @RequestParam(name = "sourcePsn", required = false) String sourcePsn,
    										  @RequestHeader(name = "accept", required = false) String responseContentType,
    										  HttpServletRequest request) {

        // Check if the requester is allowed to access the given domains.
        if (!authorizationService.hasDomainRoleRelationship(sourceDomain, "record-read") ||
                !authorizationService.hasDomainRoleRelationship(sourceDomain, "link-pseudonyms") ||
                !authorizationService.hasDomainRoleRelationship(targetDomain, "record-read") ||
                !authorizationService.hasDomainRoleRelationship(targetDomain, "link-pseudonyms")) {
            log.debug("The requester tried to access domains without the necessary access rights.");
            return responseService.forbidden(responseContentType);
        }
        
        List<Pair<PseudonymDTO, PseudonymDTO>> pseudonyms = domainDBAccessService.getLinkedPseudonyms(sourceDomain,
                Assertion.isNotNullOrEmpty(sourceIdentifier) ? sourceIdentifier : null,
                Assertion.isNotNullOrEmpty(sourceIdType) ? sourceIdType : null,
                Assertion.isNotNullOrEmpty(sourcePsn) ? sourcePsn : null,
                targetDomain,
                request);
        
        // Check if anything was found
        if (pseudonyms == null || pseudonyms.size() == 0) {
        	log.debug("No linkable pseudonyms were found.");
        	return responseService.notFound(responseContentType);
        }

        // Convert the pair list
        boolean completeView = authorizationService.currentRequestHasRole("complete-view");

        List<List<PseudonymDTO>> listOfRecordPairs = new ArrayList<>();
        for (Pair<PseudonymDTO, PseudonymDTO> pair : pseudonyms) {
            List<PseudonymDTO> recordPair = new ArrayList<>();
            
            if (completeView) {
            	recordPair.add(pair.getFirst().toReducedStandardView());
            	recordPair.add(pair.getSecond().toReducedStandardView());
            } else {
            	recordPair.add(pair.getFirst());
            	recordPair.add(pair.getSecond());
            }

            listOfRecordPairs.add(recordPair);
        }

        log.debug("Successfully linked pseudonyms.");
        return responseService.ok(responseContentType, listOfRecordPairs);
    }

    /**
     * This method retrieves all pseudonym-records stored in the given domain.
     *
     * @param domainName (required) the name of the domain the pseudonyms are in
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return	<li>a <b>200-OK</b> status and the <b>list of records</b>
     * 				when successful</li>
     * 			<li>a <b>404-NOT_FOUND</b> when the given domain wasn't
     * 				found</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the
     * 				records could not be retrieved</li>
     */
    @GetMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read-batch')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getRecordBatch(@PathVariable("domain") String domainName,
                                            @RequestHeader(name = "accept", required = false) String responseContentType,
                                            HttpServletRequest request) {
        // Check that the batch size isn't too big.
        Integer count = domainDBAccessService.getAmountOfRecordsInDomain(domainName, null);
        if (count == null) {
            // An error during accessing the domain occurred. Maybe the domain is not in the database.
            log.error("Couldn't count the number of records in the domain \"" + domainName + "\".");
            return responseService.unprocessableEntity(responseContentType);
        } else if (count == DomainDBAccessService.DOMAIN_NOT_FOUND) {
            // The domain wasn't found. Return an error 404-NOT_FOUND
            log.error("Couldn't find the domain \"" + domainName + "\".");
            return responseService.notFound(responseContentType);
        } else if (count > DEFAULT_PSEUDONYM_BATCH_LENGTH) {
            // The batch size exceeded the limit. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("The domain contains too many entries. The maximum allowed batch size is: " + DEFAULT_PSEUDONYM_BATCH_LENGTH);
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the records belong to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);
        if (d == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain where the records should be searched couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Retrieve the pseudonym-records
        List<PseudonymDTO> records = domainDBAccessService.getAllRecordsInDomain(domainName, request);

        // Check if anything was found
        if (records == null) {
            // Something went wrong
            log.error("Retrieving the records in the domain \"" + domainName + "\" failed.");
            return responseService.unprocessableEntity(responseContentType);
        } else if (records.size() == 0) {
            // Nothing was found. Return a 200-OK status.
            log.debug("No records were found.");
            return responseService.ok(responseContentType, Collections.emptyList());
        }

        // Transform result into the desired output format
        List<PseudonymDTO> resultAsJson = new ArrayList<>();
        List<String> resultAsString = new ArrayList<>();
        for (PseudonymDTO p : records) {
        	// Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                p = p.toReducedStandardView();
            }

            // Process the DTO depending on the response`s media type
            if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
                resultAsString.add(p.toRepresentationString());
            } else {
                resultAsJson.add(p);
            }
        }

        // Return result
        log.debug("Successfully retrieved all records from domain \"" + domainName + "\".");

        if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
            return responseService.ok(responseContentType, resultAsString);
        } else {
            return responseService.ok(responseContentType, resultAsJson);
        }
    }

    /**
     * This method retrieves a pseudonym-record through its identifier (id & idType).
     *
     * @param domainName (required) the name of the domain the record is in
     * @param identifier (required) the identifier to search for
     * @param idType (required) the type of the identifier
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>record</b> when it was
     * 				found</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no record was found for the
     * 				given identifier and idType</li>
     */
    @GetMapping(value = "/domains/{domain}/pseudonym", params = {"id", "idType"})
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getRecordByIdentifier(@PathVariable("domain") String domainName,
                                                   @RequestParam(name = "id", required = true) String identifier,
                                                   @RequestParam(name = "idType", required = true) String idType,
                                                   @RequestHeader(name = "accept", required = false) String responseContentType,
                                                   HttpServletRequest request) {
        // Retrieve the domain the record belongs to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        if (d == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain where the record should be searched couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Retrieve the pseudonym-record
        IdentifierItem idItem = IdentifierItem.builder().identifier(identifier).idType(idType).build();
        List<PseudonymDTO> p = pseudonymDBAccessService.getPseudonym(domainName, idItem, null, request);
        if (p != null && !p.isEmpty()) {
            // Successfully retrieved record(s), return it to the user as well as a 200-OK
        	PseudonymDTO pseudonymDTO = null;
		
	        // Determine whether or not a reduced standard view or a complete view is requested
	        if (!authorizationService.currentRequestHasRole("complete-view")) {
	        	pseudonymDTO = p.getFirst().toReducedStandardView();
	        } else {
	            pseudonymDTO = p.getFirst();
	        }

            log.debug("Successfully retrieved the requested pseudonym-record.");
            return responseService.ok(responseContentType, pseudonymDTO);
        } else {
            // Nothing found, return a 404-NOT_FOUND
            return responseService.notFound(responseContentType);
        }
    }

    /**
     * This method retrieves a pseudonym-record through its pseudonym (psn).
     *
     * @param domainName (required) the name of the domain the record is in
     * @param psn (required) the pseudonym to search for
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>record</b> when it was
     * 				found</li>
     * 			<li>a <b>404-NOT_FOUND</b> when no record was found for the
     * 				given pseudonym</li>
     */
    @GetMapping(value = "/domains/{domain}/pseudonym", params = "psn")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getRecordByPseudonym(@PathVariable("domain") String domainName,
                                                  @RequestParam(name = "psn", required = true) String psn,
                                                  @RequestHeader(name = "accept", required = false) String responseContentType,
                                                  HttpServletRequest request) {
        // Retrieve the domain the record belongs to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        if (d == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain where the record should be searched couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Retrieve the pseudonym-record
        List<PseudonymDTO> p = pseudonymDBAccessService.getPseudonym(domainName, null, psn, request);
        if (p != null && !p.isEmpty()) {
            // Successfully retrieved record(s), return it to the user as well as a 200-OK
        	PseudonymDTO pseudonymDTO = null;
		
	        // Determine whether or not a reduced standard view or a complete view is requested
	        if (!authorizationService.currentRequestHasRole("complete-view")) {
	            pseudonymDTO = p.getFirst().toReducedStandardView();
	        } else {
	            pseudonymDTO = p.getFirst();
	        }

            log.debug("Successfully retrieved the requested pseudonym-record.");
            return responseService.ok(responseContentType, pseudonymDTO);
        } else {
            // Nothing found, return a 404-NOT_FOUND
            return responseService.notFound(responseContentType);
        }
    }
    
    /**
     * Method that encapsulates the pseudonym generation.
     * 
     * @param identifier the identifier that should be pseudonymized 
     * @param idType the identifier's type
     * @param domain the domain in which the identifier should be pseudonymized
     * @param omitPrefix determines whether or not the prefix should be added to the pseudonym
     * @return the generated pseudonym
     */
    private String pseudonymize(String identifier, String idType, Domain domain, Boolean omitPrefix) {
            // Generate a new pseudonym
            String prefix = (omitPrefix != null && omitPrefix) ? "" : domain.getPrefix();
            
            Pseudonymizer pseudonymizer = new PseudonymizationFactory().getPseudonymizer(domain);
            String pseudonym = pseudonymizer.pseudonymize(identifier + idType + domain.getSalt(), prefix);
            return domain.getAddcheckdigit() ? pseudonymizer.addCheckDigit(pseudonym, domain.getLengthincludescheckdigit(), domain.getName(), prefix) : pseudonym;
    }

    /**
     * This method updates a list of pseudonym-records in a batch.
     * Every list entry must contain all necessary information (identifier, idType,
     * pseudonym, validFrom, validFromInherited, validTo, validToInherited).
     *
     * @param domainName (required) the name of the domain the pseudonyms are in
     * @param recordDtoList (required) the list of necessary information, formatted as a JSON to match the recordDto
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status on success</li>
     * 			<li>a <b>404-NOT_FOUND</b> status when the domain couldn't
     * 				be found</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the update of the
     *	 			record failed</li>
     */
    @PutMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-update-batch')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> updateRecordBatch(@PathVariable("domain") String domainName,
                                               @RequestBody List<PseudonymDTO> recordDtoList,
                                               @RequestHeader(name = "accept", required = false) String responseContentType,
                                               HttpServletRequest request) {
        int ignored = 0;
        List<PseudonymDTO> updateablePseudonyms = new ArrayList<>();

        // Retrieve the domain the records belong to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        if (d == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain to which the batch update is scoped to couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Iterate over the recordDtos
        for (int i = 0; i < recordDtoList.size(); i++) {
            PseudonymDTO r = recordDtoList.get(i);

            // Check if any of the attributes of a record is missing
            if (!Assertion.assertNotNullAll(r.getIdentifierItem().getIdentifier(), r.getIdentifierItem().getIdType(), r.getPsn(), r.getValidFrom(), r.getValidFromInherited(), r.getValidTo(), r.getValidToInherited())) {
                // Everything that is needed is available, add domain info
                r.setDomainName(d.getName());
            } else {
                // Missing attribute values: log and ignore
                log.debug("The record with number " + (i + 1) + " is missing some attributes. This part of the batch request is ignored.");
                ignored++;

                // Early "break"
                continue;
            }

            // Add pseudonym object to a list of updatable objects
            updateablePseudonyms.add(r);

        } // End for loop

        // Update records
        List<Boolean> updateSuccess = pseudonymDBAccessService.updatePseudonymBatch(updateablePseudonyms, d.getId(), request);
        if (updateSuccess != null && !updateSuccess.isEmpty()) {
        	// Success. Return a status code 200-OK and a list of the updated pseudonyms.
        	List<PseudonymDTO> updatedPseudonyms = new ArrayList<>();
        	for (int i = 0; i < updateablePseudonyms.size(); i++) {
        		if (updateSuccess.get(i) != null && updateSuccess.get(i)) {
        			PseudonymDTO updatedPseudonym = pseudonymDBAccessService.getPseudonymFromIdentifier(domainName, updateablePseudonyms.get(i).getIdentifierItem(), null).getFirst();
        			updatedPseudonyms.add(authorizationService.currentRequestHasRole("complete-view") ? updatedPseudonym : updatedPseudonym.toReducedStandardView());
        		}
        	}
        	
            log.debug("Successfully updated the batch of " + recordDtoList.size() + " pseudonym-records, from which " + ignored + (ignored == 1 ? " was" : " were") + " ignored.");
            return responseService.ok(responseContentType, updatedPseudonyms);
        } else {
            // Update failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("The requested update of the batch of pseudonym-records failed.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method updates a pseudonym-record identified by its id and idType.
     *
     * @param oldDomainName (required) the name of the domain the record should be in
     * @param pseudonymDTO (required) the record object
     * @param identifier (required) the identifier of the record that should be updated
     * @param idType (required) the type of the identifier
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>pseudonym</b> of the
     * 				updated record on success</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when neither an identifier, nor
     * 				a pseudonym were given</li>
     * 			<li>a <b>404-NOT_FOUND</b> status when either the domain,
     * 				the record that should be updated, or the new domain
     * 				couldn't be found
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the update of the
     * 				record failed</li>
     */
    @PutMapping(value = "/domains/{domain}/pseudonym/complete", params = {"id", "idType"})
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #oldDomainName, 'record-update-complete')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> updateRecordCompleteByIdentifier(@PathVariable("domain") String oldDomainName,
                                                              @RequestBody PseudonymDTO pseudonymDTO,
                                                              @RequestParam(name = "id", required = true) String identifier,
                                                              @RequestParam(name = "idType", required = true) String idType,
                                                              @RequestHeader(name = "accept", required = false) String responseContentType,
                                                              HttpServletRequest request) {
        String newIdentifier = pseudonymDTO.getIdentifierItem().getIdentifier();
        String newIdType = pseudonymDTO.getIdentifierItem().getIdType();
        String newPsn = pseudonymDTO.getPsn();
        Timestamp validFrom = pseudonymDTO.getValidFrom() != null ? Timestamp.valueOf(pseudonymDTO.getValidFrom()) : null;
        Timestamp validTo = pseudonymDTO.getValidTo() != null ? Timestamp.valueOf(pseudonymDTO.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(pseudonymDTO.getValidityTime());
        String newDomainName = pseudonymDTO.getDomainName();

        if (Assertion.assertNullAll(newIdentifier, newIdType, newPsn, validFrom, validTo, validityTime, newDomainName)) {
            // An empty object was passed, so there is nothing to create.
            log.debug("The record DTO passed by the user was empty. Nothing to update.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the record belongs to
        Domain domain = domainDBAccessService.getDomainByName(oldDomainName, null);

        // Try to retrieve the updated domain (is null, when no new domain was given)
        Domain newDomain = domainDBAccessService.getDomainByName(newDomainName, null);

        // Retrieve the old record
        IdentifierItem idItem = IdentifierItem.builder().identifier(identifier).idType(idType).build();
        List<PseudonymDTO> oldPseudonymList = pseudonymDBAccessService.getPseudonym(oldDomainName, idItem, null, null);
        if (oldPseudonymList == null || oldPseudonymList.size() == 0) {
        	// Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldPseudonymList.size() > 1) {
        	// Found too many records.Return a 422-UNPROCESSABLE_ENTITY
        	log.debug("The update for the given identifier and idType would have affected more than one record. "
        			+ "Updating multiple records at once is only permitted via the batch-endpoint.");
        	return responseService.unprocessableEntity(responseContentType);
        }
        
        PseudonymDTO oldPseudonymRecord = oldPseudonymList.get(0);

        // Check if the retrieved values are null
        if (domain == null) {
            // Couldn't find the domain. Return a 404-NOT_FOUND
            log.debug("The provided domain (\"" + oldDomainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldPseudonymRecord == null) {
        	// Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (newDomainName != null && newDomain == null) {
            // A new domain name was provided but it couldn't be found. Return a 404-NOT_FOUND
            log.debug("The new domain for the pseudonym-record (\"" + newDomainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Check if the requester has the permission for the newDomain if given
        if (newDomain != null && !authorizationService.currentRequestHasRole(newDomain.getName())) {
            log.error("The user requested to change the domain of a pseudonym-record to a domain the user has no rights for.");
            return responseService.forbidden(responseContentType);
        }

        // Determine what domain to use
        Domain d = (newDomain != null) ? newDomain : domain;

        // Create new pseudonym-record
        PseudonymDTO newPseudonymRecord = new PseudonymDTO();
        newPseudonymRecord.setIdentifierItem(IdentifierItem.builder().identifier(newIdentifier).idType(newIdType).build());
        newPseudonymRecord.setPsn(newPsn);
        newPseudonymRecord.setDomainName(d.getName());

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newPseudonymRecord.setValidFrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newPseudonymRecord.setValidFromInherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newPseudonymRecord.setValidFrom(validFrom.toLocalDateTime());
                newPseudonymRecord.setValidFromInherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newPseudonymRecord.setValidFrom(oldPseudonymRecord.getValidFrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newPseudonymRecord.setValidTo(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newPseudonymRecord.setValidToInherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newPseudonymRecord.setValidTo(validTo.toLocalDateTime());
                newPseudonymRecord.setValidToInherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newPseudonymRecord.getValidFrom().plusSeconds(validityTime) : d.getValidto());
                newPseudonymRecord.setValidToInherited(!newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime));
                newPseudonymRecord.setValidToInherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldPseudonymRecord, newPseudonymRecord, d.getId(), request)) {
            // Update successful. Retrieve the updated record to show it to the user.
            String id = (newIdentifier != null && !newIdentifier.trim().equals("")) ? newIdentifier : identifier;
            String idT = (newIdType != null && !newIdType.trim().equals("")) ? newIdType : idType;
            IdentifierItem ii = IdentifierItem.builder().identifier(id).idType(idT).build();
            String p = (newPsn != null && !newPsn.trim().equals("")) ? newPsn : oldPseudonymRecord.getPsn();
           
            PseudonymDTO updatedRecord = pseudonymDBAccessService.getPseudonym(d.getName(), ii, p, null).get(0);

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
            	updatedRecord.toReducedStandardView();
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, updatedRecord);
        } else {
            // Update failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("The requested update of a pseudonym-record failed.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method updates a pseudonym-record identified by its pseudonym.
     *
     * @param oldDomainName (required) the name of the domain the record should be in
     * @param pseudonymDTO (required) the record object
     * @param psn (required) the pseudonym of the record that should be updated
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>pseudonym</b> of the
     * 				updated record on success</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when neither an identifier, nor
     * 				a pseudonym were given</li>
     * 			<li>a <b>404-NOT_FOUND</b> status when either the domain,
     * 				the record that should be updated, or the new domain
     * 				couldn't be found
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the update of the
     * 				record failed</li>
     */
    @PutMapping(value = "/domains/{domain}/pseudonym/complete", params = "psn")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #oldDomainName, 'record-update-complete')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> updateRecordCompleteByPseudonym(@PathVariable("domain") String oldDomainName,
                                                             @RequestBody PseudonymDTO pseudonymDTO,
                                                             @RequestParam(name = "psn", required = true) String psn,
                                                             @RequestHeader(name = "accept", required = false) String responseContentType,
                                                             HttpServletRequest request) {
        String newIdentifier = pseudonymDTO.getIdentifierItem().getIdentifier();
        String newIdType = pseudonymDTO.getIdentifierItem().getIdType();
        String newPsn = pseudonymDTO.getPsn();
        Timestamp validFrom = pseudonymDTO.getValidFrom() != null ? Timestamp.valueOf(pseudonymDTO.getValidFrom()) : null;
        Timestamp validTo = pseudonymDTO.getValidTo() != null ? Timestamp.valueOf(pseudonymDTO.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(pseudonymDTO.getValidityTime());
        String newDomainName = pseudonymDTO.getDomainName();

        if (Assertion.assertNullAll(newIdentifier, newIdType, newPsn, validFrom, validTo, validityTime, newDomainName)) {
            // An empty object was passed, so there is nothing to create.
            log.debug("The record DTO passed by the user was empty. Nothing to create.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the record belongs to
        Domain domain = domainDBAccessService.getDomainByName(oldDomainName, null);

        // Try to retrieve the updated domain (is null, when no new domain was given)
        Domain newDomain = domainDBAccessService.getDomainByName(newDomainName, null);

        // Retrieve the old record
        List<PseudonymDTO> oldRecordList = pseudonymDBAccessService.getPseudonym(oldDomainName, null, psn, null);
        if (oldRecordList == null || oldRecordList.size() == 0) {
        	// Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldRecordList.size() > 1) {
        	// Found too many records.Return a 422-UNPROCESSABLE_ENTITY
        	log.debug("The update for the given identifier and idType would have affected more than one record. "
        			+ "Updating multiple records at once is only permitted via the batch-endpoint.");
        	return responseService.unprocessableEntity(responseContentType);
        }
        
        PseudonymDTO oldRecord = oldRecordList.get(0);

        // Check if the retrieved values are null
        if (domain == null) {
            // Couldn't find the domain. Return a 404-NOT_FOUND
            log.debug("The provided domain (\"" + oldDomainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldRecord == null) {
        	// Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (newDomainName != null && newDomain == null) {
            // A new domain name was provided but it couldn't be found. Return a 404-NOT_FOUND
            log.debug("The new domain for the pseudonym-record (\"" + newDomainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Check if the requester has the permission for the newDomain if given
        if (newDomain != null && !authorizationService.currentRequestHasRole(newDomain.getName())) {
            log.error("The user requested to change the domain of a pseudonym-record to a domain the user has no rights for.");
            return responseService.forbidden(responseContentType);
        }

        // Determine what domain to use
        Domain d = (newDomain != null) ? newDomain : domain;

        // Create new pseudonym-record
        PseudonymDTO newPseudonymRecord = new PseudonymDTO();
        newPseudonymRecord.setIdentifierItem(IdentifierItem.builder().identifier(newIdentifier).idType(newIdType).build());
        newPseudonymRecord.setPsn(newPsn);
        newPseudonymRecord.setDomainName(d.getName());

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newPseudonymRecord.setValidFrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newPseudonymRecord.setValidFromInherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newPseudonymRecord.setValidFrom(validFrom.toLocalDateTime());
                newPseudonymRecord.setValidFromInherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newPseudonymRecord.setValidFrom(oldRecord.getValidFrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newPseudonymRecord.setValidTo(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newPseudonymRecord.setValidToInherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newPseudonymRecord.setValidTo(validTo.toLocalDateTime());
                newPseudonymRecord.setValidToInherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newPseudonymRecord.getValidFrom().plusSeconds(validityTime) : d.getValidto());
                newPseudonymRecord.setValidToInherited(!newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime));
                newPseudonymRecord.setValidToInherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldRecord, newPseudonymRecord, d.getId(), request)) {
            // Update successful. Retrieve the updated record to show it to the user.
            String p = (newPsn != null && !newPsn.trim().equals("")) ? newPsn : psn;
            PseudonymDTO updatedRecord = pseudonymDBAccessService.getPseudonym(d.getName(), null, p, null).get(0);

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                updatedRecord.toReducedStandardView();
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, updatedRecord);
        } else {
            // Update failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("The requested update of a pseudonym-record failed.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method updates a pseudonym-record with a reduced set of updatable attributes.
     * The record is identified by its id and idType.
     *
     * @param domainName (required) the name of the domain the record should be in
     * @param pseudonymDTO (required) the record object
     * @param identifier (required) the identifier that should be updated
     * @param idType (required) the type of the identifier
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>pseudonym</b> on
     * 				success</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when neither an identifier, nor
     * 				a pseudonym were given</li>
     * 			<li>a <b>404-NOT_FOUND</b> status when either the domain or
     * 				the record that should be updated couldn't be found
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the update of the
     * 				record failed</li>
     */
    @PutMapping(value = "/domains/{domain}/pseudonym", params = {"id", "idType"})
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-update')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> updateRecordByIdentifier(@PathVariable("domain") String domainName,
                                                      @RequestBody PseudonymDTO pseudonymDTO,
                                                      @RequestParam(name = "id", required = true) String identifier,
                                                      @RequestParam(name = "idType", required = true) String idType,
                                                      @RequestHeader(name = "accept", required = false) String responseContentType,
                                                      HttpServletRequest request) {
        Timestamp validFrom = pseudonymDTO.getValidFrom() != null ? Timestamp.valueOf(pseudonymDTO.getValidFrom()) : null;
        Timestamp validTo = pseudonymDTO.getValidTo() != null ? Timestamp.valueOf(pseudonymDTO.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(pseudonymDTO.getValidityTime());

        if (Assertion.assertNullAll(validFrom, validTo, validityTime)) {
            // An empty object was passed, so there is nothing to update.
            log.debug("The record DTO passed by the user was empty. Nothing to update.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the record belongs to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        // Retrieve the old record
        IdentifierItem idItem = IdentifierItem.builder().identifier(identifier).idType(idType).build();
        List<PseudonymDTO> oldRecordList = pseudonymDBAccessService.getPseudonym(domainName, idItem, null, null);
        if (oldRecordList == null || oldRecordList.size() == 0) {
        	// Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldRecordList.size() > 1) {
        	// Found too many records.Return a 422-UNPROCESSABLE_ENTITY
        	log.debug("The update for the given identifier and idType would have affected more than one record. "
        			+ "Updating multiple records at once is only permitted via the batch-endpoint.");
        	return responseService.unprocessableEntity(responseContentType);
        }
        
        PseudonymDTO oldPseudonymRecord = oldRecordList.get(0);

        // Check if the retrieved values are null
        if (d == null) {
            // Couldn't find the domain. Return a 404-NOT_FOUND
            log.debug("The provided domain (\"" + domainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldPseudonymRecord == null) {
            // Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Create new pseudonym-record
        PseudonymDTO newPseudonymRecord = new PseudonymDTO();

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newPseudonymRecord.setValidFrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newPseudonymRecord.setValidFromInherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newPseudonymRecord.setValidFrom(validFrom.toLocalDateTime());
                newPseudonymRecord.setValidFromInherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newPseudonymRecord.setValidFrom(oldPseudonymRecord.getValidFrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newPseudonymRecord.setValidTo(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newPseudonymRecord.setValidToInherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newPseudonymRecord.setValidTo(validTo.toLocalDateTime());
                newPseudonymRecord.setValidToInherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newPseudonymRecord.getValidFrom().plusSeconds(validityTime) : d.getValidto());
                newPseudonymRecord.setValidToInherited(!newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime));
                newPseudonymRecord.setValidToInherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldPseudonymRecord, newPseudonymRecord, d.getId(), request)) {
            // Update successful, retrieve the updated record to show it to the user
            PseudonymDTO updatedRecord = pseudonymDBAccessService.getPseudonym(d.getName(), idItem, oldPseudonymRecord.getPsn(), null).get(0);

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                updatedRecord.toReducedStandardView();
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, updatedRecord);
        } else {
            // Update failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("The requested update of a pseudonym-record failed.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }

    /**
     * This method updates a pseudonym-record with a reduced set of updatable attributes.
     * The record is identified by its psn.
     *
     * @param domainName (required) the name of the domain the record should be in
     * @param pseudonymDTO (required) the record object
     * @param psn (optional) the pseudonym of the record that should be updated
     * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return 	<li>a <b>200-OK</b> status and the <b>pseudonym</b> on
     * 				success</li>
     * 			<li>a <b>400-BAD_REQUEST</b> when neither an identifier, nor
     * 				a pseudonym were given</li>
     * 			<li>a <b>404-NOT_FOUND</b> status when either the domain or
     * 				the record that should be updated couldn't be found
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> when the update of the
     * 				record failed</li>
     */
    @PutMapping(value = "/domains/{domain}/pseudonym", params = "psn")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-update')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> updateRecordByPseudonym(@PathVariable("domain") String domainName,
                                                     @RequestBody PseudonymDTO pseudonymDTO,
                                                     @RequestParam(name = "psn", required = true) String psn,
                                                     @RequestHeader(name = "accept", required = false) String responseContentType,
                                                     HttpServletRequest request) {
        Timestamp validFrom = pseudonymDTO.getValidFrom() != null ? Timestamp.valueOf(pseudonymDTO.getValidFrom()) : null;
        Timestamp validTo = pseudonymDTO.getValidTo() != null ? Timestamp.valueOf(pseudonymDTO.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(pseudonymDTO.getValidityTime());

        if (Assertion.assertNullAll(validFrom, validTo, validityTime)) {
            // An empty object was passed, so there is nothing to update.
            log.debug("The record DTO passed by the user was empty. Nothing to update.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the record belongs to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        // Retrieve the old record
        List<PseudonymDTO> oldRecordList = pseudonymDBAccessService.getPseudonym(domainName, null, psn, null);
        if (oldRecordList == null || oldRecordList.size() == 0) {
        	// Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldRecordList.size() > 1) {
        	// Found too many records.Return a 422-UNPROCESSABLE_ENTITY
        	log.debug("The update for the given identifier and idType would have affected more than one record. "
        			+ "Updating multiple records at once is only permitted via the batch-endpoint.");
        	return responseService.unprocessableEntity(responseContentType);
        }
        
        PseudonymDTO oldPseudonymRecord = oldRecordList.get(0);

        // Check if the retrieved values are null
        if (d == null) {
            // Couldn't find the domain. Return a 404-NOT_FOUND
            log.debug("The provided domain (\"" + domainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldPseudonymRecord == null) {
            // Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Create new pseudonym-record
        PseudonymDTO newPseudonymRecord = new PseudonymDTO();

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newPseudonymRecord.setValidFrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newPseudonymRecord.setValidFromInherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newPseudonymRecord.setValidFrom(validFrom.toLocalDateTime());
                newPseudonymRecord.setValidFromInherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newPseudonymRecord.setValidFrom(oldPseudonymRecord.getValidFrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newPseudonymRecord.setValidTo(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newPseudonymRecord.setValidToInherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newPseudonymRecord.setValidTo(validTo.toLocalDateTime());
                newPseudonymRecord.setValidToInherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newPseudonymRecord.getValidFrom().plusSeconds(validityTime) : d.getValidto());
                newPseudonymRecord.setValidToInherited(!newPseudonymRecord.getValidFrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newPseudonymRecord.setValidTo(newPseudonymRecord.getValidFrom().plusSeconds(validityTime));
                newPseudonymRecord.setValidToInherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldPseudonymRecord, newPseudonymRecord, d.getId(), request)) {
            // Update successful. Retrieve the updated record to show it to the user.
            PseudonymDTO updatedRecord = pseudonymDBAccessService.getPseudonym(d.getName(), null, psn, null).get(0);

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                updatedRecord.toReducedStandardView();
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, updatedRecord);
        } else {
            // Update failed. Return an error 422-UNPROCESSABLE_ENTITY.
            log.error("The requested update of a pseudonym-record failed.");
            return responseService.unprocessableEntity(responseContentType);
        }
    }
    
    /**
     * This method provides an endpoint to validate pseudonyms.
     *
     * @return 	<li>a <b>200-OK</b> status and the result of the validity-
     * 				check for the given pseudonym</li>
     * 			<li>a <b>404-NOT_FOUND</b> status when either the domain the
     * 				pseudonym is on or the record that should be validated 
     * 				couldn't be found</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> status if there is 
     * 				no check digit according to the domain</li>
     */
    @GetMapping("/domains/{domain}/pseudonym/validation")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> validatePseudonym(@PathVariable("domain") String domainName,
								               @RequestParam(name = "psn", required = true) String psn,
								               @RequestHeader(name = "accept", required = false) String responseContentType,
								               HttpServletRequest request) {
    	// Retrieve domain
    	Domain d = domainDBAccessService.getDomainByName(domainName, null);
    	
    	if (d == null) {
    		log.debug("Could not find the domain in which the validation should be performed.");
    		return responseService.notFound(responseContentType);
    	}
    	
    	if (!d.getAddcheckdigit()) {
    		// There is no check digit to validate
    		log.debug("The domain is configured so that there is no check digit added. Therefore, no validation can be performed.");
    		return responseService.unprocessableEntity(responseContentType);
    	}
    	
    	// Decide the character space for the check digit validation depending on the used pseudonymization algorithm
		LuhnCheckDigit luhn;
		
		switch (d.getAlgorithm().toUpperCase()) {
	        case "CONSECUTIVE":
	        case "RANDOM":
	        case "RANDOM_NUM": {
	        	// Used alphabet: "0123456789"
	        	luhn = new LuhnMod10CheckDigit();
	        	break;
	        }
			case "MD5":
	        case "SHA1":
	        case "SHA2": 
	        case "SHA3":
	        case "BLAKE3":
	        case "XXHASH": {
	        	// Used alphabet: "0123456789ABCDEF"
	        	luhn = new LuhnMod16CheckDigit();
	        	break;
	        }
	        case "RANDOM_LET": {
	        	// Used alphabet: "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
	        	luhn = new LuhnMod26CheckDigit();
	        	break;
	        }
	        case "RANDOM_SYM_BIOS": {
	        	// Used alphabet: "ACDEFGHJKLMNPQRTUVWXYZ0123456789"
	        	luhn = new LuhnMod32CheckDigit();
	        	break;
	        }
	        case "RANDOM_SYM": {
	        	// Used alphabet: "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	        	luhn = new LuhnMod36CheckDigit();
	        	break;
	        }
			default:
				throw new IllegalArgumentException("Unexpected algorithm: " + d.getAlgorithm());
		}
    	
		// Perform validation
		Boolean valid = luhn.validateCheckDigit(psn, d.getPrefix());
    	
    	if (valid == null) {
    		log.debug("The validation was aborted since a character that is not part of the allowed alphabet was encountered.");
    		return responseService.badRequest(responseContentType);
    	}
    	
        return responseService.ok(responseContentType, valid.toString());
    }
}

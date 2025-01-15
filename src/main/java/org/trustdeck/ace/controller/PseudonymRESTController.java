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

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trustdeck.ace.algorithms.LuhnCheckDigit;
import org.trustdeck.ace.algorithms.LuhnMod10CheckDigit;
import org.trustdeck.ace.algorithms.LuhnMod16CheckDigit;
import org.trustdeck.ace.algorithms.LuhnMod26CheckDigit;
import org.trustdeck.ace.algorithms.LuhnMod32CheckDigit;
import org.trustdeck.ace.algorithms.LuhnMod36CheckDigit;
import org.trustdeck.ace.algorithms.PseudonymizationFactory;
import org.trustdeck.ace.algorithms.Pseudonymizer;
import org.trustdeck.ace.algorithms.RandomNumberPseudonymizer;
import org.trustdeck.ace.jooq.generated.tables.pojos.Domain;
import org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym;
import org.trustdeck.ace.model.dto.RecordDto;
import org.trustdeck.ace.security.audittrail.annotation.Audit;
import org.trustdeck.ace.security.audittrail.event.AuditEventType;
import org.trustdeck.ace.security.audittrail.usertype.AuditUserType;
import org.trustdeck.ace.service.AuthorizationService;
import org.trustdeck.ace.service.DomainDBAccessService;
import org.trustdeck.ace.service.PseudonymDBAccessService;
import org.trustdeck.ace.service.ResponseService;
import org.trustdeck.ace.utils.Assertion;
import org.trustdeck.ace.utils.Utility;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    @Operation(
            summary = "Create records in batch",
            description = "Creates new pseudonym records in the specified domain in batch. Allows passing pre-created pseudonyms or generates new ones.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-create-batch", "#domainName"})
            },
            requestBody = @RequestBody(
                    description = "List of pseudonym records to be created",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = RecordDto.class))
                    )
            ),
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Domain name where the records will be created",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "omitPrefix",
                            description = "Whether to omit the prefix for pseudonyms",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "boolean", example = "true")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Records created successfully",
                            content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = RecordDto.class))
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain not found"
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity or invalid batch size"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error during pseudonym generation"
                    )
            }
    )
    @PostMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-create-batch')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL, message = "Wants to create a batch of new records.")
    public ResponseEntity<?> createRecordBatch(@PathVariable("domain") String domainName,
                                               @RequestParam(name = "omitPrefix", required = false, defaultValue = "false") Boolean omitPrefix,
                                               @RequestBody List<RecordDto> recordDtoList,
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
        List<Pseudonym> pseudonyms = new ArrayList<>();
        for (RecordDto recordDto : recordDtoList) {
            // Start creating the record
            Pseudonym p = new Pseudonym();
            p.setIdentifier(recordDto.getId());
            p.setIdtype(recordDto.getIdType());
            p.setDomainid(domain.getId());

            // Pseudonymize the identifier and store it in the object
            String pseudonym = null;
            if (recordDto.getPsn() != null && !recordDto.getPsn().trim().equals("")) {
                // A pseudonym was already given --> store it instead of creating a new one if it's in the correct format
                String psn = recordDto.getPsn().trim();
                if (omitPrefix != null && omitPrefix) {
                    pseudonym = psn;
                } else {
                    pseudonym = psn.startsWith(domain.getPrefix()) ? psn : domain.getPrefix() + psn;
                }
            } else {
                // Generate a new pseudonym
                String prefix = (omitPrefix != null && omitPrefix) ? "" : domain.getPrefix(); // Omitting the prefix here shouldn't be the norm
                Pseudonymizer pseudonymizer = new PseudonymizationFactory().getPseudonymizer(domain);
                pseudonym = pseudonymizer.pseudonymize(recordDto.getId() + recordDto.getIdType() + domain.getSalt(), prefix);
                pseudonym = domain.getAddcheckdigit() ? pseudonymizer.addCheckDigit(pseudonym, domain.getLengthincludescheckdigit(), domain.getName(), prefix) : pseudonym;

                if (domain.getAlgorithm().toUpperCase().startsWith("RANDOM") && pseudonym.equals(RandomNumberPseudonymizer.DOMAIN_FULL)) {
                	// Pseudonymization failed due to too many collisions. The domain reached it's filling point (~49%) 
                	// at which the probability to generate a previously unseen pseudonym in 25 tries is no longer greater
                	// than 99.999998%.
                	log.warn("Couldn't generate a new pseudonym due to too many pseudonyms being already in the database. ");
                	return responseService.insufficientStorage(responseContentType);
                } else if (pseudonym == null && domain.getAlgorithm().toUpperCase().startsWith("RANDOM")) {
                	// Pseudonymization failed: probably no non-colliding pseudonym was found.
                	log.warn("Pseudonymization failed for identifier \"" + recordDto.getId() + "\" and idType \"" + recordDto.getIdType() + "\". "
                			+ "Probably due to collisions with other pseudonyms. Try a greater pseudonym-length.");
                	return responseService.unprocessableEntity(responseContentType);
            	} else if (pseudonym == null) {
                    // Pseudonymization failed. Return a 500-INTERNAL_SERVER_ERROR.
                    log.error("Pseudonymization failed for identifier \"" + recordDto.getId() + "\" and idType \"" + recordDto.getIdType() + "\".");
                    return responseService.internalServerError(responseContentType);
                }
            }
            p.setPseudonym(pseudonym);

            // Determine validFrom date if (not) given by the user
            if (recordDto.getValidFrom() != null) {
                if (domain.getEnforcestartdatevalidity()) {
                    // Ensure that the given start date isn't before the start date of the domain
                    p.setValidfrom(recordDto.getValidFrom().isAfter(domain.getValidfrom()) ? recordDto.getValidFrom() : domain.getValidfrom());
                    p.setValidfrominherited(!recordDto.getValidFrom().isAfter(domain.getValidfrom()));
                } else {
                    p.setValidfrom(recordDto.getValidFrom());
                    p.setValidfrominherited(false);
                }
            } else {
                // Nothing given by the user. Use domain information.
                p.setValidfrom(domain.getValidfrom());
                p.setValidfrominherited(true);
            }

            // Determine validTo date if (not) given by the user
            if (recordDto.getValidTo() != null) {
                // End date of validity period is given
                if (domain.getEnforceenddatevalidity()) {
                    // Ensure that the given end date isn't after the end date of the domain
                    p.setValidto((recordDto.getValidTo().isBefore(domain.getValidto())) ? recordDto.getValidTo() : domain.getValidto());
                    p.setValidtoinherited(!recordDto.getValidTo().isBefore(domain.getValidto()));
                } else {
                    p.setValidto(recordDto.getValidTo());
                    p.setValidtoinherited(false);
                }
            } else if (recordDto.getValidTo() == null && recordDto.getValidityTime() != null) {
                // A validity period was given
                Long vTime = Utility.validityTimeToSeconds(recordDto.getValidityTime());

                if (domain.getEnforceenddatevalidity()) {
                    // Ensure that the given validity period ends before the end date of the domain
                    p.setValidto((p.getValidfrom().plusSeconds(vTime).isBefore(domain.getValidto())) ? p.getValidfrom().plusSeconds(vTime) : domain.getValidto());
                    p.setValidtoinherited(!p.getValidfrom().plusSeconds(vTime).isBefore(domain.getValidto()));
                } else {
                    p.setValidto(p.getValidfrom().plusSeconds(vTime));
                    p.setValidtoinherited(false);
                }
            } else {
                // Nothing was given: use date from domain
                p.setValidto(domain.getValidto());
                p.setValidtoinherited(true);
            }

            // Add the newly created pseudonym to the list
            pseudonyms.add(p);
        }

        // Insert the list of pseudonyms in one batch
        String result = pseudonymDBAccessService.insertPseudonymBatch(pseudonyms, request);

        // Evaluate the result
        if (result.equals(PseudonymDBAccessService.INSERTION_SUCCESS)) {
            // Success. Return a status code 201-CREATED and the pseudonym as payload.
            List<RecordDto> recordDtos = new ArrayList<>();
            List<String> recordDtosStrings = new ArrayList<>();

            for (Pseudonym record : pseudonyms) {
                RecordDto recordDto = null;

                // Determine whether or not a reduced standard view or a complete view is requested
                if (!authorizationService.currentRequestHasRole("complete-view")) {
                    recordDto = new RecordDto().assignPojoValues(record).toReducedStandardView();
                } else {
                    recordDto = new RecordDto().assignPojoValues(record);
                }

                // Process the DTO depending on the response`s media type
                if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
                    recordDtosStrings.add(recordDto.toRepresentationString());
                } else {
                    recordDtos.add(recordDto);
                }
            }

            // Log success message and return status
            log.debug("Successfully inserted the batch of " + pseudonyms.size() + " pseudonym-records.");

            if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
                return responseService.created(responseContentType, recordDtosStrings);
            } else {
                return responseService.created(responseContentType, recordDtos);
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
     * @param recordDto (required) the Record object
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
    @Operation(
            summary = "Create a record",
            description = "Creates a new pseudonym record in the specified domain. Supports pre-created pseudonyms or generates new ones.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-create", "#domainName"})
            },
            requestBody = @RequestBody(
                    description = "Pseudonym record to be created",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecordDto.class)
                    )
            ),
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Domain name where the record will be created",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "omitPrefix",
                            description = "Whether to omit the prefix for pseudonyms",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "boolean", example = "true")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Record created successfully",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = RecordDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "200",
                            description = "Duplicate record found, returning existing data"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain not found"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error during pseudonym generation"
                    )
            }
    )
    @PostMapping("/domains/{domain}/pseudonym")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-create')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL, message = "Wants to create a new record.")
    public ResponseEntity<?> createRecord(@PathVariable("domain") String domainName,
                                          @RequestBody RecordDto recordDto,
                                          @RequestParam(name = "omitPrefix", required = false, defaultValue = "false") Boolean omitPrefix,
                                          @RequestHeader(name = "accept", required = false) String responseContentType,
                                          HttpServletRequest request) {
    	
        if (!recordDto.validate() || !recordDto.isValidStandardView()) {
            return responseService.unprocessableEntity(responseContentType);
        }

        String identifier = recordDto.getId();
        String idType = recordDto.getIdType();
        String psn = recordDto.getPsn();
        Timestamp validFrom = recordDto.getValidFrom() != null ? Timestamp.valueOf(recordDto.getValidFrom()) : null;
        Timestamp validTo = recordDto.getValidTo() != null ? Timestamp.valueOf(recordDto.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(recordDto.getValidityTime());

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
        Pseudonym p = new Pseudonym();
        p.setIdentifier(identifier);
        p.setIdtype(idType);
        p.setDomainid(domain.getId());

        // Determine validFrom date if (not) given by the user
        if (validFrom != null) {
            if (domain.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                p.setValidfrom((validFrom.after(Timestamp.valueOf(domain.getValidfrom()))) ? validFrom.toLocalDateTime() : domain.getValidfrom());
                p.setValidfrominherited(!validFrom.after(Timestamp.valueOf(domain.getValidfrom())));
            } else {
                p.setValidfrom(validFrom.toLocalDateTime());
                p.setValidfrominherited(false);
            }
        } else {
            p.setValidfrom(domain.getValidfrom());
            p.setValidfrominherited(true);
        }

        // Determine validTo date if (not) given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (domain.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                p.setValidto((validTo.before(Timestamp.valueOf(domain.getValidto()))) ? validTo.toLocalDateTime() : domain.getValidto());
                p.setValidtoinherited(!validTo.before(Timestamp.valueOf(domain.getValidto())));
            } else {
                p.setValidto(validTo.toLocalDateTime());
                p.setValidtoinherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (domain.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                p.setValidto((p.getValidfrom().plusSeconds(validityTime).isBefore(domain.getValidto())) ? p.getValidfrom().plusSeconds(validityTime) : domain.getValidto());
                p.setValidtoinherited(!p.getValidfrom().plusSeconds(validityTime).isBefore(domain.getValidto()));
            } else {
                p.setValidto(p.getValidfrom().plusSeconds(validityTime));
                p.setValidtoinherited(false);
            }
        } else {
            // Nothing was given: use date from domain
            p.setValidto(domain.getValidto());
            p.setValidtoinherited(true);
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
        
        p.setPseudonym(pseudonym);
        
        // Insert the pseudonym-record into the database
        String result = pseudonymDBAccessService.insertPseudonym(p, domain.getMultiplepsnallowed(), request);
        
        // If a random algorithm is used, check if we generated a duplicate. If so, retry.
        if (domain.getAlgorithm().toUpperCase().startsWith("RANDOM")) {
	        // Retry DEFAULT_NUMBER_OF_RETRIES - 1 times
        	for (int i = 1; i < Pseudonymizer.DEFAULT_NUMBER_OF_RETRIES; i++) {
	        	// Check if its actually a duplicate
        		if (result.equals(PseudonymDBAccessService.INSERTION_DUPLICATE_PSEUDONYM)) {
					    // Check the domain's filling rate
        			checkDomainFillingRate(domain);
	        		
	        		// Retry
	        		p.setPseudonym(pseudonymize(identifier, idType, domain, omitPrefix));
	        		result = pseudonymDBAccessService.insertPseudonym(p, domain.getMultiplepsnallowed(), request);
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
            RecordDto newRecordDto = null;

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                newRecordDto = new RecordDto().assignPojoValues(p).toReducedStandardView();
            } else {
                newRecordDto = new RecordDto().assignPojoValues(p);
            }

            log.debug("Successfully inserted a new pseudonym-record (" + pseudonym + ").");

            List<RecordDto> recordDtos = new ArrayList<>();
            recordDtos.add(newRecordDto);

            return responseService.created(responseContentType, recordDtos);
        } else if (result.equals(PseudonymDBAccessService.INSERTION_DUPLICATE_IDENTIFIER)) {
            // Nothing added since the entry is a duplicate. Return an 200-OK status.
            List<Pseudonym> psList = pseudonymDBAccessService.getRecord(domainName, identifier, idType, psn, null);
            List<RecordDto> recordList = new ArrayList<RecordDto>();
            
            for (Pseudonym ps : psList) {
	            RecordDto newRecordDto = null;
	
	            // Determine whether or not a reduced standard view or a complete view is requested
	            if (!authorizationService.currentRequestHasRole("complete-view")) {
	                newRecordDto = new RecordDto().assignPojoValues(ps).toReducedStandardView();
	            } else {
	                newRecordDto = new RecordDto().assignPojoValues(ps);
	            }
	            
	            recordList.add(newRecordDto);
            }

            log.debug("The pseudonym-record requested to be inserted was skipped because it is already in the database.");
            return responseService.ok(responseContentType, recordList);
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
     * 			<li>a <b>404-NOT_FOUND</b> when the given domain wasn't
     * 				found</li>
     * 			<li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the
     * 				records could not be deleted</li>
     */
    @Operation(
            summary = "Delete records in batch",
            description = "Deletes all pseudonym records within the specified domain.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-delete-batch", "#domainName"})
            },
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Domain name where the records will be deleted",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Records deleted successfully"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain not found"
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity or deletion failed"
                    )
            }
    )
    @DeleteMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-delete-batch')")
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL, message = "Wants to delete a batch of records.")
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
        List<Pseudonym> records = domainDBAccessService.getAllRecordsInDomain(domainName, null);

        // Check if anything was found
        if (records == null) {
            // Something went wrong, return a 422-UNPROCESSABLE_ENTITY
            log.error("Retrieving the records for the deletion in the domain \"" + domainName + "\" failed.");
            return responseService.unprocessableEntity(responseContentType);
        } else if (records.size() == 0) {
            // Nothing was found. Return a 200-OK status.
            log.debug("No records were found. Nothing to delete");
            return responseService.ok(responseContentType);
        }

        // Perform deletion
        if (pseudonymDBAccessService.deletePseudonymBatch(records, request)) {
            // Successfully deleted the batch, return a 204-NO_CONTENT.
            log.debug("Successfully deleted all records from domain \"" + domainName + "\".");
            return responseService.noContent(responseContentType);
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
    @Operation(
            summary = "Delete a record",
            description = "Deletes a specific pseudonym record identified by its identifier or pseudonym in the specified domain.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-delete", "#domainName"})
            },
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Domain name where the record is stored",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "id",
                            description = "Identifier of the record to be deleted",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "123456")
                    ),
                    @Parameter(
                            name = "idType",
                            description = "Type of the identifier",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "MY-IDTYPE")
                    ),
                    @Parameter(
                            name = "psn",
                            description = "Pseudonym of the record to be deleted",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "TS-1234567")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Record deleted successfully"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad request due to invalid parameters"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Record or domain not found"
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity or deletion failed"
                    )
            }
    )
    @DeleteMapping("/domains/{domain}/pseudonym")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-delete')")
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL, message = "Wants to delete a record.")
    public ResponseEntity<?> deleteRecord(@PathVariable("domain") String domainName,
                                          @RequestParam(name = "id", required = false) String identifier,
                                          @RequestParam(name = "idType", required = false) String idType,
                                          @RequestParam(name = "psn", required = false) String psn,
                                          @RequestHeader(name = "accept", required = false) String responseContentType,
                                          HttpServletRequest request) {
        // Check if any record-identifying information was given
        // If so, perform deletion
        boolean deletionSuccessful = false;
        if (Assertion.assertNotNullAll(identifier, idType, psn)) {
            // All necessary info was given, perform deletion
            deletionSuccessful = pseudonymDBAccessService.deletePseudonym(domainName, identifier, idType, psn, request);
        } else if (Assertion.assertNotNullAll(identifier, idType) && psn == null) {
            // Delete through identifier
            List<Pseudonym> pList = pseudonymDBAccessService.getRecord(domainName, identifier, idType, null, null);

            // Check if a record with the given identifier exists
            if (pList == null || pList.size() == 0) {
                log.debug("There was no pseudonym-record found for the given identifier and idType.");
                return responseService.notFound(responseContentType);
            } else if (pList.size() > 1) {
            	log.debug("The deletion for the given identifier and idType would have affected more than one record which is not allowed.");
            	return responseService.unprocessableEntity(responseContentType);
            }

            // Perform deletion
            deletionSuccessful = pseudonymDBAccessService.deletePseudonym(domainName, identifier, idType, pList.get(0).getPseudonym(), request);
        } else if (Assertion.assertNullAll(identifier, idType) && psn != null) {
            // Delete through pseudonym
            List<Pseudonym> p = pseudonymDBAccessService.getRecord(domainName, null, null, psn, null);

            // Check if a record with the given pseudonym exists
            if (p == null || p.size() == 0) {
                log.debug("There was no pseudonym-record found for the given identifier and idType.");
                return responseService.notFound(responseContentType);
            }

            // Perform deletion
            deletionSuccessful = pseudonymDBAccessService.deletePseudonym(domainName, p.get(0).getIdentifier(), p.get(0).getIdtype(), psn, request);
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
    @Operation(
            summary = "Get linked pseudonym records",
            description = "Finds and links records along the pseudonym chain between two domains. Note that you must have permission to access these two domains. The result will be a linked list of matching record pairs.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-read", "link-pseudonyms", "#sourceDomain", "#targetDomain"})
            },
            parameters = {
                    @Parameter(
                            name = "sourceDomain",
                            description = "Source domain for the pseudonym chain",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "SourceDomain")
                    ),
                    @Parameter(
                            name = "targetDomain",
                            description = "Target domain for the pseudonym chain",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "TargetDomain")
                    ),
                    @Parameter(
                            name = "sourceIdentifier",
                            description = "Identifier of the starting record in the source domain",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "123456")
                    ),
                    @Parameter(
                            name = "sourceIdType",
                            description = "Type of the identifier in the source domain",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "MY-IDTYPE")
                    ),
                    @Parameter(
                            name = "sourcePsn",
                            description = "Pseudonym of the starting record in the source domain",
                            required = false,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "TS-1234567")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Linked list of record pairs retrieved successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = RecordDto.class))
                            )
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Access denied for the source or target domain"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "No linked pseudonyms found"
                    )
            }
    )
    @GetMapping(value = "/domains/linked-pseudonyms", params = {"sourceDomain", "targetDomain"})
    @PreAuthorize("hasRole('record-read') and hasRole('link-pseudonyms')")
    // Since the domains are given via query-parameters and not via path-parameters, 
    // the rights to access those are determined inside the method.
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to retrieve linked records.")
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
        
        List<Pair<Pseudonym, Pseudonym>> pseudonyms = domainDBAccessService.getLinkedPseudonyms(sourceDomain,
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

        List<List<RecordDto>> listOfRecordPairs = new ArrayList<>();
        for (Pair<Pseudonym, Pseudonym> pair : pseudonyms) {
            List<RecordDto> recordPair = new ArrayList<>();
            
            if (completeView) {
            	recordPair.add(new RecordDto().assignPojoValues(pair.getFirst()).toReducedStandardView());
            	recordPair.add(new RecordDto().assignPojoValues(pair.getSecond()).toReducedStandardView());
            } else {
            	recordPair.add(new RecordDto().assignPojoValues(pair.getFirst()));
            	recordPair.add(new RecordDto().assignPojoValues(pair.getSecond()));
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
    @Operation(
            summary = "Get records in batch",
            description = "Retrieves all pseudonym records stored in the specified domain.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-read-batch", "#domainName"})
            },
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Name of the domain where the records are stored",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Records retrieved successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = RecordDto.class))
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain not found"
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity or retrieval failed"
                    )
            }
    )
    @GetMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read-batch')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to read a batch of records.")
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

        List<RecordDto> resultAsJson = new ArrayList<>();
        List<String> resultAsString = new ArrayList<>();

        // Retrieve the domain the records belong to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);
        if (d == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain where the records should be searched couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Retrieve the pseudonym-records
        List<Pseudonym> records = domainDBAccessService.getAllRecordsInDomain(domainName, request);

        // Check if anything was found
        if (records == null) {
            // Something went wrong
            log.error("Retrieving the records in the domain \"" + domainName + "\" failed.");
            return responseService.unprocessableEntity(responseContentType);
        } else if (records.size() == 0) {
            // Nothing was found. Return a 200-OK status.
            log.debug("No records were found.");
            return responseService.ok(responseContentType, "");
        }

        // Transform result into the desired output format
        for (Pseudonym p : records) {
            RecordDto r = null;

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                r = new RecordDto().assignPojoValues(p).toReducedStandardView();
            } else {
                r = new RecordDto().assignPojoValues(p);
            }

            // Process the DTO depending on the response`s media type
            if (responseContentType != null && responseContentType.equals(MediaType.TEXT_PLAIN_VALUE)) {
                resultAsString.add(r.toRepresentationString());
            } else {
                resultAsJson.add(r);
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
    @Operation(
            summary = "Get record by query",
            description = "Fetches a record identified by its identifier and type or pseudonym within the specified domain.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-read", "#domainName"})
            },
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Name of the domain where the records are stored",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "id",
                            description = "Identifier of the record",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "123456")
                    ),
                    @Parameter(
                            name = "idType",
                            description = "Type of the identifier",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "MY-IDTYPE")
                    ),
                    @Parameter(
                            name = "psn",
                            description = "Pseudonym of the record",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "TS-1234567")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Record retrieved successfully",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = RecordDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain or record not found"
                    )
            }
    )
    @GetMapping(value = "/domains/{domain}/pseudonym", params = {"id", "idType"})
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to read a record identified by its identifier.")
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
        List<Pseudonym> pList = pseudonymDBAccessService.getRecord(domainName, identifier, idType, null, request);

        if (pList != null && pList.size() != 0) {
            // Successfully retrieved record(s), return it to the user as well as a 200-OK
        	List<RecordDto> recordList = new ArrayList<RecordDto>();
        	
        	for (Pseudonym p : pList ) {
	            RecordDto recordDto = null;
	
	            // Determine whether or not a reduced standard view or a complete view is requested
	            if (!authorizationService.currentRequestHasRole("complete-view")) {
	                recordDto = new RecordDto().assignPojoValues(p).toReducedStandardView();
	            } else {
	                recordDto = new RecordDto().assignPojoValues(p);
	            }
	            
	            recordList.add(recordDto);
        	}

            log.debug("Successfully retrieved the requested pseudonym-record.");
            return responseService.ok(responseContentType, recordList);
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
    @Hidden
    @GetMapping(value = "/domains/{domain}/pseudonym", params = "psn")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to read a record identified by its pseudonym.")
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
        List<Pseudonym> pList = pseudonymDBAccessService.getRecord(domainName, null, null, psn, request);

        if (pList != null && pList.size() != 0) {
            // Successfully retrieved a record, return it to the user as well as a 200-OK
        	List<RecordDto> recordList = new ArrayList<RecordDto>();
        	for (Pseudonym p : pList) {
	            RecordDto recordDto = null;
	            
	            // Determine whether or not a reduced standard view or a complete view is requested
	            if (!authorizationService.currentRequestHasRole("complete-view")) {
	                recordDto = new RecordDto().assignPojoValues(p).toReducedStandardView();
	            } else {
	                recordDto = new RecordDto().assignPojoValues(p);
	            }
	            
	            recordList.add(recordDto);
        	}

            // Process the DTO depending on the response`s media type

            log.debug("Successfully retrieved the requested pseudonym-record.");
            return responseService.ok(responseContentType, recordList);
        } else {
            // Nothing found, return a 404-NOT_FOUND
            return responseService.notFound(responseContentType);
        }
    }

    /**
     * This method functions as a ping endpoint.
     *
     * @return <li>a <b>200-OK</b> status</li>
     */
    @Operation(
            summary = "Ping the service",
            description = "Checks if the pseudonymization service is operational.",
            tags = {"Ping"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"domain-read"})
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Service is operational"
                    )
            }
    )
    @GetMapping(value = "/ping")
    @PreAuthorize("hasRole('domain-read')")
    // Authorize a low-level role to include the authorization-time in the baseline
    @Audit(eventType = AuditEventType.PING, auditFor = AuditUserType.ALL, message = "Wants to ping the service.")
    public ResponseEntity<?> ping() {
        // The response to the ping is just a 200-OK status code
        log.debug("Ping.");
        return ResponseEntity.ok().build();
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
    @Operation(
            summary = "Update records in batch",
            description = "Updates a list of pseudonym records in the specified domain. Requires the domain name and list of updated records.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-update-batch", "#domainName"})
            },
            requestBody = @RequestBody(
                    description = "List of pseudonym records to be updated",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = RecordDto.class))
                    )
            ),
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Name of the domain where the records are stored",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Batch updated successfully",
                            content = @Content(schema = @Schema(type = "string"))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain not found",
                            content = @Content(schema = @Schema(type = "string"))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity, update failed",
                            content = @Content(schema = @Schema(type = "string"))
                    )
            }
    )
    @PutMapping("/domains/{domain}/pseudonyms")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-update-batch')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update a batch of records.")
    public ResponseEntity<?> updateRecordBatch(@PathVariable("domain") String domainName,
                                               @RequestBody List<RecordDto> recordDtoList,
                                               @RequestHeader(name = "accept", required = false) String responseContentType,
                                               HttpServletRequest request) {
        int ignored = 0;
        List<Pseudonym> updateablePseudonyms = new ArrayList<>();

        // Retrieve the domain the records belong to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        if (d == null) {
            // The domain wasn't found; return a 404-NOT_FOUND
            log.debug("The domain where the to-be-updated record should be searched couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Iterate over the recordDtos
        for (int i = 0; i < recordDtoList.size(); i++) {
            RecordDto r = recordDtoList.get(i);
            Pseudonym newPseudonym = new Pseudonym();

            // Check if any of the attributes of a record is missing
            if (Assertion.assertNotNullAll(r.getId(), r.getIdType(), r.getPsn(), r.getValidFrom(), r.getValidFromInherited(), r.getValidTo(), r.getValidToInherited())) {
                // Everything that is needed is available. Create new pseudonym-record.
                newPseudonym.setIdentifier(r.getId());
                newPseudonym.setIdtype(r.getIdType());
                newPseudonym.setPseudonym(r.getPsn());
                newPseudonym.setValidfrom(r.getValidFrom());
                newPseudonym.setValidfrominherited(r.getValidFromInherited());
                newPseudonym.setValidto(r.getValidTo());
                newPseudonym.setValidtoinherited(r.getValidToInherited());
                newPseudonym.setDomainid(d.getId());
            } else {
                // Missing attribute values. Log and ignore.
                log.debug("The record with number " + (i + 1) + " is missing some attributes. This (part of the) request is ignored.");
                ignored++;

                // Early "break"
                continue;
            }

            // Add pseudonym object to a list of updatable objects
            updateablePseudonyms.add(newPseudonym);

        } // End for loop

        // Update records
        if (pseudonymDBAccessService.updatePseudonymBatch(updateablePseudonyms, request)) {
            // Success. Return a status code 200-OK.
            log.debug("Successfully updated the batch of " + recordDtoList.size() + " pseudonym-records, from which " + ignored + (ignored == 1 ? " was" : " were") + " ignored.");
            return responseService.ok(responseContentType);
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
     * @param recordDto (required) the record object
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
    @Operation(
            summary = "Update record by query (Complete)",
            description = "Updates a single record identified by its identifier and idType or pseudonym in the specified domain.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-update-complete", "#domainName"})
            },
            requestBody = @RequestBody(
                    description = "Complete record object with updated details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecordDto.class)
                    )
            ),
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Name of the domain where the records are stored",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),@Parameter(
                            name = "id",
                            description = "Identifier of the record to be updated",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "12345")
                    ),
                    @Parameter(
                            name = "idType",
                            description = "Type of the identifier",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "MY-IDTYPE")
                    ),
                    @Parameter(
                            name = "psn",
                            description = "Pseudonym of the record to be updated",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "TP-1234567")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Record updated successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RecordDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain or record not found",
                            content = @Content(schema = @Schema(type = "string"))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity, update failed",
                            content = @Content(schema = @Schema(type = "string"))
                    )
            }
    )
    @PutMapping(value = "/domains/{domain}/pseudonym/complete", params = {"id", "idType"})
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #oldDomainName, 'record-update-complete')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update a record identified by its identifier.")
    public ResponseEntity<?> updateRecordCompleteByIdentifier(@PathVariable("domain") String oldDomainName,
                                                              @RequestBody RecordDto recordDto,
                                                              @RequestParam(name = "id", required = true) String identifier,
                                                              @RequestParam(name = "idType", required = true) String idType,
                                                              @RequestHeader(name = "accept", required = false) String responseContentType,
                                                              HttpServletRequest request) {
        String newIdentifier = recordDto.getId();
        String newIdType = recordDto.getIdType();
        String newPsn = recordDto.getPsn();
        Timestamp validFrom = recordDto.getValidFrom() != null ? Timestamp.valueOf(recordDto.getValidFrom()) : null;
        Timestamp validTo = recordDto.getValidTo() != null ? Timestamp.valueOf(recordDto.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(recordDto.getValidityTime());
        String newDomainName = recordDto.getDomainName();

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
        List<Pseudonym> oldRecordList = pseudonymDBAccessService.getRecord(oldDomainName, identifier, idType, null, null);
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
        
        Pseudonym oldRecord = oldRecordList.get(0);

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
        Pseudonym newRecord = new Pseudonym();
        newRecord.setIdentifier(newIdentifier);
        newRecord.setIdtype(newIdType);
        newRecord.setPseudonym(newPsn);
        newRecord.setDomainid(d.getId());

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newRecord.setValidfrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newRecord.setValidfrominherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newRecord.setValidfrom(validFrom.toLocalDateTime());
                newRecord.setValidfrominherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newRecord.setValidfrom(oldRecord.getValidfrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newRecord.setValidto(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newRecord.setValidtoinherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newRecord.setValidto(validTo.toLocalDateTime());
                newRecord.setValidtoinherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newRecord.getValidfrom().plusSeconds(validityTime) : d.getValidto());
                newRecord.setValidtoinherited(!newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime));
                newRecord.setValidtoinherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldRecord, newRecord, request)) {
            // Update successful. Retrieve the updated record to show it to the user.
            String id = (newIdentifier != null && !newIdentifier.trim().equals("")) ? newIdentifier : identifier;
            String idT = (newIdType != null && !newIdType.trim().equals("")) ? newIdType : idType;
            String p = (newPsn != null && !newPsn.trim().equals("")) ? newPsn : oldRecord.getPseudonym();
            Pseudonym record = pseudonymDBAccessService.getRecord(d.getName(), id, idT, p, null).get(0);
            RecordDto newRecordDto = null;

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                newRecordDto = new RecordDto().assignPojoValues(record).toReducedStandardView();
            } else {
                newRecordDto = new RecordDto().assignPojoValues(record);
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, newRecordDto);
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
     * @param recordDto (required) the record object
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
    @Hidden
    @PutMapping(value = "/domains/{domain}/pseudonym/complete", params = "psn")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #oldDomainName, 'record-update-complete')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update a record identified by its pseudonym.")
    public ResponseEntity<?> updateRecordCompleteByPseudonym(@PathVariable("domain") String oldDomainName,
                                                             @RequestBody RecordDto recordDto,
                                                             @RequestParam(name = "psn", required = true) String psn,
                                                             @RequestHeader(name = "accept", required = false) String responseContentType,
                                                             HttpServletRequest request) {
        String newIdentifier = recordDto.getId();
        String newIdType = recordDto.getIdType();
        String newPsn = recordDto.getPsn();
        Timestamp validFrom = recordDto.getValidFrom() != null ? Timestamp.valueOf(recordDto.getValidFrom()) : null;
        Timestamp validTo = recordDto.getValidTo() != null ? Timestamp.valueOf(recordDto.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(recordDto.getValidityTime());
        String newDomainName = recordDto.getDomainName();

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
        List<Pseudonym> oldRecordList = pseudonymDBAccessService.getRecord(oldDomainName, null, null, psn, null);
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
        
        Pseudonym oldRecord = oldRecordList.get(0);

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
        Pseudonym newRecord = new Pseudonym();
        newRecord.setIdentifier(newIdentifier);
        newRecord.setIdtype(newIdType);
        newRecord.setPseudonym(newPsn);
        newRecord.setDomainid(d.getId());

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newRecord.setValidfrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newRecord.setValidfrominherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newRecord.setValidfrom(validFrom.toLocalDateTime());
                newRecord.setValidfrominherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newRecord.setValidfrom(oldRecord.getValidfrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newRecord.setValidto(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newRecord.setValidtoinherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newRecord.setValidto(validTo.toLocalDateTime());
                newRecord.setValidtoinherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newRecord.getValidfrom().plusSeconds(validityTime) : d.getValidto());
                newRecord.setValidtoinherited(!newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime));
                newRecord.setValidtoinherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldRecord, newRecord, request)) {
            // Update successful. Retrieve the updated record to show it to the user.
            String p = (newPsn != null && !newPsn.trim().equals("")) ? newPsn : psn;
            Pseudonym record = pseudonymDBAccessService.getRecord(d.getName(), null, null, p, null).get(0);
            RecordDto newRecordDto = null;

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                newRecordDto = new RecordDto().assignPojoValues(record).toReducedStandardView();
            } else {
                newRecordDto = new RecordDto().assignPojoValues(record);
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, newRecordDto);
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
     * @param recordDto (required) the record object
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
    @Operation(
            summary = "Update record by query",
            description = "Partially updates a record identified by its identifier and idType or pseudonym in the specified domain.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-update", "#domainName"})
            },
            requestBody = @RequestBody(
                    description = "Partial record object with updated details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecordDto.class)
                    )
            ),
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "Identifier of the record to be updated",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "12345")
                    ),
                    @Parameter(
                            name = "idType",
                            description = "Type of the identifier",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "MY-IDTYPE")
                    ),
                    @Parameter(
                            name = "psn",
                            description = "Pseudonym of the record to be updated",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "TP-1234567")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Record updated successfully",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = RecordDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain or record not found",
                            content = @Content(schema = @Schema(type = "string"))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity, update failed",
                            content = @Content(schema = @Schema(type = "string"))
                    )
            }
    )
    @PutMapping(value = "/domains/{domain}/pseudonym", params = {"id", "idType"})
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-update')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update a record identified by its identifier.")
    public ResponseEntity<?> updateRecordByIdentifier(@PathVariable("domain") String domainName,
                                                      @RequestBody RecordDto recordDto,
                                                      @RequestParam(name = "id", required = true) String identifier,
                                                      @RequestParam(name = "idType", required = true) String idType,
                                                      @RequestHeader(name = "accept", required = false) String responseContentType,
                                                      HttpServletRequest request) {
        Timestamp validFrom = recordDto.getValidFrom() != null ? Timestamp.valueOf(recordDto.getValidFrom()) : null;
        Timestamp validTo = recordDto.getValidTo() != null ? Timestamp.valueOf(recordDto.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(recordDto.getValidityTime());

        if (Assertion.assertNullAll(validFrom, validTo, validityTime)) {
            // An empty object was passed, so there is nothing to update.
            log.debug("The record DTO passed by the user was empty. Nothing to update.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the record belongs to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        // Retrieve the old record
        List<Pseudonym> oldRecordList = pseudonymDBAccessService.getRecord(domainName, identifier, idType, null, null);
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
        
        Pseudonym oldRecord = oldRecordList.get(0);

        // Check if the retrieved values are null
        if (d == null) {
            // Couldn't find the domain. Return a 404-NOT_FOUND
            log.debug("The provided domain (\"" + domainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldRecord == null) {
            // Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Create new pseudonym-record
        Pseudonym newRecord = new Pseudonym();

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newRecord.setValidfrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newRecord.setValidfrominherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newRecord.setValidfrom(validFrom.toLocalDateTime());
                newRecord.setValidfrominherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newRecord.setValidfrom(oldRecord.getValidfrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newRecord.setValidto(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newRecord.setValidtoinherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newRecord.setValidto(validTo.toLocalDateTime());
                newRecord.setValidtoinherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newRecord.getValidfrom().plusSeconds(validityTime) : d.getValidto());
                newRecord.setValidtoinherited(!newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime));
                newRecord.setValidtoinherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldRecord, newRecord, request)) {
            // Update successful. Retrieve the updated record to show it to the user.
            Pseudonym record = pseudonymDBAccessService.getRecord(d.getName(), identifier, idType, oldRecord.getPseudonym(), null).get(0);
            RecordDto newRecordDto = null;

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                newRecordDto = new RecordDto().assignPojoValues(record).toReducedStandardView();
            } else {
                newRecordDto = new RecordDto().assignPojoValues(record);
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, newRecordDto);
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
     * @param recordDto (required) the record object
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
    @Hidden
    @PutMapping(value = "/domains/{domain}/pseudonym", params = "psn")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-update')")
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL, message = "Wants to update a record identified by its pseudonym.")
    public ResponseEntity<?> updateRecordByPseudonym(@PathVariable("domain") String domainName,
                                                     @RequestBody RecordDto recordDto,
                                                     @RequestParam(name = "psn", required = true) String psn,
                                                     @RequestHeader(name = "accept", required = false) String responseContentType,
                                                     HttpServletRequest request) {
        Timestamp validFrom = recordDto.getValidFrom() != null ? Timestamp.valueOf(recordDto.getValidFrom()) : null;
        Timestamp validTo = recordDto.getValidTo() != null ? Timestamp.valueOf(recordDto.getValidTo()) : null;
        Long validityTime = Utility.validityTimeToSeconds(recordDto.getValidityTime());

        if (Assertion.assertNullAll(validFrom, validTo, validityTime)) {
            // An empty object was passed, so there is nothing to update.
            log.debug("The record DTO passed by the user was empty. Nothing to update.");
            return responseService.unprocessableEntity(responseContentType);
        }

        // Retrieve the domain the record belongs to
        Domain d = domainDBAccessService.getDomainByName(domainName, null);

        // Retrieve the old record
        List<Pseudonym> oldRecordList = pseudonymDBAccessService.getRecord(domainName, null, null, psn, null);
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
        
        Pseudonym oldRecord = oldRecordList.get(0);

        // Check if the retrieved values are null
        if (d == null) {
            // Couldn't find the domain. Return a 404-NOT_FOUND
            log.debug("The provided domain (\"" + domainName + "\") couldn't be found.");
            return responseService.notFound(responseContentType);
        } else if (oldRecord == null) {
            // Couldn't find the record that should be updated. Return a 404-NOT_FOUND
            log.debug("The pseudonym-record that should be updated couldn't be found.");
            return responseService.notFound(responseContentType);
        }

        // Create new pseudonym-record
        Pseudonym newRecord = new Pseudonym();

        // Determine the validity of the validFrom date if given by the user
        if (validFrom != null) {
            if (d.getEnforcestartdatevalidity()) {
                // Ensure that the given start date isn't before the start date of the domain
                newRecord.setValidfrom(validFrom.after(Timestamp.valueOf(d.getValidfrom())) ? validFrom.toLocalDateTime() : d.getValidfrom());
                newRecord.setValidfrominherited(!validFrom.after(Timestamp.valueOf(d.getValidfrom())));
            } else {
                newRecord.setValidfrom(validFrom.toLocalDateTime());
                newRecord.setValidfrominherited(false);
            }
        } else {
            // Set the old value in the new record again to easier determine/calculate a potential validTo value
            newRecord.setValidfrom(oldRecord.getValidfrom());
        }

        // Determine the validity of the validTo date if given by the user
        if (validTo != null) {
            // End date of validity period is given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given end date isn't after the end date of the domain
                newRecord.setValidto(validTo.before(Timestamp.valueOf(d.getValidto())) ? validTo.toLocalDateTime() : d.getValidto());
                newRecord.setValidtoinherited(!validTo.before(Timestamp.valueOf(d.getValidto())));
            } else {
                newRecord.setValidto(validTo.toLocalDateTime());
                newRecord.setValidtoinherited(false);
            }
        } else if (validTo == null && validityTime != null) {
            // A validity period was given
            if (d.getEnforceenddatevalidity()) {
                // Ensure that the given validity period ends before the end date of the domain
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()) ? newRecord.getValidfrom().plusSeconds(validityTime) : d.getValidto());
                newRecord.setValidtoinherited(!newRecord.getValidfrom().plusSeconds(validityTime).isBefore(d.getValidto()));
            } else {
                newRecord.setValidto(newRecord.getValidfrom().plusSeconds(validityTime));
                newRecord.setValidtoinherited(false);
            }
        }

        // Update record
        if (pseudonymDBAccessService.updatePseudonym(oldRecord, newRecord, request)) {
            // Update successful. Retrieve the updated record to show it to the user.
            Pseudonym record = pseudonymDBAccessService.getRecord(d.getName(), null, null, psn, null).get(0);
            RecordDto newRecordDto = null;

            // Determine whether or not a reduced standard view or a complete view is requested
            if (!authorizationService.currentRequestHasRole("complete-view")) {
                newRecordDto = new RecordDto().assignPojoValues(record).toReducedStandardView();
            } else {
                newRecordDto = new RecordDto().assignPojoValues(record);
            }

            // Success. Return a status code 200 and the pseudonym-record as payload.
            log.debug("Successfully updated a pseudonym-record.");
            return responseService.ok(responseContentType, newRecordDto);
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
    @Operation(
            summary = "Validate pseudonym",
            description = "Validates a pseudonym in the specified domain to check its validity based on the domain's configuration.",
            tags = {"Record"},
            security = {
                    @SecurityRequirement(name = "ace", scopes = {"record-read", "#domainName"})
            },
            parameters = {
                    @Parameter(
                            name = "domain",
                            description = "Name of the domain where the records are stored",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "string", example = "TestProject")
                    ),
                    @Parameter(
                            name = "psn",
                            description = "Pseudonym to validate",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", example = "TP-1234567")
                    ),
                    @Parameter(
                            name = "accept",
                            description = "Optional response content type (e.g., application/json, application/xml)",
                            required = false,
                            in = ParameterIn.HEADER,
                            schema = @Schema(type = "string", example = "application/json")
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Validation successful",
                            content = @Content(schema = @Schema(type = "boolean", example = "true"))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Domain or pseudonym not found",
                            content = @Content(schema = @Schema(type = "string"))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Unprocessable entity, validation failed due to missing or incorrect configuration",
                            content = @Content(schema = @Schema(type = "string"))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad request, invalid pseudonym format or unexpected input",
                            content = @Content(schema = @Schema(type = "string"))
                    )
            }
    )
    @GetMapping("/domains/{domain}/pseudonym/validation")
    @PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'record-read')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL, message = "Wants to validate a pseudonym.")
    public ResponseEntity<?> validatePseudonym(@PathVariable("domain") String domainName,
								               @RequestParam(name = "psn", required = true) String psn,
								               @RequestHeader(name = "accept", required = false) String responseContentType,
								               HttpServletRequest request) {
    	// Retrieve domain
    	Domain d = domainDBAccessService.getDomainByName(domainName, null);
    	
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

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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trustdeck.algorithms.PseudonymizationFactory;
import org.trustdeck.algorithms.Pseudonymizer;
import org.trustdeck.dto.EntityInstanceDTO;
import org.trustdeck.dto.EntityTypeDTO;
import org.trustdeck.dto.ProjectDTO;
import org.trustdeck.exception.DuplicateEntityInstanceException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.jooq.generated.tables.pojos.Pseudonym;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.service.DomainDBAccessService;
import org.trustdeck.service.EntityInstanceDBService;
import org.trustdeck.service.EntityTypeDBService;
import org.trustdeck.service.JsonSchemaService;
import org.trustdeck.service.ProjectDBService;
import org.trustdeck.service.PseudonymDBAccessService;
import org.trustdeck.service.ResponseService;
import org.trustdeck.utils.Assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class offers a REST API for interacting with entity instances.
 *
 * @author Armin Müller
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api")
public class EntityInstanceRESTController {
	
	/** Enables service for working with predefined responses. */
    @Autowired
    private ResponseService responseService;
    
    /** Enables access to the data base interaction methods for the entity type. */
    @Autowired
    private EntityTypeDBService entityTypeDBService;
    
    /** Enables access to the data base interaction methods for the entity instance. */
    @Autowired
    private EntityInstanceDBService entityInstanceDBService;
    
    /** Enables access to the data base interaction methods for project objects. */
    @Autowired
    private ProjectDBService projectDBService;
	
	/** Enables access to the domain data base interaction methods. */
	@Autowired
	private DomainDBAccessService ddba;

	/** Enables access to the pseudonym data base interaction methods. */
	@Autowired
	private PseudonymDBAccessService pdba;
	
	/** Enables access to the JSON schema validation functionalities. */
	@Autowired
	private JsonSchemaService jsonSchemaService;
	
	/** The maximum number of search results that will be returned to the caller. */
	private final static int MAX_NUMBER_OF_SEARCH_RESULTS = 20; 
	
	/**
	 * Endpoint to create a new instance of an entity type.
	 * If a domain is associated with the given type, this endpoint automatically
	 * creates a pseudonym in the associated domain.
	 * 
	 * @param projectAbbreviation the abbreviation of the project to which the request is scoped to
	 * @param entityTypeName the name of the entity type associated with this instance
	 * @param entityInstanceDTO the data transfer object containing this instance's data
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
	 * @return <li>a <b>201-CREATED</b> status with the created entity instance on success</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the instance payload is 
     *         missing/invalid or fails schema validation</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the project or entity type does not 
     *         cannot be found</li>
     *         <li>a <b>410-GONE</b> status when the project has ended or the entity type 
     *         is marked as deprecated</li>
     *         <li>a <b>422-UNPROCESSABLE_ENTITY</b> status when creation failed</li>
	 */
	@PostMapping("/projects/{projectAbbreviation}/entities/{entityTypeName}")
	@PreAuthorize("hasRole('project-entity-instance-create')")
	@Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> createEntityInstance(@PathVariable("projectAbbreviation") String projectAbbreviation,
			   									  @PathVariable("entityTypeName") String entityTypeName,
												  @RequestBody EntityInstanceDTO entityInstanceDTO,
												  @RequestHeader(name = "accept", required = false) String responseContentType,
												  HttpServletRequest request) {
		// Check if project exists and still active
		ProjectDTO project = projectDBService.getProjectByAbbreviation(projectAbbreviation, null);
		if (project == null) {
			log.debug("Project \"" + projectAbbreviation + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			// Project end was in the past
			log.debug("The project already ended so that no entity types can be retrieved from it.");
			return responseService.gone(responseContentType);
		}
		
		// Check if entity type exists and is still active
		EntityTypeDTO entityType = entityTypeDBService.getEntityTypeByName(entityTypeName, project.getId(), null);
		if (entityType == null) {
			log.debug("Entity type \"" + entityTypeName + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (entityType.getIsDeprecated()) {
			log.debug("The entity type is marked as deprecated and cannot be used anymore.");
			return responseService.gone(responseContentType);
		}
		
		// Check that the provided DTO in the body is there and has data in it
	    if (entityInstanceDTO == null || entityInstanceDTO.getData() == null) {
	        log.debug("No instance payload provided.");
	        return responseService.badRequest(responseContentType);
	    }
	    
	    // Retrieve or build the compiled type schema
    	JsonSchema compiledTypeSchema = jsonSchemaService.getCompiledSchemaFromDefinition(entityType.getTypeDefinition());
    	
    	// Validate the payload data against the type definition
    	List<String> errors = jsonSchemaService.validateInstance(entityInstanceDTO.getData(), compiledTypeSchema);

    	// Check if there are any errors
        if (!errors.isEmpty()) {
            log.debug("Instance payload validation failed.");
            for (String s : errors) {
            	log.trace(s);
            }
            
            return responseService.badRequest(responseContentType);
        }
		
		// Fill the DTO that encapsulates the necessary information
		EntityInstanceDTO createInstance = new EntityInstanceDTO();
		createInstance.setProjectID(project.getId());
		createInstance.setEntityTypeID(entityType.getId());
		createInstance.setData(entityInstanceDTO.getData());
		
		EntityInstanceDTO created;
		try {
			created = entityInstanceDBService.createEntityInstance(createInstance, request);
		} catch (DuplicateEntityInstanceException e) {
			log.info("While creating an entity instance, an identical one was found and will be used instead.");
			return responseService.ok(responseContentType, entityInstanceDBService.getEntityInstance(createInstance, null));
		}
		
		// Evaluate the creation success
		if (created == null) {
			log.info("Entity instance creation failed during database access.");
			return responseService.unprocessableEntity(responseContentType);
		}
		
		// Check if a domain is connected to the instance's type
		if (Assertion.isNotNullOrEmpty(entityType.getAssociatedDomainName())) {
			// There is an associated domain --> a pseudonym should automatically be created
			
			// Retrieve domain
			Domain domain = ddba.getDomainByName(entityType.getAssociatedDomainName(), null);
			
			// If a valid domain object is available, generate and store the psn-value
			if (domain != null) {
				String identifier = created.getTrustdeckID().toString();
				String idType = "TrustDeckID";
				
				// Generate a new pseudonym-value
	            Pseudonymizer pseudonymizer = new PseudonymizationFactory().getPseudonymizer(domain);
	            String rawPseudonym = pseudonymizer.pseudonymize(identifier + idType + domain.getSalt(), domain.getPrefix());
	            String psn = domain.getAddcheckdigit() ? pseudonymizer.addCheckDigit(rawPseudonym, domain.getLengthincludescheckdigit(), domain.getName(), domain.getPrefix()) : rawPseudonym;
				
				// Build pseudonym object
				Pseudonym p = new Pseudonym();
				p.setIdentifier(identifier);
				p.setIdtype(idType);
				p.setPseudonym(psn);
				p.setValidfrom(domain.getValidfrom());
	            p.setValidfrominherited(true);
	            p.setValidto(domain.getValidfrom());
	            p.setValidtoinherited(true);
	            p.setDomainid(domain.getId());
				
	            // Sent to database
				String result = pdba.insertPseudonym(p, false, request);
				
				// Evaluate creation result
				if (!result.equals(PseudonymDBAccessService.INSERTION_SUCCESS)) {
					log.debug("The automatic pseudonym generation failed: " + result);
				} else {
					log.debug("Successfully created a pseudonym for the entity instance.");
				}
			} else {
				log.debug("Could not find the associated domain. No pseudonym was created.");
			}
		}
		
		log.debug("Successfully created the new instance: " + created.getTrustdeckID().toString());
		return responseService.created(responseContentType, created);
	}
	
	/**
	 * Endpoint to retrieve an entity instance.
	 * 
	 * @param projectAbbreviation the abbreviation of the project to which the request is scoped to
	 * @param entityTypeName the name of the entity type associated with this instance
	 * @param trustDeckId the unique UUID for this entity instance
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
	 * @return <li>a <b>200-OK</b> status with the requested entity instance on success</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the project, entity type, or 
     *         instance cannot be found</li>
     *         <li>a <b>410-GONE</b> status when the project has ended or the entity type 
     *         is marked as deprecated</li>
	 */
	@GetMapping("/projects/{projectAbbreviation}/entities/{entityTypeName}/{trustDeckId}")
	@PreAuthorize("hasRole('project-entity-instance-read')")
	@Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> getEntityInstance(@PathVariable("projectAbbreviation") String projectAbbreviation,
											   @PathVariable("entityTypeName") String entityTypeName,
											   @PathVariable("trustDeckId") String trustDeckId,
											   @RequestHeader(name = "accept", required = false) String responseContentType,
											   HttpServletRequest request) {
		
		// Check if project exists and still active
		ProjectDTO project = projectDBService.getProjectByAbbreviation(projectAbbreviation, null);
		if (project == null) {
			log.debug("Project \"" + projectAbbreviation + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			// Project end was in the past
			log.debug("The project already ended so that no entity types can be retrieved from it.");
			return responseService.gone(responseContentType);
		}
		
		// Check if entity type exists and is still active
		EntityTypeDTO entityType = entityTypeDBService.getEntityTypeByName(entityTypeName, project.getId(), null);
		if (entityType == null) {
			log.debug("Entity type \"" + entityTypeName + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (entityType.getIsDeprecated()) {
			log.debug("The entity type is marked as deprecated and cannot be used anymore.");
			return responseService.gone(responseContentType);
		}
		
		// Retrieve instance
		EntityInstanceDTO instance = entityInstanceDBService.getEntityInstance(trustDeckId, request);
		
		// Check result
		if (instance == null) {
			log.debug("No entity instance with the given TrustDeckID was found.");
			return responseService.notFound(responseContentType);
		}
		
		log.debug("Successfully retrieved an entity instance.");
		return responseService.ok(responseContentType, instance);
	}
	
	/**
	 * Endpoint to update an entity instance object. Updatable attributes are
	 * data, is_deleted, created_at, and updated_at.
	 * 
	 * @param projectAbbreviation the abbreviation of the project to which the request is scoped to
	 * @param entityTypeName the name of the entity type associated with this instance
	 * @param trustDeckId the unique UUID for this entity instance
	 * @param entityInstanceDTO the data transfer object containing this instance's data
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
	 * @return <li>a <b>200-OK</b> status with the updated entity instance on success</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when no updatable fields are 
     *         provided or the payload is invalid/fails schema validation</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the project, entity type, or 
     *         target instance cannot be found</li>
     *         <li>a <b>410-GONE</b> status when the project has ended or the entity type 
     *         is marked as deprecated</li>
     *         <li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the update failed</li>
	 */
	@PutMapping("/projects/{projectAbbreviation}/entities/{entityTypeName}/{trustDeckId}")
	@PreAuthorize("hasRole('project-entity-instance-update')")
	@Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> updateEntityInstance(@PathVariable("projectAbbreviation") String projectAbbreviation,
												  @PathVariable("entityTypeName") String entityTypeName,
												  @PathVariable("trustDeckId") String trustDeckId,
												  @RequestBody EntityInstanceDTO entityInstanceDTO,
												  @RequestHeader(name = "accept", required = false) String responseContentType,
												  HttpServletRequest request) {
		
		// Check if project exists and still active
		ProjectDTO project = projectDBService.getProjectByAbbreviation(projectAbbreviation, null);
		if (project == null) {
			log.debug("Project \"" + projectAbbreviation + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			// Project end was in the past
			log.debug("The project already ended so that no entity types can be retrieved from it.");
			return responseService.gone(responseContentType);
		}
		
		// Check if entity type exists and is still active
		EntityTypeDTO entityType = entityTypeDBService.getEntityTypeByName(entityTypeName, project.getId(), null);
		if (entityType == null) {
			log.debug("Entity type \"" + entityTypeName + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (entityType.getIsDeprecated()) {
			log.debug("The entity type is marked as deprecated and cannot be used anymore.");
			return responseService.gone(responseContentType);
		}
		
		// Check that the provided DTO in the body is there
	    if (entityInstanceDTO == null) {
	        log.debug("No update entity instance provided.");
	        return responseService.badRequest(responseContentType);
	    }
		
	    // Check that we have something to update
	    if (Assertion.assertNullAll(entityInstanceDTO.getData(), entityInstanceDTO.getIsDeleted(), entityInstanceDTO.getCreatedAt(), entityInstanceDTO.getUpdatedAt())) {
	    	log.debug("No updatable values given, nothing to update.");
	    	return responseService.badRequest(responseContentType);
	    }
	    
	    // Validate a possibly updated JSONB data part
	    if (entityInstanceDTO.getData() != null) {
	    	// Retrieve the compiled type schema
	    	JsonSchema compiledTypeSchema = jsonSchemaService.getCompiledSchemaFromDefinition(entityType.getTypeDefinition());
	    	
	    	// Validate the new payload data against the type definition
	    	List<String> errors = jsonSchemaService.validateInstance(entityInstanceDTO.getData(), compiledTypeSchema);
	
	    	// Check if there are any errors
	        if (!errors.isEmpty()) {
	            log.debug("Instance payload validation failed.");
	            return responseService.badRequest(responseContentType);
	        }
	    }
	    
	    // Retrieve the old instance
	    EntityInstanceDTO oldInstance = entityInstanceDBService.getEntityInstance(trustDeckId, null);
	    
	    if (oldInstance == null) {
	    	log.debug("Could not find the instance that should be updated.");
	    	return responseService.notFound(responseContentType);
	    }
	    
	    // Collect attributes
	    EntityInstanceDTO newInstance = new EntityInstanceDTO();
	    newInstance.setTrustdeckID(oldInstance.getTrustdeckID());
	    newInstance.setProjectID(oldInstance.getProjectID());
	    newInstance.setEntityTypeID(oldInstance.getEntityTypeID());
	    newInstance.setData(entityInstanceDTO.getData() != null ? entityInstanceDTO.getData() : oldInstance.getData());
	    newInstance.setIsDeleted(entityInstanceDTO.getIsDeleted() != null ? entityInstanceDTO.getIsDeleted() : oldInstance.getIsDeleted());
	    newInstance.setCreatedAt(entityInstanceDTO.getCreatedAt() != null ? entityInstanceDTO.getCreatedAt() : oldInstance.getCreatedAt());
	    newInstance.setUpdatedAt(entityInstanceDTO.getUpdatedAt() != null ? entityInstanceDTO.getUpdatedAt() : OffsetDateTime.now());
	    
		// Update the instance
		EntityInstanceDTO updated = entityInstanceDBService.updateEntityInstance(oldInstance.getId(), newInstance, request);
		
		// Evaluate the update success
		if (updated == null) {
			log.info("Entity instance creation failed during database access.");
			return responseService.unprocessableEntity(responseContentType);
		}
		
		log.debug("Successfully updated the instance: " + updated.getTrustdeckID().toString());
		return responseService.ok(responseContentType, updated);
	}
	
	/**
	 * Endpoint to delete an entity instance. Deletion is performed by tombstoning the instance
	 * through setting the is_deleted-flag accordingly.
	 * 
	 * @param projectAbbreviation the abbreviation of the project to which the request is scoped to
	 * @param entityTypeName the name of the entity type associated with this instance
	 * @param trustDeckId the unique UUID for this entity instance
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
	 * @return <li>a <b>204-NO_CONTENT</b> status when the instance was successfully 
	 * 		   tombstoned</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the TrustDeckID is not a valid 
     *         UUID</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the project or entity type cannot 
     *         be found</li>
     *         <li>a <b>410-GONE</b> status when the project has ended or the entity type 
     *         is marked as deprecated</li>
     *         <li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the deletion failed</li>
	 */
	@DeleteMapping("/projects/{projectAbbreviation}/entities/{entityTypeName}/{trustDeckId}")
	@PreAuthorize("hasRole('project-entity-instance-delete')")
	@Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> deleteEntityInstance(@PathVariable("projectAbbreviation") String projectAbbreviation,
												  @PathVariable("entityTypeName") String entityTypeName,
												  @PathVariable("trustDeckId") String trustDeckId,
												  @RequestHeader(name = "accept", required = false) String responseContentType,
												  HttpServletRequest request) {
		// Check if project exists and still active
		ProjectDTO project = projectDBService.getProjectByAbbreviation(projectAbbreviation, null);
		if (project == null) {
			log.debug("Project \"" + projectAbbreviation + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			// Project end was in the past
			log.debug("The project already ended so that no entity types can be retrieved from it.");
			return responseService.gone(responseContentType);
		}
		
		// Check if entity type exists and is still active
		EntityTypeDTO entityType = entityTypeDBService.getEntityTypeByName(entityTypeName, project.getId(), null);
		if (entityType == null) {
			log.debug("Entity type \"" + entityTypeName + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (entityType.getIsDeprecated()) {
			log.debug("The entity type is marked as deprecated and cannot be used anymore.");
			return responseService.gone(responseContentType);
		}
		
		// Parse trustDeckId into a UUID
		UUID tdid;
		try {
			tdid = UUID.fromString(trustDeckId);
		} catch (IllegalArgumentException e) {
			log.debug("The given TrustDeckID was not a valid UUID.");
			return responseService.badRequest(responseContentType);
		}
		
		// Delete the instance and evaluate the result
		boolean deleted = false;
		try {
			deleted = entityInstanceDBService.deleteEntityInstance(tdid, request);
		} catch (UnexpectedResultSizeException e) {
			if (e.getActualSize() == 0) {
				log.debug("Could not find the entity instance that should be deleted.");
				return responseService.notFound(responseContentType);
			}
		}
		
		// Evaluate deletion result
		if (deleted) {
			log.debug("Successfully deleted (tombstoned) the requested entity instance.");
			return responseService.noContent(responseContentType);
		} else {
			log.debug("Could not delete the requested entity instance.");
			return responseService.unprocessableEntity(responseContentType);
		}
	}
	
	/**
	 * Endpoint to search for entity instances. Multi-word searches are supported.
	 * 
	 * @param projectAbbreviation the abbreviation of the project to which the request is scoped to
	 * @param entityTypeName the name of the entity type associated with this instance
	 * @param query the search string that should be looked up 
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
	 * @return <li>a <b>200-OK</b> status with the list of matching entity instances on 
	 * 		   success</li>
     *         <li>a <b>206-PARTIAL_CONTENT</b> status with a truncated result set when 
     *         more than the maximum number of allowed results are found</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the project or entity type cannot 
     *         be found, or when no instances match the query</li>
     *         <li>a <b>410-GONE</b> status when the project has ended or the entity type 
     *         is marked as deprecated</li>
	 */
	@GetMapping(value = "/projects/{projectAbbreviation}/entities/{entityTypeName}", params = {"query"})
	@PreAuthorize("hasRole('project-entity-instance-search')")
	@Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> searchEntityInstance(@PathVariable("projectAbbreviation") String projectAbbreviation,
												  @PathVariable("entityTypeName") String entityTypeName,
												  @RequestParam(name = "query", required = true) String query,
												  @RequestHeader(name = "accept", required = false) String responseContentType,
												  HttpServletRequest request) {
		
		// Check if project exists and still active
		ProjectDTO project = projectDBService.getProjectByAbbreviation(projectAbbreviation, null);
		if (project == null) {
			log.debug("Project \"" + projectAbbreviation + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			// Project end was in the past
			log.debug("The project already ended so that no entity types can be retrieved from it.");
			return responseService.gone(responseContentType);
		}
		
		// Check if entity type exists and is still active
		EntityTypeDTO entityType = entityTypeDBService.getEntityTypeByName(entityTypeName, project.getId(), null);
		if (entityType == null) {
			log.debug("Entity type \"" + entityTypeName + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (entityType.getIsDeprecated()) {
			log.debug("The entity type is marked as deprecated and cannot be used anymore.");
			return responseService.gone(responseContentType);
		}
		
		// Send query to the database
		List<EntityInstanceDTO> foundInstances = entityInstanceDBService.searchEntityInstance(query, request);
		
		// Evaluate findings
		if (foundInstances == null || foundInstances.size() == 0) {
			log.debug("No entity instances were found for the given search string.");
			return responseService.notFound(responseContentType);
		} else if (foundInstances.size() > MAX_NUMBER_OF_SEARCH_RESULTS) {
			log.debug("Successfully queried the database and found more than " + MAX_NUMBER_OF_SEARCH_RESULTS + " search results, so the result list was truncated.");
			return responseService.partialContent(responseContentType, foundInstances.subList(0, MAX_NUMBER_OF_SEARCH_RESULTS));
		} else {
			log.debug("Successfully queried the database and found " + foundInstances.size() + " search results.");
			return responseService.ok(responseContentType, foundInstances);
		}
	}
	
	/**
	 * Endpoint to check if there are entities in the database similar/equal to the 
	 * given one. 
	 * 
	 * @param projectAbbreviation the abbreviation of the project to which the request is scoped to
	 * @param entityTypeName the name of the entity type associated with the instance to check
	 * @param entityInstanceDTO the data transfer object containing the data of the instance that should be checked
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
	 * @return <li>a <b>200-OK</b> status with a list of candidate entities when matches are found</li>
     *         <li>a <b>204-NO_CONTENT</b> status when no record-linkage candidates are found</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the instance payload is missing/empty or 
     *         none of the required linkage attributes have values or are not defined at all, or 
     *         when payload validation fails</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the project or entity type does not exist</li>
     *         <li>a <b>410-GONE</b> status when the project has ended or the entity type is deprecated</li>
     *         <li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the record-linkage search fails</li>
	 */
	@PostMapping("/projects/{projectAbbreviation}/entities/{entityTypeName}/record-linkage")
	@PreAuthorize("hasRole('project-entity-instance-record-linkage')")
	@Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> recordLinkage(@PathVariable("projectAbbreviation") String projectAbbreviation,
			   							   @PathVariable("entityTypeName") String entityTypeName,
			   							   @RequestBody EntityInstanceDTO entityInstanceDTO,
			   							   @RequestHeader(name = "accept", required = false) String responseContentType,
			   							   HttpServletRequest request) {
		// Check if project exists and still active
		ProjectDTO project = projectDBService.getProjectByAbbreviation(projectAbbreviation, null);
		if (project == null) {
			log.debug("Project \"" + projectAbbreviation + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			// Project end was in the past
			log.debug("The project already ended so that no entity types can be retrieved from it.");
			return responseService.gone(responseContentType);
		}
		
		// Check if entity type exists and is still active
		EntityTypeDTO entityType = entityTypeDBService.getEntityTypeByName(entityTypeName, project.getId(), null);
		if (entityType == null) {
			log.debug("Entity type \"" + entityTypeName + "\" was not found.");
			return responseService.notFound(responseContentType);
		} else if (entityType.getIsDeprecated()) {
			log.debug("The entity type is marked as deprecated and cannot be used anymore.");
			return responseService.gone(responseContentType);
		}
		
		// Check if a payload was given
	    if (entityInstanceDTO == null || entityInstanceDTO.getData() == null) {
	        log.debug("No instance payload or empty data provided for record linkage.");
	        return responseService.badRequest(responseContentType);
	    }
	    
	    // Retrieve or build the compiled type schema
    	JsonSchema compiledTypeSchema = jsonSchemaService.getCompiledSchemaFromDefinition(entityType.getTypeDefinition());
    	
    	// Validate the payload data against the type definition
    	List<String> errors = jsonSchemaService.validateInstance(entityInstanceDTO.getData(), compiledTypeSchema);

    	// Check if there are any errors
        if (!errors.isEmpty()) {
            log.debug("Instance payload validation failed.");
            for (String s : errors) {
            	log.trace(s);
            }
            
            return responseService.badRequest(responseContentType);
        }
        
        // Determine which attributes should be used during the record linkage ("linkage"-flag on attributes)
        List<String> linkageAttributes = new ArrayList<>();
        for (JsonNode attr : entityType.getTypeDefinition().get("attributes")) {
            if (attr.has("linkage") && attr.get("linkage").asBoolean(false)) {
                linkageAttributes.add(attr.get("name").asText());
            }
        }
        
        if (linkageAttributes.isEmpty()) {
            log.debug("The entity type \"" + entityTypeName + "\" defines no linkage attributes.");
            return responseService.badRequest(responseContentType);
        }
        
        // Build a map of the values for the attributes that should be used for RL
        Map<String, JsonNode> linkageValues = new HashMap<>();
        JsonNode data = entityInstanceDTO.getData();
        
        for (String attributeName : linkageAttributes) {
            if (data.has(attributeName) && !data.get(attributeName).isNull() && !Assertion.isJsonEmpty(data.get(attributeName))) {
                linkageValues.put(attributeName, data.get(attributeName));
            }
        }
        
        if (linkageValues.isEmpty()) {
            log.debug("None of the linkage attributes were present or non-empty in the given payload.");
            return responseService.badRequest(responseContentType);
        }
        
        // Search for candidates
        List<EntityInstanceDTO> candidates = entityInstanceDBService.searchRecordLinkageCandidates(project.getId(), entityType.getId(), linkageValues, MAX_NUMBER_OF_SEARCH_RESULTS, request);
	    
        // Evaluate results
        if (candidates == null) {
            log.debug("Record linkage candidate search failed.");
            return responseService.unprocessableEntity(responseContentType);
        } else if (candidates.isEmpty()) {
        	log.debug("No record linkage candidates were found.");
        	return responseService.noContent(responseContentType);
        } else {
        	return responseService.ok(responseContentType, candidates);
        }
	}
}

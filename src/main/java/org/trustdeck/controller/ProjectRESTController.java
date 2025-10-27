/*
 * Trust Deck Services
 * Copyright 2022-2025 Armin Müller & Eric Wündisch
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

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import org.trustdeck.dto.ProjectDTO;
import org.trustdeck.exception.DuplicateProjectException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.service.ProjectDBService;
import org.trustdeck.service.ResponseService;
import org.trustdeck.utils.Assertion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class offers REST API endpoints for interacting with project entities.
 *
 * @author Armin Müller
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api")
public class ProjectRESTController {
	
	/** Enables service for working with predefined responses. */
    @Autowired
    private ResponseService responseService;
    
    /** Enables access to the data base interaction methods. */
    @Autowired
    private ProjectDBService projectDBService;
    
    /** Default value for the project's validity time. */
    private static final Period DEFAULT_PROJECT_VALIDITY_TIME = Period.ofYears(10);
    
    /** Default value for the flag whether or not this project stores entities. */
    private static final boolean DEFAULT_STORE_ENTITIES = true;
    
    /** Default value for the flag whether or not this project stores pseudonyms. */
    private static final boolean DEFAULT_STORE_PSEUDONYMS = true;
    
    /** Pattern/Regex of allowed characters for the abbreviation attribute of projects. */
    private static final Pattern VALID_ABBREVIATION_CHAR_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

	/**
	 * Method to create a new project.
	 * 
	 * @param projectDTO (required) the project object
	 * @param responseContentType (optional) the response content type
	 * @param request the request object, injected by Spring Boot
     * @return <li>a <b>200-OK</b> status with the existing project when a duplicate was detected</li>
     *         <li>a <b>201-CREATED</b> status with the created project and a <b>Location</b> header on success</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the name/abbreviation is missing or dates are invalid</li>
     *         <li>a <b>406-NOT_ACCEPTABLE</b> status when the abbreviation contains disallowed characters</li>
     *         <li>a <b>422-UNPROCESSABLE_ENTITY</b> status when creation failed</li>
	 */
	@PostMapping("/projects")
    @PreAuthorize("hasRole('project-create')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> createProject(@RequestBody ProjectDTO projectDTO,
                                           @RequestHeader(name = "accept", required = false) String responseContentType,
                                           HttpServletRequest request) {
		ProjectDTO p = new ProjectDTO();
		
		// Sanitize all the necessary attributes are given and store them in p
		String name = projectDTO.getName().trim();
		String abbr = projectDTO.getAbbreviation().trim();
		OffsetDateTime start = projectDTO.getStartDate();
		OffsetDateTime end = projectDTO.getEndDate();
		Boolean storeEntities = projectDTO.getStoreEntities();
		Boolean storePseudonyms = projectDTO.getStorePseudonyms();
		String desc = projectDTO.getDescription().trim();
		
		// Check if name and abbreviation are given
		if (Assertion.isNullOrEmpty(name) || Assertion.isNullOrEmpty(abbr)) {
			log.debug("Creating a new project failed due to a missing name or abbreviation.");
			return responseService.badRequest(responseContentType);
		}
		
    	// Ensure that only whitelisted characters are in the abbreviation, so that they do not break endpoints when used in an URI
    	if (!VALID_ABBREVIATION_CHAR_PATTERN.matcher(abbr).matches()) {
    		log.debug("Invalid project abbreviation. Must only contain letters, digits, underscores, or hyphens.");
    	    return responseService.badRequest(responseContentType);
    	}
        	
        URI location = URI.create("/api/projects/" + abbr);
		
		p.setName(name);
		p.setAbbreviation(abbr);
		
		// Handle start and end of project
		start = start != null ? start : OffsetDateTime.now();
		end = end != null ? end : start.plus(DEFAULT_PROJECT_VALIDITY_TIME);
		
		if (end.isBefore(start)) {
			log.debug("Creating a new project failed due to an invalid start and/or end date of the project.");
			log.trace("Start-time: " + start.toString() + ", end-time: " + end.toString());
			responseService.badRequest(responseContentType);
		}
		
		p.setStartDate(start);
		p.setEndDate(end);
		
		// Handle store entities / pseudonyms flags
		storeEntities = storeEntities != null ? storeEntities : DEFAULT_STORE_ENTITIES;
		storePseudonyms = storePseudonyms != null ? storePseudonyms : DEFAULT_STORE_PSEUDONYMS;
		
		p.setStoreEntities(storeEntities);
		p.setStorePseudonyms(storePseudonyms);
		
		p.setDescription(desc);
		
		// Create the project in the database
		ProjectDTO createdProject;
		try {
			createdProject = projectDBService.createProject(p, request);
		} catch (DuplicateProjectException e) {
			log.debug("Encountered a project duplicate. Return the original.");
			return responseService.ok(responseContentType, projectDBService.getProjectByName(name, null));
		}
		
		// Evaluate creation result
		if (createdProject == null) {
			log.info("Creation of a new project failed.");
			return responseService.unprocessableEntity(responseContentType);
		}

		// If we reach this point, creation was successful --> Return a 201-CREATED status
		return responseService.created(responseContentType, location, createdProject);
	}

	/**
	 * Method to retrieve a list of all projects where the 
	 * requesting user has access to.
	 * 
	 * @param responseContentType (optional) the response content type
	 * @param request the request object, injected by Spring Boot
     * @return <li>a <b>501-NOT_IMPLEMENTED</b> status (endpoint not implemented yet)</li>
	 */
	@GetMapping("/projects")
    @PreAuthorize("hasRole('project-read-all')")
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getProjectsWithAccessTo(@RequestHeader(name = "accept", required = false) String responseContentType,
                                           			 HttpServletRequest request) {
		// TODO
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}
	
	/**
	 * Method to retrieve a certain project identified by its
	 * abbreviation.
	 * 
	 * @param abbreviation the project's abbreviation
	 * @param responseContentType (optional) the response content type
	 * @param request the request object, injected by Spring Boot
     * @return <li>a <b>200-OK</b> status with the requested project on success</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the abbreviation is missing or empty</li>
     *         <li>a <b>404-NOT_FOUND</b> status when no project exists for the given abbreviation</li>
	 */
	@GetMapping("/projects/{abbreviation}")
    @PreAuthorize("hasRole('project-read')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getProject(@PathVariable(name = "abbreviation", required = true) String abbreviation,
                                        @RequestHeader(name = "accept", required = false) String responseContentType,
                                        HttpServletRequest request) {
		
		// Null-check the given string
		if (Assertion.isNotNullOrEmpty(abbreviation)) {
			log.debug("No project abbreviation was given.");
			return responseService.badRequest(responseContentType);
		}
		
		ProjectDTO project = projectDBService.getProjectByAbbreviation(abbreviation, request);
		
		if (project == null) {
			log.debug("No project found for the given abbreviation.");
			return responseService.notFound(responseContentType);
		}
		
		// At this point a project was found, return it to the user
		log.debug("Successfully retrieved a project for abbreviation \"" + abbreviation + "\".");
		return responseService.ok(responseContentType, project);
	}
	
	/**
	 * Method to retrieve statistics about a certain project
	 * identified by it's abbreviation.
	 * 
	 * @param abbreviation the project's abbreviation
	 * @param responseContentType (optional) the response content type
	 * @param request the request object, injected by Spring Boot
     * @return <li>a <b>501-NOT_IMPLEMENTED</b> status (endpoint not implemented yet)</li>
	 */
	@GetMapping("projects/{abbreviation}/statistics")
    @PreAuthorize("hasRole('project-read')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> getProjectStatistics(@PathVariable("abbreviation") String abbreviation,
                                				  @RequestHeader(name = "accept", required = false) String responseContentType,
                                				  HttpServletRequest request) {
		
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

	/**
	 * Method to update a project identity identified by it's abbreviation.
	 * 
	 * @param abbreviation the abbreviation of the project that is to be updated
	 * @param newProjectDTO (required) the project object containing all updated values, null-values will lead to keeping the old values
	 * @param responseContentType (optional) the response content type
	 * @param request the request object, injected by Spring Boot
     * @return <li>a <b>200-OK</b> status with the updated project on success</li>
     * 		   <li>a <b>404-NOT_FOUND</b> status when the project does not exist</li>
     *         <li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the update failed</li>
	 */
	@PutMapping("/projects/{abbreviation}")
    @PreAuthorize("hasRole('project-update')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> updateProject(@PathVariable("abbreviation") String abbreviation,
    									   @RequestBody ProjectDTO newProjectDTO,
    									   @RequestHeader(name = "accept", required = false) String responseContentType,
    									   HttpServletRequest request) {
		
		// Check if the original project exists
		if (projectDBService.getProjectByAbbreviation(abbreviation, null) == null) {
			log.debug("The project that should be updated was not found.");
			return responseService.notFound(responseContentType);
		}
		
		// Sanitize the new abbreviation and name
		String abbr = Assertion.isNullOrEmpty(newProjectDTO.getAbbreviation()) ? null : newProjectDTO.getAbbreviation();
		String name = Assertion.isNullOrEmpty(newProjectDTO.getName()) ? null : newProjectDTO.getName();
		newProjectDTO.setAbbreviation(abbr);
		newProjectDTO.setName(name);
		
		// Send the DTO to the database service
		Integer id = projectDBService.updateProject(abbr, newProjectDTO, request);
		
		// Check success of the update
		if (id == null || id < 1) {
			log.debug("Updating the project data failed.");
			return responseService.unprocessableEntity(responseContentType);
		}
		
		// At this point the update was successful, return the updated project object
		return responseService.ok(responseContentType, projectDBService.getProjectByID(id, null));
	}
	
	/**
	 * Method to delete a project identified by it's abbreviation.
	 * The project will not be removed from the database but rather
	 * tombstoned, meaning the end-date will be set properly so that
	 * retrieving, updating, inserting, etc. is not allowed anymore.
	 * 
	 * @param abbreviation the project's abbreviation
	 * @param deleteDate (optional) the date from which the project should be considered as deleted 
	 * @param responseContentType (optional) the response content type
	 * @param request the request object, injected by Spring Boot
     * @return <li>a <b>204-NO_CONTENT</b> status when the project was successfully deleted/tombstoned</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the abbreviation is missing or empty</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the project does not exist</li>
     *         <li>a <b>422-UNPROCESSABLE_ENTITY</b> status when the deletion failed</li>
	 */
	@DeleteMapping("/projects/{abbreviation}")
    @PreAuthorize("hasRole('project-delete')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> deleteProject(@PathVariable("abbreviation") String abbreviation,
    									   @RequestParam(name = "deleteDate", required = false) String deleteDate,
    									   @RequestHeader(name = "accept", required = false) String responseContentType,
                                           HttpServletRequest request) {
		
		// Check the given abbreviation
		if (Assertion.isNullOrEmpty(abbreviation)) {
			log.debug("The given abbreviation was empty.");
			responseService.badRequest(responseContentType);
		}
		
		// Can the project be found
		if (projectDBService.getProjectByAbbreviation(abbreviation, null) == null) {
			log.debug("Project with abbreviation \"" + abbreviation + "\" was not found.");
			return responseService.notFound(responseContentType);
		}
		
		// Sanitize the date that should be used for deletion
		OffsetDateTime end;
		try {
			end = OffsetDateTime.parse(deleteDate);
		} catch (DateTimeParseException e) {
			log.debug("Parsing the given deletion time failed. Using the current time instead.");
			end = OffsetDateTime.now();
		}
		end = end.isBefore(OffsetDateTime.now()) ? end : OffsetDateTime.now();
		
		// Delete the project
		boolean isDeleted;
		try {
			isDeleted = projectDBService.deleteProject(abbreviation, end, request);
		} catch (UnexpectedResultSizeException e) {
			log.debug("Deletion attempt failed and was rolled back.", e);
			return responseService.unprocessableEntity(responseContentType);
		}
		
		// Evaluate the result
		if (isDeleted) {
			log.info("Project with abbreviation \"" + abbreviation + "\" was succesfully deleted (tombstoned).");
			return responseService.noContent(responseContentType);
		} else {
			log.debug("Project deletion was unsuccesful.");
			return responseService.unprocessableEntity(responseContentType);
		}
	}
}

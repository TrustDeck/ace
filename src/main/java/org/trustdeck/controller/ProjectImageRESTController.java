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

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.trustdeck.dto.ProjectDTO;
import org.trustdeck.dto.ProjectImageDTO;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.service.ProjectDBService;
import org.trustdeck.service.ProjectImageDBService;
import org.trustdeck.service.ResponseService;
import org.trustdeck.utils.Assertion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class offers a REST API for interacting with project images.
 *
 * @author Armin Müller
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api")
public class ProjectImageRESTController {

	/** Enables access to the project image database functionalities. */
	@Autowired
	private ProjectImageDBService imageService;

	/** Enables access to the project database functionalities. */
	@Autowired
	private ProjectDBService projectService;

	/** Enables service for working with predefined responses. */
    @Autowired
    private ResponseService responseService;

	/** The maximum image size in bytes. Must match the DB check (5MiB) */
	private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

	/** The set of media types that a given image needs to be in. */
	private static final Set<String> ALLOWED_IMAGE_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/svg+xml");
	
	
	@PostMapping(path = "/projects/{abbreviation}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('project-image-create')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> createProjectImage(@PathVariable(name = "abbreviation", required = true) String abbreviation,
												@RequestPart("file") MultipartFile image,
												@RequestHeader(name = "accept", required = false) String responseContentType,
												HttpServletRequest request) {
		// Check that the given file is not null
		if (image == null || image.isEmpty()) {
			log.debug("No image provided.");
			return responseService.badRequest(responseContentType);
		}
		
		// Ensure that the image is not too big
		if (image.getSize() > MAX_IMAGE_SIZE) {
			log.debug("The given file was too big.");
			return responseService.payloadTooLarge(responseContentType);
		}
		
		// Check that the given file is an image
		if (!ALLOWED_IMAGE_MEDIA_TYPES.contains(image.getContentType())) {
			log.debug("The given file is not of a supported image type (was: \"" + image.getContentType() + "\").");
			return responseService.unsupportedMediaType(responseContentType);

		}
		
		// Retrieve the owning project
		if (Assertion.isNotNullOrEmpty(abbreviation)) {
			log.debug("No project abbreviation was given.");
			return responseService.badRequest(responseContentType);
		}
		
		ProjectDTO project = projectService.getProjectByAbbreviation(abbreviation, null);
		
		if (project == null) {
			log.debug("Could not find the project.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			log.debug("Project has already ended. No changes allowed.");
			return responseService.gone(responseContentType);
		}
		
		// Transform image to bytes
		byte[] imageBytes;
		try {
			imageBytes = image.getBytes();
		} catch (IOException e) {
			log.debug("Representing the image as a byte array failed.");
			return responseService.unprocessableEntity(responseContentType);
		}
		
		// Build DTO
		ProjectImageDTO imageDTO = new ProjectImageDTO();
		imageDTO.setProjectId(project.getId());
		imageDTO.setImageBytes(imageBytes);
		imageDTO.setMimeType(image.getContentType());
		
		// Create the image in the DB
		ProjectImageDTO dto = imageService.createProjectImage(imageDTO, request);
		
		// Check if the creation was successful
		if (dto == null) {
			log.debug("Creating the project image failed.");
			return responseService.unprocessableEntity(responseContentType);
		} else {
			log.info("Successfully saved a new image for the project \"" + project.getName() + "\".");
			return responseService.created(responseContentType);
		}
	}
	

	@GetMapping(path = "/projects/{abbreviation}/image")
	@PreAuthorize("hasRole('project-image-read')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> getProjectImage(@PathVariable(name = "abbreviation", required = true) String abbreviation,
											 @RequestHeader(name = "accept", required = false) String responseContentType,
											 HttpServletRequest request) {
		// Retrieve the owning project
		if (Assertion.isNotNullOrEmpty(abbreviation)) {
			log.debug("No project abbreviation was given.");
			return responseService.badRequest(responseContentType);
		}
		
		ProjectDTO project = projectService.getProjectByAbbreviation(abbreviation, null);
		
		if (project == null) {
			log.debug("Could not find the project.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			log.debug("Project has already ended. No changes allowed.");
			return responseService.gone(responseContentType);
		}
		
		// Retrieve the image from the database		
		ProjectImageDTO dto = imageService.getProjectImageByProjectId(project.getId(), request);
		
		if (dto == null) {
			log.debug("Could not find the project's image.");
			return responseService.notFound(responseContentType);
		} else if (dto.getImageBytes() == null || dto.getMimeType() == null) {
			log.debug("Image was empty.");
			return responseService.noContent(responseContentType);
		}
		
		log.debug("Successfully retrieved the project's image.");
		return responseService.ok(dto.getMimeType(), dto.getImageBytes());
	}
	
	@PutMapping(path = "/projects/{abbreviation}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('project-image-update')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> updateProjectImage(@PathVariable(name = "abbreviation", required = true) String abbreviation,
												@RequestPart("file") MultipartFile image,
												@RequestHeader(name = "accept", required = false) String responseContentType,
												HttpServletRequest request) {
		// Retrieve the owning project
		if (Assertion.isNotNullOrEmpty(abbreviation)) {
			log.debug("No project abbreviation was given.");
			return responseService.badRequest(responseContentType);
		}
		
		ProjectDTO project = projectService.getProjectByAbbreviation(abbreviation, null);
		
		if (project == null) {
			log.debug("Could not find the project.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			log.debug("Project has already ended. No changes allowed.");
			return responseService.gone(responseContentType);
		}
		
		// Check that the given file is not null
		if (image == null || image.isEmpty()) {
			log.debug("No image provided.");
			return responseService.badRequest(responseContentType);
		}
		
		// Ensure that the image is not too big
		if (image.getSize() > MAX_IMAGE_SIZE) {
			log.debug("The given file was too big.");
			return responseService.payloadTooLarge(responseContentType);
		}
		
		// Check that the given file is an image
		if (!ALLOWED_IMAGE_MEDIA_TYPES.contains(image.getContentType())) {
			log.debug("The given file is not of a supported image type (was: \"" + image.getContentType() + "\").");
			return responseService.unsupportedMediaType(responseContentType);

		}
		
		// Transform image to bytes
		byte[] imageBytes;
		try {
			imageBytes = image.getBytes();
		} catch (IOException e) {
			log.debug("Representing the image as a byte array failed.");
			return responseService.unprocessableEntity(responseContentType);
		}
		
		// Build DTO
		ProjectImageDTO imageDTO = new ProjectImageDTO();
		imageDTO.setProjectId(project.getId());
		imageDTO.setImageBytes(imageBytes);
		imageDTO.setMimeType(image.getContentType());
		
		// Update the image in the DB
		ProjectImageDTO dto = imageService.updateProjectImage(project.getId(), imageDTO, request);
		
		// Check if the creation was successful
		if (dto == null) {
			log.debug("Updating the project image failed.");
			return responseService.unprocessableEntity(responseContentType);
		} else {
			log.info("Successfully saved an updated image for the project \"" + project.getName() + "\".");
			return responseService.created(responseContentType);
		}
	}
	
	@DeleteMapping(path = "/projects/{abbreviation}/image")
	@PreAuthorize("hasRole('project-image-delete')") //TODO: maybe analogous to  @auth.hasDomainRoleRelationship(#root, #domainName, 'domain-read')
    @Audit(eventType = AuditEventType.DELETE, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> deleteProjectImage(@PathVariable(name = "abbreviation", required = true) String abbreviation,
												@RequestHeader(name = "accept", required = false) String responseContentType,
												HttpServletRequest request) {
		// Retrieve the owning project
		if (Assertion.isNotNullOrEmpty(abbreviation)) {
			log.debug("No project abbreviation was given.");
			return responseService.badRequest(responseContentType);
		}
		
		ProjectDTO project = projectService.getProjectByAbbreviation(abbreviation, null);
		
		if (project == null) {
			log.debug("Could not find the project.");
			return responseService.notFound(responseContentType);
		} else if (project.getEndDate().isBefore(OffsetDateTime.now())) {
			log.debug("Project has already ended. No changes allowed.");
			return responseService.gone(responseContentType);
		}
		
		// Perform deletion
		boolean deleted;
		try {
			deleted = imageService.deleteProjectImage(project.getId(), request);
		} catch (UnexpectedResultSizeException e) {
			log.warn("Project image deletion would affect an unexpected amount of records and was therefore rolled back.");
			return responseService.unprocessableEntity(responseContentType);
		}
		
		// Evaluate deletion result
		if (deleted) {
			log.debug("Successfully deleted the project image.");
			return responseService.noContent(responseContentType);
		} else {
			log.debug("Project image deletion failed.");
			return responseService.unprocessableEntity(responseContentType);
		}
	}
}

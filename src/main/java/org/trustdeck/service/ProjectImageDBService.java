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

package org.trustdeck.service;

import static org.trustdeck.jooq.generated.Tables.PROJECT_IMAGE;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.MappingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trustdeck.dto.ProjectImageDTO;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.ProjectImage;
import org.trustdeck.jooq.generated.tables.records.ProjectImageRecord;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class encapsulates the database access for project objects.
 */
@Slf4j
@Service
public class ProjectImageDBService {
	
	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
	private DSLContext dsl;
    
    /**
	 * Create a new project image.
	 *
	 * @param projectImage the DTO containing projectId, imageBytes, mimeType
	 * @param request the http request object containing information necessary for the audit trail
	 * @return the created image record as a DTO, or {@code null} on error
	 */
    @Transactional
    public ProjectImageDTO createProjectImage(ProjectImageDTO projectImage, HttpServletRequest request) {
		ProjectImageRecord created;
		try {
			// Build the SQL statement and execute it
			created = dsl.insertInto(PROJECT_IMAGE)
					.set(PROJECT_IMAGE.PROJECT_ID, projectImage.getProjectId())
					.set(PROJECT_IMAGE.IMAGE, projectImage.getImageBytes())
					.set(PROJECT_IMAGE.MIME_TYPE, projectImage.getMimeType())
					.returning()
					.fetchOne();

			// Check the result
			if (created == null) {
				log.debug("Inserting the project image failed.");
				return null;
			}
		} catch (DataAccessException e) {
			log.debug("Inserting the project image failed.", e);
			return null;
		}

		log.debug("Creating the project image for project_id " + created.getProjectId() + " was successful.");
		return new ProjectImageDTO().assignPojoValues(new ProjectImage(created));
    }
    
    /**
     * Retrieve a project image by its projectId.
     * 
     * @param projectId the owning project's ID
     * @param request the http request object containing information necessary for the audit trail
     * @return the project image, or {@code null} when unsuccessful
     */
    @Transactional
    public ProjectImageDTO getProjectImageByProjectId(int projectId, HttpServletRequest request) {
		ProjectImage img;
		try {
			// Build the SQL statement and execute it
			img = dsl.selectFrom(PROJECT_IMAGE)
					.where(PROJECT_IMAGE.PROJECT_ID.eq(projectId))
					.fetchOneInto(ProjectImage.class);
		} catch (MappingException e) {
			log.debug("Could not map the project image search result into the ProjectImage-POJO.", e);
			return null;
		} catch (DataAccessException f) {
			log.debug("Retrieving the project image from the database failed.", f);
			return null;
		}

		// Check the resulting image
		if (img == null) {
			log.debug("No project image was found for projectId " + projectId + ".");
			return null;
		}
		
		log.debug("Successfully retrieved the project image from the database.");
		return new ProjectImageDTO().assignPojoValues(img);
    }

    /**
     * Method to uUpdate the project image.
     * 
     * @param projectId the owning project's ID
     * @param newImage the DTO containing the updated information
     * @param request the http request object containing information necessary for the audit trail
     * @return the updated image as it is represented in the database after the update when successful, {@code null} otherwise
     */
    @Transactional
    public ProjectImageDTO updateProjectImage(int projectId, ProjectImageDTO newImage, HttpServletRequest request) {
    	ProjectImageRecord updated;
		try {
			// Build the SQL statement and execute it
			updated = dsl.update(PROJECT_IMAGE)
					.set(PROJECT_IMAGE.IMAGE, newImage.getImageBytes())
					.set(PROJECT_IMAGE.MIME_TYPE, newImage.getMimeType())
					.where(PROJECT_IMAGE.PROJECT_ID.eq(projectId))
					.returning()
					.fetchOne();
		} catch (DataAccessException e) {
			log.error("Updating the project image failed.", e);
			return null;
		}

		// Check the resulting image
		if (updated == null) {
			log.debug("The project image update failed.");
			return null;
		}

		log.debug("Updating the project image for project with id " + projectId + " was successful.");
		return new ProjectImageDTO().assignPojoValues(new ProjectImage(updated));
    }

    /**
     * Method to remove a project image from the database.
     * 
     * @param projectId the owning project's ID
     * @param request the http request object containing information necessary for the audit trail
     * @return {@code true} when the deletion was successful, {@code false} otherwise
     * @throws UnexpectedResultSizeException
     */
    @Transactional
    public boolean deleteProjectImage(int projectId, HttpServletRequest request) throws UnexpectedResultSizeException {
		int deletedRecords = 0;
		try {
			// Build the SQL statement and execute it
			deletedRecords = dsl.deleteFrom(PROJECT_IMAGE)
					.where(PROJECT_IMAGE.PROJECT_ID.eq(projectId))
					.execute();
		} catch (DataAccessException e) {
			log.debug("Deleting the project image in the database failed.", e);
			return false;
		}

		// Determine success; throw exception to break the transaction in case anything went wrong
        if (deletedRecords != 1) {
			log.debug("Too many records would have been affected by the deletion, which was therefore aborted and rolled back.");
			throw new UnexpectedResultSizeException(1, deletedRecords);
		}
            
        // The project image was successfully deleted
        log.debug("Successfully removed the project image.");
        return true;
    }
}

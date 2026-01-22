/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller
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

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.exception.MappingException;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trustdeck.dto.ProjectDTO;
import org.trustdeck.exception.DuplicateProjectException;
import org.trustdeck.exception.ProjectOIDCException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.Project;
import org.trustdeck.jooq.generated.tables.records.ProjectRecord;
import org.trustdeck.utils.Assertion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.trustdeck.jooq.generated.Tables.ENTITY_INSTANCE;
import static org.trustdeck.jooq.generated.Tables.ENTITY_TYPE;
import static org.trustdeck.jooq.generated.Tables.PROJECT;

/**
 * This class encapsulates the database access for project objects.
 * 
 * @author Armin Müller
 */
@Slf4j
@Service
public class ProjectDBService {
    
	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
	private DSLContext dsl;
    
    /** Handles rights and roles for projects. */
    @Autowired
    private ProjectOIDCService projectOidcService;

    /**
     * Method to insert a new project into the database table.
     * 
     * @param project the project object containing the necessary information
     * @param request the http request object containing information necessary for the audit trail
     * @throws DuplicateProjectException when the project (identified by the name) is already in the database
     * @return The inserted project object including the ID when the insertion was successful,
     * 		   {@code null} when the insertion failed or would create a duplicate.
     */
    @Transactional
    public ProjectDTO createProject(ProjectDTO project, HttpServletRequest request) throws DuplicateProjectException {
    	try {
    		// Insert project
    		ProjectRecord projectRecord = dsl.newRecord(PROJECT);
    		projectRecord.setName(project.getName());
    		projectRecord.setAbbreviation(project.getAbbreviation());
    		projectRecord.setStartDate(project.getStartDate());
    		projectRecord.setEndDate(project.getEndDate());
    		projectRecord.setStoreEntities(project.getStoreEntities());
    		projectRecord.setStorePseudonyms(project.getStorePseudonyms());
    		projectRecord.setDescription(project.getDescription());
    		
    		// Store and determine success
	        if(projectRecord.insert() == 0) {
	        	log.debug("Inserting the project with name \"" + projectRecord.getName() + "\" failed due to an equal record already being in the database.");
	        	return null;
	        }
	        
	        // Update project object after successful insertion
	        project.setId(projectRecord.getId());
    	} catch (DataAccessException e) {
	    	if (e.getMessage().contains(" already exists.")) {
	    		// Found duplicate
	    		log.debug("Insertion of the project object failed due to being a duplicate.");
	    		throw new DuplicateProjectException(project.getName());
	    	}
	    	
	    	log.error("Inserting the new project object into the database failed: " + e.getMessage());
	    	return null;
	    }
    	
    	// Handle rights and roles
        if (request != null) {
            // Check if the information needed for OIDC management are in the token
            JwtAuthenticationToken token = (JwtAuthenticationToken) request.getUserPrincipal();
            if (token == null || token.getToken() == null || token.getToken().getSubject().isBlank()) {
                throw new ProjectOIDCException(project.getName());
            }

            // Create project groups and roles and add the user that made this request to the new groups and roles
            projectOidcService.createProjectGroupsAndRolesAndJoin(project.getName(), token.getToken().getSubject());
        }
	    
	    // Return the project object
        log.debug("Creating the project with name \"" + project.getName() + "\" was successful.");
	    return project;
    }
    
    /**
     * This method retrieves a project by its name.
     * 
     * @param name the name of the project to search for
     * @param request the http request object containing information necessary for the audit trail
     * @return the project object or {@code null} if nothing was found
     */
    @Transactional
    public ProjectDTO getProjectByName(String name, HttpServletRequest request) {
    	// Build and execute the query
    	List<Project> projects = null;
        try {
	        // Execute the query
        	projects = dsl.selectFrom(PROJECT)
	                  .where(PROJECT.NAME.equalIgnoreCase(name))
	                  .fetchInto(Project.class);
        } catch (MappingException e) {
        	log.debug("Could not map the project search result into the Project-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for the project in the database failed: " + f.getMessage());
        }
    	
        // Check if the search was successful
    	if (projects == null || projects.size() == 0) {
    		log.debug("No project with name \"" + name + "\" found.");
            return null;
    	} else if (projects.size() > 1) {
        	log.debug("More than one project object was found. The first result will be used.");
        }

        // Create a ProjectDTO, populate and return it
        return new ProjectDTO().assignPojoValues(projects.getFirst());
    }
    
    /**
     * This method retrieves a project by its abbreviation.
     * 
     * @param abbreviation the abbreviation of the project to search for
     * @param request the http request object containing information necessary for the audit trail
     * @return the project object or {@code null} if nothing was found
     */
    @Transactional
    public ProjectDTO getProjectByAbbreviation(String abbreviation, HttpServletRequest request) {
    	// Build and execute the query
    	List<Project> projects = null;
        try {
	        // Execute the query
        	projects = dsl.selectFrom(PROJECT)
	                  .where(PROJECT.ABBREVIATION.equalIgnoreCase(abbreviation))
	                  .fetchInto(Project.class);
        } catch (MappingException e) {
        	log.debug("Could not map the project search result into the Project-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for the project in the database failed: " + f.getMessage());
        }
    	
        // Check if the search was successful
    	if (projects == null || projects.size() == 0) {
    		log.debug("No project with abbreviation \"" + abbreviation + "\" found.");
            return null;
    	} else if (projects.size() > 1) {
        	log.debug("More than one project object was found. The first result will be used.");
        }

        // Create a ProjectDTO, populate and return it
        return new ProjectDTO().assignPojoValues(projects.getFirst());
    }
    
    /**
     * This method retrieves a project by its id.
     * 
     * @param id the id of the project to search for
     * @param request the http request object containing information necessary for the audit trail
     * @return the project object or {@code null} if nothing was found
     */
    @Transactional
    public ProjectDTO getProjectByID(int id, HttpServletRequest request) {
    	// Build and execute the query
    	List<Project> projects = null;
        try {
	        // Execute the query
        	projects = dsl.selectFrom(PROJECT)
	                  .where(PROJECT.ID.eq(id))
	                  .fetchInto(Project.class);
        } catch (MappingException e) {
        	log.debug("Could not map the project search result into the Project-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for the project in the database failed: " + f.getMessage());
        }
    	
        // Check if the search was successful
    	if (projects == null || projects.size() == 0) {
    		log.debug("No project with ID \"" + id + "\" found.");
            return null;
    	} else if (projects.size() > 1) {
        	log.debug("More than one project object was found. The first result will be used.");
        }

        // Create a ProjectDTO, populate and return it
        return new ProjectDTO().assignPojoValues(projects.getFirst());
    }

    /**
     * Delete a project from the database using its name.
     * The deletion is a tombstoning operation where the 
     * end_date is set and no real deletion is performed. 
     * 
     * @param project the DTO containing information about the project that should be deleted
     * @param deleteDate the date which should be used for the end_date
     * @param request the http request object containing information necessary for the audit trail
     * @return {@code true} when the deletion was successful, {@code false} when the project object 
     * 			that should be deleted was not found
     * @throws UnexpectedResultSizeException whenever the deletion would not exactly affect one project entry
     */
    @Transactional
    // TODO: When deleting a project, should the types defined in it also be considered deleted? What about the instances using the type?
    public boolean deleteProject(ProjectDTO project, OffsetDateTime deleteDate, HttpServletRequest request) throws UnexpectedResultSizeException {
    	// Check if the given project is valid
    	if (project != null && project.getId() != null && project.getId() > 0) {
    		// Check if project is still active
    		if (project.getEndDate().isBefore(deleteDate)) {
    			log.debug("The project's end date is already in the past. Deleting (tombstoning) not necessary.");
    			return true;
    		}
    		
        	// Delete project object by updating the end_date
        	int deletedRecords = 0;
			try {
				deletedRecords = dsl.update(PROJECT)
						.set(PROJECT.END_DATE, deleteDate)
						.where(PROJECT.ID.eq(project.getId()))
						.execute();
			} catch (DataAccessException e) {
				log.error("Failed to delete the project \"" + project.getAbbreviation() + "\".", e);
				return false;
			}
        	
        	// Determine success
            if (deletedRecords != 1) {
            	// Throw exception to terminate the transaction
                log.debug("Deleting the project with abbreviation \"" + project.getAbbreviation() + "\" would not affect exactly one entry. Aborting.");
                throw new UnexpectedResultSizeException(1, deletedRecords);
            }
    	
	    	// Handle rights and roles
	        if (request != null) {
	            // Check if the information needed for OIDC management are in the token
	            JwtAuthenticationToken token = (JwtAuthenticationToken) request.getUserPrincipal();
	            if (token == null || token.getToken() == null || token.getToken().getSubject().isBlank()) {
	                throw new ProjectOIDCException(project.getName());
	            }
	
	            // Remove the associated groups and roles for this domain from Keycloak
	            projectOidcService.leaveAndDeleteProjectGroupsAndRoles(project.getName());
	        }
            
            // The project object was successfully deleted
            log.debug("Successfully removed the project object.");
            return true;
        }
        
        // At this point, no project with the given name was found
        log.debug("No project with the given abbreviation found. Nothing to delete.");
        return false;
    }

    /**
     * This method updates a project identified by its name.
     * 
     * @param oldProject the project object containing information of the project that should be updated
     * @param updatedProject the project object containing the updated information
     * @param request the http request object containing information necessary for the audit trail
     * @return the ID of the updated project, or {@code null} if an error occurred
     */
    @Transactional
    public ProjectDTO updateProject(ProjectDTO oldProject, ProjectDTO updatedProject, HttpServletRequest request) {
    	// Check if the old record was given
		if (oldProject == null || oldProject.getId() == null) {
			log.debug("The project object that should be updated was not found.");
			return null;
		}
		
		// Check if the project is deleted/tombstoned
		if (oldProject.getEndDate().isBefore(OffsetDateTime.now())) {
			log.debug("The project cannot be updated because it is deleted/tombstoned.");
			return null;
		}
		
		// Check if the project is in use (find project_id mentions in the type table and in the instance table)
		int usedBy = 0;
    	try {
    		usedBy = dsl.select(
    				DSL.field(dsl.selectCount()
    	                     .from(ENTITY_TYPE)
    	                     .where(ENTITY_TYPE.PROJECT_ID.eq(oldProject.getId())))
    				.add(DSL.field(dsl.selectCount()
    	                     .from(ENTITY_INSTANCE)
    	                     .where(ENTITY_INSTANCE.PROJECT_ID.eq(oldProject.getId()))))
    				).fetchOne(0, int.class);
    	} catch (DataAccessException e) {
    		log.debug("Searching for entity type references in the database failed.", e);
    		return null;
    	}
    	
    	// Built update object with sanitized values, depending on if the project is in use or not
		ProjectRecord updatedProjectRecord;
		String newName = null;
		try {
			if (usedBy != 0) {
				log.debug("The project is currently in use and can therefore only be partially updated.");

				OffsetDateTime newStart = updatedProject.getStartDate() != null ? updatedProject.getStartDate() : oldProject.getStartDate();
				OffsetDateTime newEnd = updatedProject.getEndDate() != null ? updatedProject.getEndDate() : oldProject.getEndDate();

				// Sanity check: is the end after the start
				newStart = newStart.isBefore(newEnd) ? newStart : oldProject.getStartDate();
				newEnd = newEnd.isAfter(newStart) ? newEnd : oldProject.getEndDate();

				String newDescription = updatedProject.getDescription() != null ? updatedProject.getDescription() : oldProject.getDescription();

				// Do the actual update
				updatedProjectRecord = dsl.update(PROJECT)
						.set(PROJECT.START_DATE, newStart)
						.set(PROJECT.END_DATE, newEnd)
						.set(PROJECT.DESCRIPTION, newDescription)
						.where(PROJECT.ID.eq(oldProject.getId()))
						.returning()
						.fetchOne();
			} else {
				newName = Assertion.isNotNullOrEmpty(updatedProject.getName()) ? updatedProject.getName() : oldProject.getName();
				String newAbbreviation = Assertion.isNotNullOrEmpty(updatedProject.getAbbreviation())? updatedProject.getAbbreviation() : oldProject.getAbbreviation();
				OffsetDateTime newStart = updatedProject.getStartDate() != null ? updatedProject.getStartDate() : oldProject.getStartDate();
				OffsetDateTime newEnd = updatedProject.getEndDate() != null ? updatedProject.getEndDate() : oldProject.getEndDate();

				// Sanity check: is the end after the start
				newStart = newStart.isBefore(newEnd) ? newStart : oldProject.getStartDate();
				newEnd = newEnd.isAfter(newStart) ? newEnd : oldProject.getEndDate();

				Boolean newStoreEntities = updatedProject.getStoreEntities() != null ? updatedProject.getStoreEntities() : oldProject.getStoreEntities();
				Boolean newStorePseudonyms = updatedProject.getStorePseudonyms() != null ? updatedProject.getStorePseudonyms() : oldProject.getStorePseudonyms();
				String newDescription = updatedProject.getDescription() != null ? updatedProject.getDescription() : oldProject.getDescription();

				updatedProjectRecord = dsl.update(PROJECT)
						.set(PROJECT.NAME, newName)
						.set(PROJECT.ABBREVIATION, newAbbreviation)
						.set(PROJECT.START_DATE, newStart)
						.set(PROJECT.END_DATE, newEnd)
						.set(PROJECT.STORE_ENTITIES, newStoreEntities)
						.set(PROJECT.STORE_PSEUDONYMS, newStorePseudonyms)
						.set(PROJECT.DESCRIPTION, newDescription)
						.where(PROJECT.ID.eq(oldProject.getId()))
						.returning()
						.fetchOne();
			}
		} catch (IntegrityConstraintViolationException e) {
			log.debug("The date constraint that is built in the database was violated: start date was after end date.");
			return null;
    	} catch (DataAccessException e) {
    		log.debug("Updating the project object \"" + oldProject.getName() + "\" failed.");
            return null;
		}
    	
    	// Check result
    	if (updatedProjectRecord == null) {
    		log.debug("Updating the project object \"" + oldProject.getName() + "\" failed.");
            return null;
    	}
    	
    	// Check if the OIDC rights and roles need to be adapted
        if (newName != null && !oldProject.getName().equals(newName)
                    && projectOidcService.canBeUsedAsProjectGroup(newName)) {
        	// The project name has changed, so we need to update the OIDC rights and roles
        	
        	// Check if the required request object is available
        	if (request != null) {
                JwtAuthenticationToken token = (JwtAuthenticationToken) request.getUserPrincipal();
                
                if (token != null && token.getToken() != null && !token.getToken().getSubject().isBlank()) {
                    try {
                    	projectOidcService.updateProjectGroups(oldProject.getName(), newName, token.getToken().getSubject());
                    } catch (Exception e) {
                        log.error("Updating OIDC rights and roles failed: " + e.getMessage());
                        throw new ProjectOIDCException("oldName: " + oldProject.getName() + ", newName: " + newName);
                    }
                }
            }
        }

		// Return the newly updated project DTO
		log.debug("Updating the project object \"" + oldProject.getName() + "\" succeeded.");
        return new ProjectDTO().assignPojoValues(new Project(updatedProjectRecord));
    }
    
    /**
     * This method retrieves all projects.
     * 
     * @param request the http request object containing information necessary for the audit trail
     * @return all projects as a list
     */
    @Transactional(readOnly = true)
    public List<ProjectDTO> getAllProjects(HttpServletRequest request) {
    	// Build and execute the query
    	List<Project> projects = null;
        try {
	        // Execute the query
        	projects = dsl.selectFrom(PROJECT)
	                  .fetchInto(Project.class);
        } catch (MappingException e) {
        	log.debug("Could not map the project search result into the Project-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for the project in the database failed: " + f.getMessage());
        }
        
        // Transform projects into the right format
        List<ProjectDTO> dtos = new ArrayList<>();
        for (Project p : projects) {
        	dtos.add(new ProjectDTO().assignPojoValues(p));
        }

        // Return the list of projects
        return dtos;
    }
    
    /**
     * This method retrieves all project names.
     * 
     * @param request the http request object containing information necessary for the audit trail
     * @return the names of all projects as a list
     */
    @Transactional(readOnly = true)
    public List<String> getAllProjectNames(HttpServletRequest request) {
    	// Build and execute the query
    	List<String> projects = null;
        try {
	        // Execute the query
        	projects = dsl.select(PROJECT.NAME)
                    .from(PROJECT)
                    .fetch(PROJECT.NAME);
        } catch (MappingException e) {
        	log.debug("Could not map the project search result into the Project-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for the project in the database failed: " + f.getMessage());
        }
        
        // Return the list of projects
        return projects;
    }
}

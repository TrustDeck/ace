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
import org.jooq.exception.MappingException;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trustdeck.dto.ProjectDTO;
import org.trustdeck.exception.DuplicateProjectException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.Project;
import org.trustdeck.jooq.generated.tables.records.ProjectRecord;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.trustdeck.jooq.generated.Tables.ENTITY_INSTANCE;
import static org.trustdeck.jooq.generated.Tables.ENTITY_TYPE;
import static org.trustdeck.jooq.generated.Tables.PROJECT;

/**
 * This class encapsulates the database access for project objects.
 */
@Slf4j
@Service
public class ProjectDBService {
    
	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
	private DSLContext dsl;
    
    /** Enables access to the entity type database service. */
    @Autowired
    private EntityTypeDBService ets;

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
    public Project createProject(Project project, HttpServletRequest request) throws DuplicateProjectException {
    	try {
    		ProjectRecord projectRecord = dsl.newRecord(PROJECT);
    		projectRecord.setName(project.getName());
    		projectRecord.setAbbreviation(project.getAbbreviation());
    		projectRecord.setStartDate(project.getStartDate());
    		projectRecord.setEndDate(project.getEndDate());
    		projectRecord.setStoreEntities(project.getStoreEntities());
    		projectRecord.setStorePseudonyms(project.getStorePseudonyms());
    		projectRecord.setDescription(project.getDescription());
    		projectRecord.setAssociatedEntityTypeIds(project.getAssociatedEntityTypeIds());
    		
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
	                  .and(PROJECT.END_DATE.ge(OffsetDateTime.now()))
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
	                  .and(PROJECT.END_DATE.ge(OffsetDateTime.now()))
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
	                  .and(PROJECT.END_DATE.ge(OffsetDateTime.now()))
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
     * @param name the name of the project that should be deleted
     * @param request the http request object containing information necessary for the audit trail
     * @return {@code true} when the deletion was successful, {@code false} when the project object 
     * 			that should be deleted was not found
     * @throws UnexpectedResultSizeException whenever the deletion would not exactly affect one project entry
     */
    @Transactional
    public boolean deleteProject(String name, HttpServletRequest request) throws UnexpectedResultSizeException {
    	// Fetch the project to check if it exists
    	ProjectDTO project = getProjectByName(name, null);
        
    	if (project != null) {
    		// Check if project is still active
    		if (project.getEndDate().isBefore(OffsetDateTime.now())) {
    			log.debug("The project's end date is already in the past. Deleting (tombstoning) not necessary.");
    			return true;
    		}
    		
        	// Delete project object by updating the end_date
        	int deletedRecords = 0;
			try {
				deletedRecords = dsl.update(PROJECT)
						.set(PROJECT.END_DATE, OffsetDateTime.now())
						.where(PROJECT.NAME.equalIgnoreCase(project.getName()))
						.execute();
			} catch (DataAccessException e) {
				log.error("Failed to delete the project \"" + project.getName() + "\".", e);
				return false;
			}
        	
        	// Determine success
            if (deletedRecords != 1) {
            	// Throw exception to terminate the transaction
                log.debug("Deleting the project with name \"" + project.getName() + "\" would not affect exactly one entry. Aborting.");
                throw new UnexpectedResultSizeException(1, deletedRecords);
            }
            
            // The project object was successfully deleted
            log.debug("Successfully removed the project object.");
            return true;
        }
        
        // At this point, no project with the given name was found
        log.debug("No project with the given name found. Nothing to delete.");
        return false;
    }

    /**
     * This method updates a project identified by its name.
     * 
     * @param name the name of the project that should be updated
     * @param updatedProject the project object containing the updated information
     * @param request the http request object containing information necessary for the audit trail
     * @return the ID of the updated project, or {@code null} if an error occurred
     */
    @Transactional
    public Integer updateProject(String name, ProjectDTO updatedProject, HttpServletRequest request) {
    	// Get data needed for the update
    	ProjectDTO oldProject = getProjectByName(name, null);
		
		// Check if the old record was found
		if (oldProject == null) {
			log.debug("The project object that should be updated was not found.");
			return null;
		}
		
		// Check if the project is in use
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
    	
    	// Built update object, depending on if the project is in use or not
		ProjectRecord projectRecord = new ProjectRecord();
    	if (usedBy != 0) {
    		log.debug("The project is currently in use and can therefore only be partially updated.");
    		
    		// Sanitize the given values and update the attributes
    		// Handle times
			OffsetDateTime newStart = updatedProject.getStartDate() != null ? updatedProject.getStartDate() : oldProject.getStartDate();
			OffsetDateTime newEnd = updatedProject.getEndDate() != null ? updatedProject.getEndDate() : oldProject.getEndDate();
    		projectRecord.setStartDate(newStart.isBefore(newEnd) ? newStart : oldProject.getStartDate());
			projectRecord.setEndDate(newEnd.isAfter(newStart) ? newEnd : oldProject.getEndDate());
    		
			projectRecord.setDescription(updatedProject.getDescription() != null ? updatedProject.getDescription() : oldProject.getDescription());
			
			// Merge the new types with the old types --> no removal of old types in the partial update allowed
			String[] newTypes = updatedProject.getAssociatedEntityTypes() != null && updatedProject.getAssociatedEntityTypes().length != 0 ? updatedProject.getAssociatedEntityTypes() : oldProject.getAssociatedEntityTypes();
			Set<String> merged = new HashSet<>();
			merged.addAll(Arrays.asList(oldProject.getAssociatedEntityTypes()));
			merged.addAll(Arrays.asList(newTypes));
			projectRecord.setAssociatedEntityTypeIds(ets.getEntityTypeIDs(oldProject.getId(), merged.toArray(new String[0])));
    	} else {
			// Sanitize the given values and update the attributes
			projectRecord.setName(updatedProject.getName() != null && !updatedProject.getName().isBlank() ? updatedProject.getName() : oldProject.getName());
			projectRecord.setAbbreviation(updatedProject.getAbbreviation() != null && !updatedProject.getAbbreviation().isBlank() ? updatedProject.getAbbreviation() : oldProject.getAbbreviation());
    		projectRecord.setStartDate(updatedProject.getStartDate() != null ? updatedProject.getStartDate() : oldProject.getStartDate());
			projectRecord.setEndDate(updatedProject.getEndDate() != null ? updatedProject.getEndDate() : oldProject.getEndDate());
			projectRecord.setStoreEntities(updatedProject.getStoreEntities() != null ? updatedProject.getStoreEntities() : oldProject.getStoreEntities());
			projectRecord.setStorePseudonyms(updatedProject.getStorePseudonyms() != null ? updatedProject.getStorePseudonyms() : oldProject.getStorePseudonyms());
			projectRecord.setDescription(updatedProject.getDescription() != null ? updatedProject.getDescription() : oldProject.getDescription());
			projectRecord.setAssociatedEntityTypeIds(updatedProject.getAssociatedEntityTypes() != null && updatedProject.getAssociatedEntityTypes().length != 0 ? ets.getEntityTypeIDs(oldProject.getId(), updatedProject.getAssociatedEntityTypes()) : ets.getEntityTypeIDs(oldProject.getId(), oldProject.getAssociatedEntityTypes()));
    	}
			
        // Store and determine success
        int wasStored = projectRecord.update();
        log.debug("Updating the project object \"" + projectRecord.getName() + "\" " + ((wasStored == 1) ? "succeeded." : "failed."));
        
        // Return the project ID when successful
        return wasStored == 1 ? projectRecord.getId() : null;
    }
}

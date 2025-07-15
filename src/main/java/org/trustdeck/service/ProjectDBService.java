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
import org.jooq.exception.TooManyRowsException;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.trustdeck.jooq.generated.Tables.OBJECTTYPE;
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
    		projectRecord.setStartdate(project.getStartdate());
    		projectRecord.setEnddate(project.getEnddate());
    		projectRecord.setMainContact(project.getMainContact());
    		projectRecord.setAssociatedObjecttypeIds(project.getAssociatedObjecttypeIds());
    		
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
     * Also deletes orphaned algorithm objects.
     * 
     * @param name the name of the project that should be deleted
     * @param request the http request object containing information necessary for the audit trail
     * @return {@code true} when the deletion was successful, {@code false} when the project object 
     * 			that should be deleted was not found
     * @throws UnexpectedResultSizeException whenever the deletion would not exactly affect one project entry
     */
    @Transactional
    public boolean deleteProject(String name, HttpServletRequest request) throws UnexpectedResultSizeException {
    	// Fetch the person to check if it exist and to get the algorithm ID before deletion
    	ProjectDTO project = getProjectByName(name, null);
        if (project != null) {
        	// Delete person object
        	int deletedRecords = dsl.deleteFrom(PROJECT)
               .where(PROJECT.NAME.equalIgnoreCase(name))
               .execute();
        	
        	// Determine success
            if (deletedRecords > 1) {
            	// Throw exception to terminate the transaction
                log.debug("Deleting the project with name \"" + project.getName() + "\" would not affect exactly one entry. Aborting.");
                throw new UnexpectedResultSizeException(1, deletedRecords);
            } else if (deletedRecords == 0) {
            	log.debug("Nothing found to delete.");
            	return false;
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
    	DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    	
    	// Get data needed for the update
    	ProjectDTO oldProject = getProjectByName(name, null);
    	
    	    		
    	// Update the project object
    	// Fetch old project record
    	ProjectRecord projectRecord = null;
    	try {
    		projectRecord = dsl.fetchOne(PROJECT, PROJECT.ID.eq(oldProject.getId()));
		} catch (DataAccessException e) {
			log.debug("Fetching the project record that should be updated failed (ID: " + oldProject.getId() + ").");
			return null;
		}
		
		// Check if the old record was found
		if (projectRecord == null) {
			log.debug("The project object that should be updated was not found (ID: " + oldProject.getId() + ").");
			return null;
		}
		
		// Sanitize the given values and update the attributes
		projectRecord.setName(updatedProject.getName() != null && !updatedProject.getName().isBlank() ? updatedProject.getName() : oldProject.getName());
		projectRecord.setStartdate(updatedProject.getStartDate() != null && !updatedProject.getStartDate().isBlank() ? LocalDate.parse(updatedProject.getStartDate(), dateFormatter) : LocalDate.parse(oldProject.getStartDate(), dateFormatter));
		projectRecord.setEnddate(updatedProject.getEndDate() != null && !updatedProject.getEndDate().isBlank() ? LocalDate.parse(updatedProject.getEndDate(), dateFormatter) : LocalDate.parse(oldProject.getEndDate(), dateFormatter));
		projectRecord.setMainContact(updatedProject.getMainContact() != null && !updatedProject.getMainContact().isBlank() ? updatedProject.getMainContact() : oldProject.getMainContact());
		projectRecord.setAssociatedObjecttypeIds(updatedProject.getAssociatedObjects() != null && updatedProject.getAssociatedObjects().length != 0 ? getObjectTypeIDs(updatedProject.getAssociatedObjects()) : getObjectTypeIDs(oldProject.getAssociatedObjects()));
        
        // Store and determine success
        int wasStored = projectRecord.update();
        log.debug("Updating the project object \"" + projectRecord.getName() + "\" " + ((wasStored == 1) ? "succeeded." : "failed."));
        
        // Return the project ID when successful
        return wasStored == 1 ? projectRecord.getId() : null;
    }
    
    /**
     * This method retrieves the names for the given object type IDs from a project.
     * 
     * @param objectTypeIDs the array of object type IDs stored in a project
     * @return an array of the names of the object types in the same order as the IDs were given.
     */
    @Transactional
    public String[] getObjectTypeNames(Integer[] objectTypeIDs) {
    	String[] objectTypeNames = new String[objectTypeIDs.length];
    	
    	for (int i = 0; i < objectTypeIDs.length; i++) {
    		String name = null;
    		
    		try {
    			name = dsl.select(OBJECTTYPE.NAME)
	    	                 .from(OBJECTTYPE)
	    	                 .where(OBJECTTYPE.ID.eq(objectTypeIDs[i]))
	    	                 .fetchOneInto(String.class);
    		} catch (TooManyRowsException e) {
            	log.debug("Found more than one project.");
        	} catch (MappingException f) {
            	log.debug("Could not map the search result.");
            } catch (DataAccessException g) {
            	log.debug("Searching for the object name in the database failed: " + g.getMessage());
            }
    		
    		objectTypeNames[i] = name;
    	}
    	
    	return objectTypeNames;
    }
    
    /**
     * This method retrieves the IDs for the given object type names from a project.
     * 
     * @param objectTypeNames the array of object type names stored in a project
     * @return an array of the IDs of the object types in the same order as the names were given.
     */
    @Transactional
    public Integer[] getObjectTypeIDs(String[] objectTypeNames) {
    	Integer[] objectTypeIDs = new Integer[objectTypeNames.length];
    	
    	for (int i = 0; i < objectTypeNames.length; i++) {
    		Integer id = null;
    		
    		try {
    			id = dsl.select(OBJECTTYPE.ID)
	    	            .from(OBJECTTYPE)
	    	            .where(OBJECTTYPE.NAME.equalIgnoreCase(objectTypeNames[i]))
	    	            .fetchOneInto(Integer.class);
    		} catch (TooManyRowsException e) {
            	log.debug("Found more than one project.");
        	} catch (MappingException f) {
            	log.debug("Could not map the search result.");
            } catch (DataAccessException g) {
            	log.debug("Searching for the object ID in the database failed: " + g.getMessage());
            }
    		
    		objectTypeIDs[i] = id;
    	}
    	
    	return objectTypeIDs;
    }
}

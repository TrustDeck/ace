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

import static org.trustdeck.jooq.generated.Tables.ENTITYTYPE;
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
    		projectRecord.setAbbreviation(project.getAbbreviation());
    		projectRecord.setStartdate(project.getStartdate());
    		projectRecord.setEnddate(project.getEnddate());
    		projectRecord.setStoreentities(project.getStoreentities());
    		projectRecord.setCreatepseudonyms(project.getCreatepseudonyms());
    		projectRecord.setDescription(project.getDescription());
    		projectRecord.setAssociatedEntitytypeIds(project.getAssociatedEntitytypeIds());
    		
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
		projectRecord.setAbbreviation(updatedProject.getAbbreviation() != null && !updatedProject.getAbbreviation().isBlank() ? updatedProject.getAbbreviation() : oldProject.getAbbreviation());
		projectRecord.setStartdate(updatedProject.getStartDate() != null && !updatedProject.getStartDate().isBlank() ? LocalDate.parse(updatedProject.getStartDate(), dateFormatter) : LocalDate.parse(oldProject.getStartDate(), dateFormatter));
		projectRecord.setEnddate(updatedProject.getEndDate() != null && !updatedProject.getEndDate().isBlank() ? LocalDate.parse(updatedProject.getEndDate(), dateFormatter) : LocalDate.parse(oldProject.getEndDate(), dateFormatter));
		projectRecord.setStoreentities(updatedProject.getStoreEntities() != null ? updatedProject.getStoreEntities() : oldProject.getStoreEntities());
		projectRecord.setCreatepseudonyms(updatedProject.getCreatePseudonyms() != null ? updatedProject.getCreatePseudonyms() : oldProject.getCreatePseudonyms());
		projectRecord.setDescription(updatedProject.getDescription() != null ? updatedProject.getDescription() : oldProject.getDescription());
		projectRecord.setAssociatedEntitytypeIds(updatedProject.getAssociatedEntityTypes() != null && updatedProject.getAssociatedEntityTypes().length != 0 ? getEntityTypeIDs(updatedProject.getAssociatedEntityTypes()) : getEntityTypeIDs(oldProject.getAssociatedEntityTypes()));
        
        // Store and determine success
        int wasStored = projectRecord.update();
        log.debug("Updating the project object \"" + projectRecord.getName() + "\" " + ((wasStored == 1) ? "succeeded." : "failed."));
        
        // Return the project ID when successful
        return wasStored == 1 ? projectRecord.getId() : null;
    }
    
    /**
     * This method retrieves the names for the given entity type IDs from a project.
     * 
     * @param entityTypeIDs the array of entity type IDs stored in a project
     * @return an array of the names of the entity types in the same order as the IDs were given.
     */
    @Transactional
    public String[] getEntityTypeNames(Integer[] entityTypeIDs) {
    	String[] entityTypeNames = new String[entityTypeIDs.length];
    	
    	for (int i = 0; i < entityTypeIDs.length; i++) {
    		String name = null;
    		
    		try {
    			name = dsl.select(ENTITYTYPE.NAME)
	    	                 .from(ENTITYTYPE)
	    	                 .where(ENTITYTYPE.ID.eq(entityTypeIDs[i]))
	    	                 .fetchOneInto(String.class);
    		} catch (TooManyRowsException e) {
            	log.debug("Found more than one project.");
        	} catch (MappingException f) {
            	log.debug("Could not map the search result.");
            } catch (DataAccessException g) {
            	log.debug("Searching for the entity name in the database failed: " + g.getMessage());
            }
    		
    		entityTypeNames[i] = name;
    	}
    	
    	return entityTypeNames;
    }
    
    /**
     * This method retrieves the IDs for the given entity type names from a project.
     * 
     * @param entityTypeNames the array of entity type names stored in a project
     * @return an array of the IDs of the entity types in the same order as the names were given.
     */
    @Transactional
    public Integer[] getEntityTypeIDs(String[] entityTypeNames) {
    	Integer[] entityTypeIDs = new Integer[entityTypeNames.length];
    	
    	for (int i = 0; i < entityTypeNames.length; i++) {
    		Integer id = null;
    		
    		try {
    			id = dsl.select(ENTITYTYPE.ID)
	    	            .from(ENTITYTYPE)
	    	            .where(ENTITYTYPE.NAME.equalIgnoreCase(entityTypeNames[i]))
	    	            .fetchOneInto(Integer.class);
    		} catch (TooManyRowsException e) {
            	log.debug("Found more than one project.");
        	} catch (MappingException f) {
            	log.debug("Could not map the search result.");
            } catch (DataAccessException g) {
            	log.debug("Searching for the entity ID in the database failed: " + g.getMessage());
            }
    		
    		entityTypeIDs[i] = id;
    	}
    	
    	return entityTypeIDs;
    }
}

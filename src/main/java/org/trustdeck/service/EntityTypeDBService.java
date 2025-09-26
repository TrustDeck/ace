/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller
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

import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.MappingException;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trustdeck.dto.EntityTypeDTO;
import org.trustdeck.exception.CreateEntityTypeException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.EntityType;
import org.trustdeck.jooq.generated.tables.records.EntityTypeRecord;
import org.trustdeck.utils.Assertion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import static org.trustdeck.jooq.generated.Tables.ENTITY_TYPE;
import static org.trustdeck.jooq.generated.Tables.ENTITY_INSTANCE;

/**
 * This class encapsulates the database access for entity types.
 * 
 * @author Armin Müller
 */
@Slf4j
@Service
public class EntityTypeDBService {
    
	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
	private DSLContext dsl;
	
	/**
     * Method to insert a new entity type into the database.
     * 
     * @param entityTypeDTO the entity type data transfer object containing the necessary data
     * @param request the http request object containing information necessary for the audit trail 
     * @return The newly inserted entity type object when the insertion was successful,
     * 		   the original entity type object if the given one was a duplicate, and
     * 		   {@code null} when the insertion failed.
     * @throws CreateEntityTypeException when the creation of the entity type or the partition 
     * 		   in the database failed to abort and roll back this transaction.
     */
    @Transactional
    public EntityTypeDTO createEntityType(EntityTypeDTO entityTypeDTO, HttpServletRequest request) throws CreateEntityTypeException {
    	// Create the record and send it to the database
    	EntityTypeRecord createdEntityType;
    	try {
    		createdEntityType = dsl.newRecord(ENTITY_TYPE);
	    	createdEntityType.setName(entityTypeDTO.getName());
	    	createdEntityType.setVersion(entityTypeDTO.getVersion());
	    	createdEntityType.setIsDeprecated(entityTypeDTO.getIsDeprecated());
	    	createdEntityType.setIsBaseType(entityTypeDTO.getIsBaseType());
	    	createdEntityType.setTypeDefinition(entityTypeDTO.getTypeDefinition());
	    	createdEntityType.setProjectId(entityTypeDTO.getProjectID());
	
	        // Store and determine success
	        if (createdEntityType.insert() != 1) {
	        	log.debug("Inserting the entity type failed.");
	        	return null;
	        }
    	
	    	// Create new partition in the database for this type
	    	// jOOQ doesn't model "PARTITION OF" yet, so use plain SQL
	        dsl.query("CREATE TABLE {0} PARTITION OF {1} FOR VALUES IN ({2})", 
	        		DSL.name("entityinstance_t" + createdEntityType.getId()), 
	        		DSL.name("entity_instance"), 
	        		DSL.inline(createdEntityType.getId()))
	        .execute();
	    } catch (DataAccessException e) {
	    	if (e.getMessage().contains(" already exists.")) {
	    		// Found duplicate, return the original
	    		log.info("While creating an entity type, an identical one was found and will be used instead.");
	    		return getEntityType(entityTypeDTO.getName(), entityTypeDTO.getVersion(), entityTypeDTO.getProjectID(), null);
	    	} else {
		    	// Inserting the new entity type into the database failed; throw exception to abort
		    	throw new CreateEntityTypeException(e.getMessage());
	    	}
	    }
	    
	    // Return the entity type
        log.debug("Creating the entity type \"" + entityTypeDTO.getName() + "\" was successful.");
	    return new EntityTypeDTO().assignPojoValues(new EntityType(createdEntityType));
    }

    /**
     * Method to retrieve an entity type from the database by explicitly providing the three variables
     * name, version, and projectID. Triplets of these are unique in the database.
     * Base types will have no projectID, so it can be null.
     * 
     * @param name the entity type's name
     * @param version the entity type's version
     * @param projectID the project to which the entity type is assigned to
     * @param request the http request object containing information necessary for the audit trail
     * @return the retrieved entity type when successfully found, or {@code null} when nothing was found.
     */
    @Transactional
    public EntityTypeDTO getEntityType(String name, String version, Integer projectID, HttpServletRequest request) {
    	// Check if all the necessary arguments are available
    	if (Assertion.isNullOrEmpty(name) || Assertion.isNullOrEmpty(version)) {
    		log.debug("For retrieving the entity type, there is an argument missing or empty.");
    		return null;
    	}
    	
    	// Build and execute the query
    	List<EntityType> entityTypes = null;
    	try {
    		entityTypes = dsl.selectFrom(ENTITY_TYPE)
                .where(ENTITY_TYPE.NAME.equalIgnoreCase(name))
                .and(ENTITY_TYPE.VERSION.equalIgnoreCase(version))
                .and(ENTITY_TYPE.PROJECT_ID.equal(projectID))
                .fetchInto(EntityType.class);
        } catch (MappingException e) {
        	log.debug("Could not map the entity type search result into the EntityType-POJO.", e);
        	return null;
        } catch (DataAccessException f) {
        	log.debug("Searching for the entity type in the database failed.", f);
        	return null;
        }
    	
    	// Check if the search was successful
    	if (entityTypes == null || entityTypes.size() == 0) {
    		log.debug("No entity type was found.");
            return null;
    	} else if (entityTypes.size() > 1) {
        	log.debug("Too many entity types were found.");
        	return null;
        } else if (entityTypes.getFirst().getIsDeprecated()) {
        	log.debug("The entity type is marked as deleted/deprecated.");
        	return null;
        }

        // Create a DTO, populate and return it
        return new EntityTypeDTO().assignPojoValues(entityTypes.getFirst());
    }
    
    /**
     * Method to retrieve an entity type from the database by providing an EntityTypeDTO containing at least
     * name, version, and projectID. Triplets of these are unique in the database.
     * 
     * @param entityTypeDTO the entity type data transfer object containing the necessary data (at least name, version, and projectID)
     * @param request the http request object containing information necessary for the audit trail
     * @return
     */
    @Transactional
    public EntityTypeDTO getEntityType(EntityTypeDTO entityTypeDTO, HttpServletRequest request) {
    	// Check if all the necessary arguments are available
    	if (entityTypeDTO == null) {
    		return null;
    	}
    	
    	return getEntityType(entityTypeDTO.getName(), entityTypeDTO.getVersion(), entityTypeDTO.getProjectID(), request);
    }
    
    /**
     * Method to delete an entity type. Only possible if the entity type is not used anywhere.
     * 
     * @param name the entity type's name
     * @param version the entity type's version
     * @param projectID the project to which the entity type is assigned to
     * @param request the http request object containing information necessary for the audit trail
     * @return {@code true} when deletion was successful, {@code false} when anything went wrong during the deletion
     * @throws UnexpectedResultSizeException when the deletion would have affected an unexpected number of database entries
     */
    @Transactional
    public boolean deleteEntityType(String name, String version, Integer projectID, HttpServletRequest request) throws UnexpectedResultSizeException {
    	// Check if all the necessary arguments are available
    	if (Assertion.isNullOrEmpty(name) || Assertion.isNullOrEmpty(version) || projectID == null) {
    		log.debug("For retrieving the entity type, there is an argument missing or empty.");
    		return false;
    	}
    	
    	// Check if the entity type is used anywhere --> only delete it if it's not used
    	int id = getEntityType(name, version, projectID, null).getId();
    	int usedBy = 0;
    	try {
    		usedBy = dsl.selectCount()
    				.from(ENTITY_INSTANCE)
    				.where(ENTITY_INSTANCE.ENTITY_TYPE_ID.equal(id))
    				.fetchOne(0, int.class);
    	} catch (DataAccessException e) {
    		log.debug("Searching for entity type refrences in the database failed.", e);
    		return false;
    	}
    	
    	if (usedBy != 0) {
    		log.debug("The entity type was referenced by " + usedBy + "entit" + (usedBy == 1 ? "y" : "ies") + " and can therefore not be deleted.");
    		return false;
    	}
    	
    	// The entity type is not used --> perform deletion
    	int deletedEntityTypes = 0;
    	try {
    		// Build and execute the query
    		deletedEntityTypes = dsl.deleteFrom(ENTITY_TYPE)
                .where(ENTITY_TYPE.NAME.equalIgnoreCase(name))
                .and(ENTITY_TYPE.VERSION.equalIgnoreCase(version))
                .and(ENTITY_TYPE.PROJECT_ID.equal(projectID))
                .execute();
        } catch (DataAccessException e) {
        	log.debug("Deleting the entity type in the database failed.", e);
        	return false;
        }
    	
    	// Check if the deletion was successful
    	if (deletedEntityTypes != 1) {
    		// An unexpected number of records was affected. Log it and abort by throwing
            // an exception (which will rollback everything from the transaction).
            log.error("Couldn't delete the entity type \"" + name + "\" from the database.");
            throw new UnexpectedResultSizeException(1, deletedEntityTypes);
    	}
    	
    	// If we reach this point, the deletion was successful
    	return true;
    }
    
    /**
     * Method to update an entity type. Only works when the entity type is not used anywhere.
     * 
     * @param oldEntityTypeDTO the entity type data transfer object containing the necessary data to identify the type that should be updated
     * @param newEntityTypeDTO the entity type object containing the data to use for the update; null-values lead to keeping the old values
     * @param request the http request object containing information necessary for the audit trail
     * @return the updated entity type object when successful, {@code null} when anything went wrong
     */
    @Transactional
    public EntityTypeDTO updateEntityType(EntityTypeDTO oldEntityTypeDTO, EntityTypeDTO newEntityTypeDTO, HttpServletRequest request) {
    	// Ensure that all necessary information is given --> retrieve the database version of the old record
    	EntityTypeDTO oldType = getEntityType(oldEntityTypeDTO, null);
    	
    	if (oldType == null) {
    		return null;
    	}
    	
    	// Check if the entity type is used anywhere --> only update it if it's not used
    	int usedBy = 0;
    	try {
    		usedBy = dsl.selectCount()
    				.from(ENTITY_INSTANCE)
    				.where(ENTITY_INSTANCE.ENTITY_TYPE_ID.equal(oldType.getId()))
    				.fetchOne(0, int.class);
    	} catch (DataAccessException e) {
    		log.debug("Searching for entity type references in the database failed.", e);
    		return null;
    	}
    	
    	if (usedBy != 0) {
    		log.debug("The entity type was referenced by " + usedBy + "entit" + (usedBy == 1 ? "y" : "ies") + " and can therefore not be updated.");
    		return null;
    	}
    	
    	// Decide on which values to use
    	String name = Assertion.isNotNullOrEmpty(newEntityTypeDTO.getName()) ? newEntityTypeDTO.getName() : oldType.getName();
        String version = Assertion.isNotNullOrEmpty(newEntityTypeDTO.getVersion()) ? newEntityTypeDTO.getVersion() : oldType.getVersion();
        Boolean isBaseType = newEntityTypeDTO.getIsBaseType() != null ? newEntityTypeDTO.getIsBaseType() : oldType.getIsBaseType();
        JSONB typeDef = newEntityTypeDTO.getTypeDefinition() != null ? newEntityTypeDTO.getTypeDefinition() : oldType.getTypeDefinition();
        Integer projectId = (newEntityTypeDTO.getProjectID() != null && newEntityTypeDTO.getProjectID() > 0) ? newEntityTypeDTO.getProjectID() : oldType.getProjectID();
    	
    	// Create the update-record and send it to the database
        EntityTypeRecord updatedRecord = null;
    	try {
    		// Update and return the updated record
    		updatedRecord = dsl.update(ENTITY_TYPE)
	                .set(ENTITY_TYPE.NAME, name)
	                .set(ENTITY_TYPE.VERSION, version)
	                .set(ENTITY_TYPE.IS_BASE_TYPE, isBaseType)
	                .set(ENTITY_TYPE.TYPE_DEFINITION, typeDef)
	                .set(ENTITY_TYPE.PROJECT_ID, projectId)
	                .where(ENTITY_TYPE.ID.eq(oldType.getId()))
	                .returning()
	                .fetchOne();
	    } catch (DataAccessException e) {
	    	log.error("Updating the entity type failed.", e);
	    	return null;
	    }
	    
	    // Return the entity type
        log.debug("Updating the entity type \"" + updatedRecord.getName() + "\" was successful.");
	    return new EntityTypeDTO().assignPojoValues(new EntityType(updatedRecord));
    }
    
    /**
     * Method to search for entity types.
     * This search supports full-text-searching over all attributes of an entity type,
     * as well as multiple words.
     * 
     * @param query the (multi-word) search query
     * @param request the http request object containing information necessary for the audit trail
     * @return a list of entity types that match the search query
     */
    @Transactional
    public List<EntityTypeDTO> searchEntityType(String query, HttpServletRequest request) {
    	if (Assertion.isNullOrEmpty(query)) {
            log.debug("Search query is empty.");
            return null;
        }

        // Split query-parts on whitespace; every part should match at least one column
        String[] parts = query.trim().split("\\s+");
        
        // Built the search conditions statement
        Condition condition = DSL.trueCondition();
        for (String part : parts) {
            String pattern = "%" + part + "%";

            // Search across columns
            Condition partCond = ENTITY_TYPE.NAME.likeIgnoreCase(pattern)
                .or(ENTITY_TYPE.VERSION.likeIgnoreCase(pattern))
                // Cast non-text columns to text for LIKE matching
                .or(DSL.cast(ENTITY_TYPE.ID, String.class).likeIgnoreCase(pattern))
                .or(DSL.cast(ENTITY_TYPE.PROJECT_ID, String.class).likeIgnoreCase(pattern))
                .or(DSL.cast(ENTITY_TYPE.IS_BASE_TYPE, String.class).likeIgnoreCase(pattern))
                // JSONB via full text search on the tsvector (uses the GIN index on the tsvector named 'full_text_search_vector')
                .or(DSL.condition("full_text_search_vector @@ plainto_tsquery('simple', {0})", DSL.val(part)));

            // AND-connect all search parts
            condition = condition.and(partCond);
        }

        // Exclude deprecated/deleted types
        condition = condition.and(ENTITY_TYPE.IS_DEPRECATED.eq(false));

        // Execute the search
        List<EntityType> results;
        try {
            results = dsl.selectFrom(ENTITY_TYPE)
                      .where(condition)
                      .orderBy(ENTITY_TYPE.NAME.asc(), ENTITY_TYPE.VERSION.asc())
                      .fetchInto(EntityType.class);
        } catch (MappingException e) {
            log.debug("Could not map entity type search result.", e);
            return null;
        } catch (DataAccessException e) {
            log.debug("Searching entity types failed.", e);
            return null;
        }

        // Evaluate the search results
        if (results == null || results.isEmpty()) {
            log.debug("No entity type matched the query \"" + query + "\".");
            return null;
        }

        // Return the found types
        return results.stream().map(row -> new EntityTypeDTO().assignPojoValues(row)).toList();
    }
}

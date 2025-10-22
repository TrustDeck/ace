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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.MappingException;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trustdeck.dto.EntityInstanceDTO;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.EntityInstance;
import org.trustdeck.jooq.generated.tables.records.EntityInstanceRecord;
import org.trustdeck.utils.Assertion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import static org.trustdeck.jooq.generated.Tables.ENTITY_INSTANCE;

/**
 * This class encapsulates the database access for entity instances.
 * 
 * @author Armin Müller
 */
@Slf4j
@Service
public class EntityInstanceDBService {
    
	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
	private DSLContext dsl;
	
	/**
     * Method to insert a new entity instance into the database.
     * 
     * @param entityInstanceDTO the entity instance data transfer object containing the necessary data
     * @param request the http request object containing information necessary for the audit trail 
     * @return The newly inserted entity instance object when the insertion was successful,
     * 		   the original entity instance object if the given one was a duplicate, and
     * 		   {@code null} when the insertion failed.
     */
    @Transactional
    public EntityInstanceDTO createEntityInstance(EntityInstanceDTO entityInstanceDTO, HttpServletRequest request) {
    	// Create the insert statement and execute it
    	EntityInstanceRecord createdEntityInstance;
    	try {
    		// Let DB defaults generate trustdeck_id, created_at, updated_at, is_deleted if null
    		createdEntityInstance = dsl.insertInto(ENTITY_INSTANCE)
    				// The UUID will be automatically generated
	    			.set(ENTITY_INSTANCE.PROJECT_ID, entityInstanceDTO.getProjectID())
	                .set(ENTITY_INSTANCE.ENTITY_TYPE_ID, entityInstanceDTO.getEntityTypeID())
	                .set(ENTITY_INSTANCE.DATA, entityInstanceDTO.getData())
	                // The attributes is_deleted, created_at, and updated_at will be automatically set by the DB using defaults
	                .returning()
	                .fetchOne();

	        // Determine success
	        if (createdEntityInstance == null) {
	        	log.debug("Inserting the entity instance failed.");
	        	return null;
	        }
	    } catch (DataAccessException e) {
	    	log.error("Inserting the new entity type into the database failed.", e);
		    return null;
	    }
	    
	    // Return the entity type
        log.trace("Creating the entity instance \"" + createdEntityInstance.getTrustdeckId() + "\" was successful.");
	    return new EntityInstanceDTO().assignPojoValues(new EntityInstance(createdEntityInstance));
    }

    /**
     * Method to retrieve an entity instance from the database by explicitly providing the 
     * trustDeckID, which is unique in the database.
     * 
     * @param trustDeckID the entity instance's publicly accessible TrustDeck ID
     * @param request the http request object containing information necessary for the audit trail
     * @return the retrieved entity instance when successfully found, or {@code null} when nothing was found.
     */
    @Transactional
    public EntityInstanceDTO getEntityInstance(UUID trustDeckID, HttpServletRequest request) {
    	// Check if all the necessary arguments are available
    	if (trustDeckID == null) {
    		log.debug("Could not retrieve the entity instance, because the TrustDeckID is missing or empty.");
    		return null;
    	}
    	
    	// Build and execute the query
    	EntityInstance instance = null;
    	try {
    		instance = dsl.selectFrom(ENTITY_INSTANCE)
                .where(ENTITY_INSTANCE.TRUSTDECK_ID.equal(trustDeckID))
                .and(ENTITY_INSTANCE.IS_DELETED.equal(false))
                .fetchOneInto(EntityInstance.class);
        } catch (MappingException e) {
        	log.debug("Could not map the entity instance search result into the EntityInstance-POJO.", e);
        	return null;
        } catch (DataAccessException f) {
        	log.debug("Searching for the entity instance in the database failed.", f);
        	return null;
        }
    	
    	// Check if the search was successful
    	if (instance == null) {
    		log.debug("No entity instance was found.");
            return null;
    	}

        // Create a DTO, populate and return it
        return new EntityInstanceDTO().assignPojoValues(instance);
    }
    
    /**
     * Method to retrieve an entity instance from the database by providing an EntityInstanceDTO. 
     * Internally it uses only the trustDeckID, which is unique in the database.
     * 
     * @param entityInstance the DTO containing at least the instance's publicly accessible TrustDeck ID
     * @param request the http request object containing information necessary for the audit trail
     * @return the retrieved entity instance when successfully found, or {@code null} when nothing was found.
     */
    @Transactional
    public EntityInstanceDTO getEntityInstance(EntityInstanceDTO entityInstance, HttpServletRequest request) {
    	// Check if all the necessary arguments are available
    	if (entityInstance == null || entityInstance.getTrustdeckID() == null) {
    		log.debug("Could not retrieve the entity instance, because an argument is missing or empty.");
    		return null;
    	}
    	
    	return getEntityInstance(entityInstance.getTrustdeckID(), request);
    }
    
    /**
     * Method to retrieve an entity instance from the database by explicitly providing the 
     * trustDeckID as a String, which is unique in the database.
     * 
     * @param trustDeckID the entity instance's publicly accessible TrustDeck ID as a String
     * @param request the http request object containing information necessary for the audit trail
     * @return the retrieved entity instance when successfully found, or {@code null} when nothing was found.
     */
    @Transactional
    public EntityInstanceDTO getEntityInstance(String trustDeckID, HttpServletRequest request) {
    	// Check if all the necessary arguments are available
    	if (Assertion.isNotNullOrEmpty(trustDeckID)) {
    		log.debug("Could not retrieve the entity instance, because to the TrustDeckID is missing or empty.");
    		return null;
    	}
    	
    	// Try parsing the string as a UUID object
    	UUID tdid = null;
    	try {
    		tdid = UUID.fromString(trustDeckID);
    	} catch (IllegalArgumentException e) {
			log.debug("Could not transform the given String into the TrustDeckID-UUID-format.", e);
			return null;
		}
    	
    	return getEntityInstance(tdid, request);
    }
    
    /**
     * Method to delete an entity instance.
     * The deletion is done by marking the entry as deleted and not
     * by actually removing the record from the database.
     * 
     * @param trustDeckID the entity instance's publicly accessible TrustDeck ID
     * @param request the http request object containing information necessary for the audit trail
     * @return {@code true} when deletion was successful, {@code false} when anything went wrong during the deletion
     * @throws UnexpectedResultSizeException when the deletion would have affected an unexpected number of database entries
     */
    @Transactional
    public boolean deleteEntityInstance(UUID trustDeckID, HttpServletRequest request) throws UnexpectedResultSizeException {
    	// Check if all the necessary arguments are available
    	if (trustDeckID == null) {
    		log.debug("For retrieving the entity instance, there is an argument missing or empty.");
    		return false;
    	}
    	
    	// Perform deletion by updating the is_deleted flag
    	int deletedEntityInstances = 0;
    	try {
    		// Build and execute the query
    		deletedEntityInstances = dsl.update(ENTITY_INSTANCE)
	                .set(ENTITY_INSTANCE.IS_DELETED, true)
	                .set(ENTITY_INSTANCE.UPDATED_AT, OffsetDateTime.now())
	                .where(ENTITY_INSTANCE.TRUSTDECK_ID.eq(trustDeckID))
	                .execute();
        } catch (DataAccessException e) {
        	log.debug("Deleting the entity instance in the database failed.", e);
        	return false;
        }
    	
    	// Check if the deletion was successful
    	if (deletedEntityInstances != 1) {
    		// An unexpected number of records was affected. Log it and abort by throwing
            // an exception (which will rollback everything from the transaction).
    		log.error("Too many records would have been affected by the deletion, which was therefore aborted and rolled back.");
        	throw new UnexpectedResultSizeException(1, deletedEntityInstances);
    	}
    	
    	// If we reach this point, the deletion was successful
    	return true;
    }
    
    /**
     * Method to update an entity instance.
     * 
     * @param oldInstanceID the entity instance database id that is needed to identify the instance that should be updated
     * @param newEntityInstanceDTO the entity instance object containing the data to use for the update
     * @param request the http request object containing information necessary for the audit trail
     * @return the updated entity instance object when successful, {@code null} when anything went wrong
     */
    @Transactional
    public EntityInstanceDTO updateEntityInstance(long oldInstanceID, EntityInstanceDTO newEntityInstanceDTO, HttpServletRequest request) {
    	// Create the update-record and send it to the database
        EntityInstanceRecord updatedRecord = null;
    	try {
    		// Update and return the updated record (as long as it's not already deleted)
    		updatedRecord = dsl.update(ENTITY_INSTANCE)
	                .set(ENTITY_INSTANCE.TRUSTDECK_ID, newEntityInstanceDTO.getTrustdeckID())
	                .set(ENTITY_INSTANCE.PROJECT_ID, newEntityInstanceDTO.getProjectID())
	                .set(ENTITY_INSTANCE.ENTITY_TYPE_ID, newEntityInstanceDTO.getEntityTypeID())
	                .set(ENTITY_INSTANCE.DATA, newEntityInstanceDTO.getData())
	                .set(ENTITY_INSTANCE.IS_DELETED, newEntityInstanceDTO.getIsDeleted())
	                .set(ENTITY_INSTANCE.CREATED_AT, newEntityInstanceDTO.getCreatedAt())
	                .set(ENTITY_INSTANCE.UPDATED_AT, newEntityInstanceDTO.getUpdatedAt())
	                .where(ENTITY_INSTANCE.ID.eq(oldInstanceID))
	                .and(ENTITY_INSTANCE.IS_DELETED.ne(true))
	                .returning()
	                .fetchOne();
    	} catch (DataAccessException e) {
	    	log.error("Updating the entity instance failed.", e);
	    	return null;
	    }
	    
	    // Return the updated entity instance
        log.debug("Updating the entity instance \"" + updatedRecord.getTrustdeckId() + "\" was successful.");
	    return new EntityInstanceDTO().assignPojoValues(new EntityInstance(updatedRecord));
    }
    
    /**
     * Method to search for entity instance.
     * This search supports full-text-searching over all attributes of an entity instance,
     * as well as multiple words.
     * 
     * @param query the (multi-word) search query
     * @param request the http request object containing information necessary for the audit trail
     * @return a list of entity instances that match the search query
     */
    @Transactional
    public List<EntityInstanceDTO> searchEntityInstance(String query, HttpServletRequest request) {
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

            // Search across columns; cast non-text columns to text for LIKE matching
            Condition partCond = DSL.cast(ENTITY_INSTANCE.TRUSTDECK_ID, String.class).likeIgnoreCase(pattern)
                .or(DSL.cast(ENTITY_INSTANCE.PROJECT_ID, String.class).likeIgnoreCase(pattern))
                .or(DSL.cast(ENTITY_INSTANCE.ENTITY_TYPE_ID, String.class).likeIgnoreCase(pattern))
                // JSONB via full text search on the tsvector (uses the GIN index on the tsvector named 'full_text_search_vector')
                .or(DSL.condition("full_text_search_vector @@ plainto_tsquery('simple', {0})", DSL.val(part)))
                .or(DSL.cast(ENTITY_INSTANCE.CREATED_AT, String.class).likeIgnoreCase(pattern))
                .or(DSL.cast(ENTITY_INSTANCE.UPDATED_AT, String.class).likeIgnoreCase(pattern));

            // AND-connect all search parts
            condition = condition.and(partCond);
        }

        // Exclude deleted instances
        condition = condition.and(ENTITY_INSTANCE.IS_DELETED.eq(false));

        // Execute the search
        List<EntityInstance> results;
        try {
            results = dsl.selectFrom(ENTITY_INSTANCE)
                      .where(condition)
                      .fetchInto(EntityInstance.class);
        } catch (MappingException e) {
            log.debug("Could not map entity instance search result.", e);
            return null;
        } catch (DataAccessException f) {
            log.debug("Searching entity instances failed.", f);
            return null;
        }

        // Evaluate the search results
        if (results == null || results.isEmpty()) {
            log.debug("No entity instance matched the query \"" + query + "\".");
            return null;
        }

        // Return the found types
        return results.stream().map(row -> new EntityInstanceDTO().assignPojoValues(row)).toList();
    }
}

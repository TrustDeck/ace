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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.MappingException;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trustdeck.dto.AlgorithmDTO;
import org.trustdeck.dto.PersonDTO;
import org.trustdeck.exception.DuplicatePersonException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.exception.UpdateException;
import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
import org.trustdeck.jooq.generated.tables.pojos.Person;
import org.trustdeck.jooq.generated.tables.records.PersonRecord;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import static org.trustdeck.jooq.generated.Tables.PERSON;

/**
 * This class encapsulates the database access for person objects.
 * 
 * @author Armin Müller
 */
@Slf4j
@Service
public class PersonDBService {
    
	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
	private DSLContext dsl;
    
	/** Enables the access to the algorithm specific database access methods. */
    @Autowired
	private AlgorithmDBService algorithmDBService;
	
	/**
     * Method to insert a new person into the database table.
     * 
     * @param person the person object containing the necessary information
     * @param algorithm the algorithm object containing information about how the person identifier was generated
     * @param request the http request object containing information necessary for the audit trail
     * @throws DuplicatePersonException when the identifier & idType combination are already in the database
     * @return The inserted person object including the ID when the insertion was successful,
     * 		   {@code null} when the insertion failed or would create a duplicate.
     */
    @Transactional
    public Person createPerson(Person person, Algorithm algorithm, HttpServletRequest request) throws DuplicatePersonException {
    	// Retrieve the proper ID for the algorithm
    	Integer algorithmID = algorithm.getId() != null && algorithm.getId() > 0 ? algorithm.getId() : algorithmDBService.createOrGetAlgorithm(algorithm);
    	
    	// Check if an ID was found or created
    	if (algorithmID == null) {
    		log.warn("Retrieving or creating the algorithm object for this person object failed! The person object will have no algorithm assigned.");
    	}
	    
	    try {
	    	// Insert the person record with all fields
	        PersonRecord personRecord = dsl.newRecord(PERSON);
	        personRecord.setFirstname(person.getFirstname());
	        personRecord.setLastname(person.getLastname());
	        personRecord.setBirthname(person.getBirthname());
	        personRecord.setAdministrativegender(person.getAdministrativegender());
	        personRecord.setDateofbirth(person.getDateofbirth());
	        personRecord.setStreet(person.getStreet());
	        personRecord.setPostalcode(person.getPostalcode());
	        personRecord.setCity(person.getCity());
	        personRecord.setCountry(person.getCountry());
	        personRecord.setIdentifier(person.getIdentifier());
	        personRecord.setIdtype(person.getIdtype());
	        personRecord.setIdentifieralgorithm(algorithmID);
	
	        // Store and determine success
	        if(personRecord.insert() == 0) {
	        	log.debug("Inserting the person with identifier \"" + personRecord.getIdentifier() + "\" failed due to an equal record already being in the database.");
	        	return null;
	        }
	        
	        // Update person object after successful insertion
	        person.setIdentifier(personRecord.getIdentifier());
	        person.setIdentifieralgorithm(algorithmID);
	    } catch (DataAccessException e) {
	    	if (e.getMessage().contains(" already exists.")) {
	    		// Found duplicate
	    		log.debug("Insertion of the person object failed due to being a duplicate.");
	    		throw new DuplicatePersonException(person.getIdentifier(), person.getIdtype());
	    	}
	    	
	    	log.error("Inserting the new person object into the database failed: " + e.getMessage());
	    	return null;
	    }
	    
	    // Return the person object
        log.debug("Creating the person with identifier \"" + person.getIdentifier() + "\" was successful.");
	    return person;
    }

    /**
     * This method retrieves a person by its identifier and idType.
     * 
     * @param identifier the identifier of the person you want to retrieve
     * @param idType the idType of the person you want to retrieve
     * @param request the http request object containing information necessary for the audit trail
     * @return the person object or {@code null} if nothing was found
     */
    @Transactional
    public PersonDTO getPersonByIdentifier(String identifier, String idType, HttpServletRequest request) {
    	// Build and execute the query
    	List<Person> persons = null;
    	try {
        	persons = dsl.selectFrom(PERSON)
                .where(PERSON.IDENTIFIER.equal(identifier))
                .and(PERSON.IDTYPE.equal(idType))
                .fetchInto(Person.class);
        } catch (MappingException e) {
        	log.debug("Could not map the person search result into the Person-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for the person in the database failed: " + f.getMessage());
        }
    	
    	// Check if the search was successful
    	if (persons == null || persons.size() == 0) {
    		log.debug("No person for identifer \"" + identifier + "\" and idType \"" + idType + "\" found.");
            return null;
    	} else if (persons.size() > 1) {
        	log.debug("More than one person object was found. The first result will be used.");
        }

        // Create a PersonDTO, populate and return it
        return new PersonDTO().assignPojoValues(persons.getFirst());
    }
    
    /**
     * This method retrieves a person by its names.
     * 
     * @param firstName the first name of the person to search for
     * @param lastName the last name of the person to search for
     * @param birthName the birth name of the person to search for (e.g. after marriage)
     * @param request the http request object containing information necessary for the audit trail
     * @return the person object or {@code null} if nothing was found
     */
    @Transactional
    public PersonDTO getPersonByName(String firstName, String lastName, String birthName, HttpServletRequest request) {
    	// Build the query based on the given non-null attributes
    	Condition condition = DSL.trueCondition();
        if (firstName != null) {
            condition = condition.and(PERSON.FIRSTNAME.equalIgnoreCase(firstName));
        }
        if (lastName != null) {
            condition = condition.and(PERSON.LASTNAME.equalIgnoreCase(lastName));
        }
        if (birthName != null) {
            condition = condition.and(PERSON.BIRTHNAME.equalIgnoreCase(birthName));
        }

        List<Person> persons = null;
        try {
	        // Execute the query
	        persons = dsl.selectFrom(PERSON)
	                  .where(condition)
	                  .fetchInto(Person.class);
        } catch (MappingException e) {
        	log.debug("Could not map the person search result into the Person-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for the person in the database failed: " + f.getMessage());
        }
    	
        // Check if the search was successful
    	if (persons == null || persons.size() == 0) {
    		log.debug("No person with first name \"" + firstName + "\", last name \"" + lastName + "\", and birth name \"" + birthName + "\" found.");
            return null;
    	} else if (persons.size() > 1) {
        	log.debug("More than one person object was found. The first result will be used.");
        }

        // Create a PersonDTO, populate and return it
        return new PersonDTO().assignPojoValues(persons.getFirst());
    }

    /**
     * Delete a person from the database using its identifier and idType.
     * Also deletes orphaned algorithm objects.
     * 
     * @param identifier the identifier of the person that should be deleted
     * @param idType the idType of the person that should be deleted
     * @param request the http request object containing information necessary for the audit trail
     * @return {@code true} when the deletion was successful, {@code false} when the person object 
     * 			that should be deleted was not found
     * @throws UnexpectedResultSizeException whenever the deletion would not exactly affect one person entry
     */
    @Transactional
    public boolean deletePerson(String identifier, String idType, HttpServletRequest request) throws UnexpectedResultSizeException {
    	// Fetch the person to check if it exist and to get the algorithm ID before deletion
    	PersonDTO person = getPersonByIdentifier(identifier, idType, null);
        if (person != null) {
        	// Delete person object
        	int deletedRecords = dsl.deleteFrom(PERSON)
               .where(PERSON.IDENTIFIER.equalIgnoreCase(identifier))
               .and(PERSON.IDTYPE.equalIgnoreCase(idType))
               .execute();
        	
        	// Determine success
            if (deletedRecords > 1) {
                log.debug("Deleting the person with identifier \"" + person.getIdentifier() + "\" and idType \"" + idType + "\" would not affect exactly one entry. Aborting.");
                throw new UnexpectedResultSizeException(1, deletedRecords);
            } else if (deletedRecords == 0) {
            	log.debug("Nothing found to delete.");
            	return false;
            }
            
            // At this point, the person object was successfully deleted
            log.debug("Successfully removed the person object. Checking for orphaned algorithm.");
            
            // Now, also delete the algorithm object if it's orphaned
            boolean deletedAlgo = false;
            try {
            	deletedAlgo = algorithmDBService.deleteAlgorithm(person.getAlgorithm().getId());
            } catch (UnexpectedResultSizeException e) {
            	log.debug("Deleting the algorithm would affect other algorithm objects, so it will not be deleted.");
            	
            	// Re-throw the exception to break the transaction
            	throw e;
            }
            
            // If the deletion of the algorithm object failed, then probably because it is not orphaned, which is totally fine
            if (!deletedAlgo) {
            	log.debug("While deleting the person object with identifier \"" + person.getIdentifier() + "\": the algorithm object was not deleted.");
            } else {
            	log.debug("Successfully deleted the orphaned algorithm.");
            }
            
            return true;
        }
        
        // At this point, no person with the given identifier and idType was found
        log.debug("Nothing to delete.");
        return false;
    }

    /**
     * Query for persons by searching in the person-attributes.
     * 
     * @param query the string to search for
     * @param request the http request object containing information necessary for the audit trail
     * @return a list of persons that were found, or {@code null} if nothing was found
     */
    @Transactional
    public List<Person> searchPersons(String query, HttpServletRequest request) {
        List<Person> persons = null;
        try {
        	persons = dsl.selectFrom(PERSON)
        	    .where(PERSON.LASTNAME.likeIgnoreCase("%" + query + "%")
        	        .or(PERSON.FIRSTNAME.likeIgnoreCase("%" + query + "%"))
        	        .or(PERSON.BIRTHNAME.likeIgnoreCase("%" + query + "%"))
        	        .or(DSL.toChar(PERSON.DATEOFBIRTH, "YYYY-MM-DD").likeIgnoreCase("%" + query + "%")))
        	    	.or(PERSON.STREET.likeIgnoreCase("%" + query + "%"))
        	    	.or(PERSON.POSTALCODE.likeIgnoreCase("%" + query + "%"))
        	    	.or(PERSON.CITY.likeIgnoreCase("%" + query + "%"))
        	    	.or(PERSON.COUNTRY.likeIgnoreCase("%" + query + "%"))
        	    	.or(PERSON.IDENTIFIER.likeIgnoreCase("%" + query + "%"))
        	    	.or(PERSON.IDTYPE.likeIgnoreCase("%" + query + "%"))
        	    .fetchInto(Person.class);
        } catch (MappingException e) {
        	log.debug("Could not map the person search result into the Person-POJO.");
        } catch (DataAccessException f) {
        	log.debug("Searching for persons in the database failed: " + f.getMessage());
        }
        
        // If nothing was found, return null
        if (persons == null || persons.isEmpty()) {
        	log.debug("No person for the query \"" + query + "\" found.");
            return null;
        }
        
        return persons;
    }

    /**
     * This method updates a person identified by its identifier and idType.
     * 
     * @param identifier the identifier of the person that should be updated
     * @param idType the idType of the person that should be updated
     * @param updatedPerson the person object containing the updated information
     * @param request the http request object containing information necessary for the audit trail
     * @return the ID of the updated person, or {@code null} if an error occurred
     */
    @Transactional
    public Integer updatePerson(String identifier, String idType, PersonDTO updatedPerson, HttpServletRequest request) {
    	DateTimeFormatter dobFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    	
    	// Get data needed for the update
    	AlgorithmDTO updatedAlgo = updatedPerson.getAlgorithm();
    	PersonDTO oldPerson = getPersonByIdentifier(identifier, idType, null);
    	AlgorithmDTO oldAlgo = oldPerson.getAlgorithm();
    	Integer newAlgoID = null;
        
    	// Check how many person objects are using this algorithm
    	if (algorithmDBService.getNumberOfPersonsUsingAlgorithm(oldAlgo.getId()) > 1) {
    		// The algorithm is also used by other Persons
    		
    		// Check if the algorithm changed in the updated object
    		if (!oldAlgo.equals(updatedAlgo)) {
    			// The algorithm object changed, create a new algorithm object from the old one 
    			// and then update the newly created one, so other referencing person objects aren't affected
    			Algorithm newAlgo = new Algorithm();
    			
    			newAlgo.setName(updatedAlgo.getName() == null || updatedAlgo.getName().isBlank() ? oldAlgo.getName() : updatedAlgo.getName());
    			newAlgo.setAlphabet(updatedAlgo.getAlphabet() == null || updatedAlgo.getAlphabet().isBlank() ? oldAlgo.getAlphabet() : updatedAlgo.getAlphabet());
    			newAlgo.setRandomalgorithmdesiredsize(updatedAlgo.getRandomAlgorithmDesiredSize() < 1 ? oldAlgo.getRandomAlgorithmDesiredSize() : updatedAlgo.getRandomAlgorithmDesiredSize());
    			newAlgo.setRandomalgorithmdesiredsuccessprobability(updatedAlgo.getRandomAlgorithmDesiredSuccessProbability() <= 0 ? oldAlgo.getRandomAlgorithmDesiredSuccessProbability() : updatedAlgo.getRandomAlgorithmDesiredSuccessProbability());
    			newAlgo.setConsecutivevaluecounter(updatedAlgo.getConsecutiveValueCounter() < 1 ? oldAlgo.getConsecutiveValueCounter() : updatedAlgo.getConsecutiveValueCounter());
    			newAlgo.setPseudonymlength(updatedAlgo.getPseudonymLength() < 1 ? oldAlgo.getPseudonymLength() : updatedAlgo.getPseudonymLength());
    			newAlgo.setPaddingcharacter(updatedAlgo.getPaddingCharacter() == null || updatedAlgo.getPaddingCharacter().isBlank() ? oldAlgo.getPaddingCharacter() : updatedAlgo.getPaddingCharacter());
    			newAlgo.setAddcheckdigit(updatedAlgo.isAddCheckDigit());
    			newAlgo.setLengthincludescheckdigit(updatedAlgo.isLengthIncludesCheckDigit());
    			newAlgo.setSalt(updatedAlgo.getSalt() == null || updatedAlgo.getSalt().isBlank() ? oldAlgo.getSalt() : updatedAlgo.getSalt());
    			newAlgo.setSaltlength(updatedAlgo.getSaltLength() == 0 ? oldAlgo.getSaltLength() : updatedAlgo.getSaltLength());
    			
    			// Create new Algorithm object
    			newAlgoID = algorithmDBService.createAlgorithm(newAlgo);
    			
    			// Check if the insertion was successful
    			if (newAlgoID == null) {
    				log.debug("Creating a new algorithm object in the database failed. Aborting update.");
    				throw new UpdateException(newAlgo);
    			}
    		} else {
    			// The algorithm object did not change, so no need to update it
    			newAlgoID = oldAlgo.getId();
    		}
    	} else {
    		// The algorithm object is only used by one person object, does it need to be updated?
    		if (!oldAlgo.equals(updatedAlgo)) {
    			// The algorithm object changed, update it
    			newAlgoID = algorithmDBService.updateAlgorithm(oldAlgo.convertToPOJO(), updatedAlgo.convertToPOJO());
    			
    			// Check if the update was successful
    			if (newAlgoID == null) {
    				log.debug("Updating the algorithm object in the database failed. Aborting update.");
    				throw new UpdateException(updatedAlgo.convertToPOJO());
    			}
    		} else {
    			// The algorithm object did not change, so no need to update it
    			newAlgoID = oldAlgo.getId();
    		}
    	}
    	    		
    	// Update the person object
    	// Fetch old person record
    	PersonRecord personRecord = null;
    	try {
    		personRecord = dsl.fetchOne(PERSON, PERSON.ID.eq(oldPerson.getId()));
		} catch (DataAccessException e) {
			log.debug("Fetching the person record that should be updated failed (ID: " + oldPerson.getId() + ").");
			return null;
		}
		
		// Check if the old record was found
		if (personRecord == null) {
			log.debug("The person object that should be updated was not found (ID: " + oldPerson.getId() + ").");
			return null;
		}
		
		// Sanitize the given values and update the attributes
        personRecord.setFirstname(updatedPerson.getFirstName() != null && !updatedPerson.getFirstName().isBlank() ? updatedPerson.getFirstName() : oldPerson.getFirstName());
        personRecord.setLastname(updatedPerson.getLastName() != null && !updatedPerson.getLastName().isBlank() ? updatedPerson.getLastName() : oldPerson.getLastName());
        personRecord.setBirthname(updatedPerson.getBirthName() != null && !updatedPerson.getBirthName().isBlank() ? updatedPerson.getBirthName() : oldPerson.getBirthName());
        personRecord.setAdministrativegender(updatedPerson.getAdministrativeGender() != null && !updatedPerson.getAdministrativeGender().isBlank() ? updatedPerson.getAdministrativeGender() : oldPerson.getAdministrativeGender());
        personRecord.setDateofbirth(updatedPerson.getDateOfBirth() != null && !updatedPerson.getDateOfBirth().isBlank() ? LocalDate.parse(updatedPerson.getDateOfBirth(), dobFormatter) : LocalDate.parse(oldPerson.getDateOfBirth(), dobFormatter));
        personRecord.setStreet(updatedPerson.getStreet() != null && !updatedPerson.getStreet().isBlank() ? updatedPerson.getStreet() : oldPerson.getStreet());
        personRecord.setPostalcode(updatedPerson.getPostalCode() != null && !updatedPerson.getPostalCode().isBlank() ? updatedPerson.getPostalCode() : oldPerson.getPostalCode());
        personRecord.setCity(updatedPerson.getCity() != null && !updatedPerson.getCity().isBlank() ? updatedPerson.getCity() : oldPerson.getCity());
        personRecord.setCountry(updatedPerson.getCountry() != null && !updatedPerson.getCountry().isBlank() ? updatedPerson.getCountry() : oldPerson.getCountry());
        personRecord.setIdentifier(updatedPerson.getIdentifier() != null && !updatedPerson.getIdentifier().isBlank() ? updatedPerson.getIdentifier() : oldPerson.getIdentifier());
        personRecord.setIdtype(updatedPerson.getIdType() != null && !updatedPerson.getIdType().isBlank() ? updatedPerson.getIdType() : oldPerson.getIdType());
        personRecord.setIdentifieralgorithm(newAlgoID);
        
        // Store and determine success
        int wasStored = personRecord.update();
        log.debug("Updating the person object \"" + personRecord.getIdentifier() + "\" " + ((wasStored == 1) ? "succeeded." : "failed."));
        
        // Return the algorithm ID when successful
        return wasStored == 1 ? personRecord.getId() : null;
    }
}

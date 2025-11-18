/*
 * Trust Deck Services
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
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

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.jooq.DSLContext;
import org.jooq.DeleteConditionStep;
import org.jooq.Loader;
import org.jooq.UpdateConditionStep;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.TooManyRowsException;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trustdeck.dto.PseudonymDTO;
import org.trustdeck.exception.DomainNotFoundException;
import org.trustdeck.exception.DuplicateIdentifierException;
import org.trustdeck.exception.DuplicatePseudonymException;
import org.trustdeck.exception.PseudonymNotFoundException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.jooq.generated.tables.pojos.Pseudonym;
import org.trustdeck.jooq.generated.tables.records.PseudonymRecord;
import org.trustdeck.model.IdentifierItem;
import org.trustdeck.utils.Assertion;

import static org.trustdeck.jooq.generated.Tables.PSEUDONYM;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to encapsulate all methods needed to access the database for handling pseudonym-records.
 *
 * @author Armin Müller & Eric Wündisch
 */
@Service
@Slf4j
public class PseudonymDBAccessService {

    /** Enables the access to the domain specific database access methods. */
    @Autowired
    private DomainDBAccessService domainDBAccessService;

    /** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
    private DSLContext dslCtx;

    /** Represents the duplication status of a requested insertion of an identifier & idType combination into the database. */
    public static final String INSERTION_DUPLICATE_IDENTIFIER = "duplicate identifier";
    
    /** Represents the duplication status of a requested insertion of a pseudonym into the database. */
    public static final String INSERTION_DUPLICATE_PSEUDONYM = "duplicate pseudonym";

    /** Represents an erroneous insertion of a record into the database. */
    public static final String INSERTION_ERROR = "error";

    /** Represents a successful insertion of a record into the database. */
    public static final String INSERTION_SUCCESS = "success";

    /**
     * Method to insert multiple pseudonyms at once in a batch. Duplicates will be ignored.
     *
     * @param pseudonyms a list of pseudonyms to insert into the database
     * @param domainId the ID of the domain in which the pseudonyms should be created
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code INSERTION_SUCCESS} when the batch insertion was successful, {@code INSERTION_ERROR} otherwise
     */
    @Transactional
    public String createPseudonymBatch(List<PseudonymDTO> pseudonyms, int domainId, HttpServletRequest request) {
        try {
            // Load the batch through mapping an array of pseudonyms (represented as an array of attributes)
            // to the database fields
            Loader<PseudonymRecord> loader = dslCtx.loadInto(PSEUDONYM)
                    .onDuplicateKeyIgnore()
                    .loadArrays(pseudonyms.stream().map(
                                    t -> new Object[] {
                                            t.getIdentifierItem().getIdentifier(),
                                            t.getIdentifierItem().getIdType(),
                                            t.getPsn(),
                                            t.getValidFrom(),
                                            t.getValidFromInherited(),
                                            t.getValidTo(),
                                            t.getValidToInherited(),
                                            domainId
                                    })
                            .toArray(Object[][]::new))
                    .fields(PSEUDONYM.IDENTIFIER,
                            PSEUDONYM.IDTYPE,
                            PSEUDONYM.PSEUDONYM_,
                            PSEUDONYM.VALIDFROM,
                            PSEUDONYM.VALIDFROMINHERITED,
                            PSEUDONYM.VALIDTO,
                            PSEUDONYM.VALIDTOINHERITED,
                            PSEUDONYM.DOMAINID)
                    .execute();

            // Log information about the batch processing
            log.trace("Inserted " + loader.stored() + " pseudonym(s).");
            log.trace("Ignored " + loader.ignored() + " pseudonym(s).");

            log.debug("Successfully inserted a batch of " + pseudonyms.size() + " pseudonyms into the database.");
            return INSERTION_SUCCESS;
        } catch (Exception e) {
            log.error("Couldn't insert the batch of " + pseudonyms.size() + " pseudonyms into the database: " + e.getMessage() + "\n");
            return INSERTION_ERROR;
        }
    }

    /**
     * Method to insert a pseudonym into the database.
     *
     * @param pseudonym the pseudonym object that should be inserted
     * @param domainId the ID of the domain in which the pseudonym should be created
     * @param multiplePsnAllowed whether or not multiple pseudonyms per id&idType combination are allowed
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code INSERTION_SUCCESS} when the insertion was successful, {@code INSERTION_DUPLICATE}
     * when the record was already in the database, {@code INSERTION_ERROR} otherwise
     */
    @Transactional
    public String createPseudonym(PseudonymDTO pseudonym, int domainId, boolean multiplePsnAllowed, HttpServletRequest request) {
        try {
        	// If no multiple pseudonyms per id&idType-combination are allowed, then check 
        	// if the id&idType-combination is already in the database
        	if (!multiplePsnAllowed) {
        		// Try retrieving duplicates
            	List<Pseudonym> p = dslCtx.selectFrom(PSEUDONYM)
						                .where(PSEUDONYM.IDENTIFIER.equal(pseudonym.getIdentifierItem().getIdentifier()))
						                .and(PSEUDONYM.IDTYPE.equal(pseudonym.getIdentifierItem().getIdType()))
						                .and(PSEUDONYM.DOMAINID.eq(domainId))
						                .fetchInto(Pseudonym.class);
            	
            	if (p != null && p.size() >= 1) {
                    throw new DuplicateIdentifierException("", pseudonym.getIdentifierItem().getIdentifier(), pseudonym.getIdentifierItem().getIdType());
            	}
        	}

        	// Insert into table
        	PseudonymRecord created;
            try {
            	created = dslCtx.insertInto(PSEUDONYM)
	        			.set(PSEUDONYM.IDENTIFIER, pseudonym.getIdentifierItem().getIdentifier())
	        			.set(PSEUDONYM.IDTYPE, pseudonym.getIdentifierItem().getIdType())
	        			.set(PSEUDONYM.PSEUDONYM_, pseudonym.getPsn())
	        			.set(PSEUDONYM.VALIDFROM, pseudonym.getValidFrom())
	        			.set(PSEUDONYM.VALIDFROMINHERITED, pseudonym.getValidFromInherited())
	        			.set(PSEUDONYM.VALIDTO, pseudonym.getValidTo())
	        			.set(PSEUDONYM.VALIDTOINHERITED, pseudonym.getValidToInherited())
	        			.set(PSEUDONYM.DOMAINID, domainId)
	        			.returning()
	        			.fetchOne();
	        	if (created == null) {
	        		log.debug("Inserting the pseudonym failed.");
					return null;
	        	}
			} catch (TooManyRowsException e) {
				// Too many rows affected, abort by throwing an exception
				log.error("Couldn't insert the pseudonym into the database.");
				throw new UnexpectedResultSizeException(1, null);
        	} catch (DataAccessException f) {
				// Check if the cause of the exception was a uniqueness violation
	            if (f.getCause() instanceof PSQLException) {
	                PSQLException psqlException = (PSQLException) f.getCause();
	                
	                if (psqlException.getSQLState().equals("23505") || psqlException.getMessage().contains("duplicate key")) {
	                	// Record is already in the DB. Use exception to break the transaction.
	                    throw new DuplicatePseudonymException(domainDBAccessService.getDomainByID(domainId, null).getName(), pseudonym.getIdentifierItem().getIdentifier(), pseudonym.getIdentifierItem().getIdType());
	                } else {
	                    // Re-throw the original PSQL exception
	                	throw psqlException;
	                }
	            } else {
	            	// Re-throw the original exception
                	throw f;
	            }
	        }
            
            log.debug("Successfully inserted the pseudonym into the database.");
            return INSERTION_SUCCESS;
        } catch (DuplicateIdentifierException e) {
        	log.debug("The identifier & idType combination that should be inserted was already there. No insertion performed.");
            return INSERTION_DUPLICATE_IDENTIFIER;
        } catch (DuplicatePseudonymException | DuplicateKeyException f) {
        	log.debug("The pseudonym that should be inserted was already there. No insertion performed.");
            return INSERTION_DUPLICATE_PSEUDONYM;
        } catch (UnexpectedResultSizeException g) {
            log.error("The insertion would have affected an unexpected number of records. It should only affect 1 pseudonym record.");
            return INSERTION_ERROR;
        } catch (Exception h) {
            log.error("Couldn't insert the record into the database: " + h.getClass() + ": " + h.getMessage());
            return INSERTION_ERROR;
        }
    }

    /**
     * Checks whether or not the given pseudonym-record can be found in the DB.
     * 
     * @param p the pseudonym-record to check the existence of
     * @return {@code true} when the pseudonym-record exists in the DB, {@code false} otherwise
     */
    @Transactional
    public boolean exists(PseudonymDTO p) {
    	Domain d = domainDBAccessService.getDomainByName(p.getDomainName(), null);
    	if (d == null) {
    		return false;
    	}
    	
    	// Also include the psn in the check if multiple pseudonym-values per identifier&idType-combination are allowed
    	String psn = d.getMultiplepsnallowed() ? p.getPsn() : null;
    	List<PseudonymDTO> results = getPseudonym(d.getName(), p.getIdentifierItem(), psn, null);
    	
    	return results != null && results.size() == 1;
    }
    
    /**
     * Method to retrieve pseudonym-records. This method actually only 
     * evaluates the given information and delegates the getting-process.
     * 
     * @param domainName the name of the domain to search in
     * @param identifierItem the identifierItem to search for
     * @param psn the pseudonym to search for
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return the pseudonymization data element referring to the given information, or
     * {@code null} when nothing is found or an error occurs
     */
    public List<PseudonymDTO> getPseudonym(String domainName, IdentifierItem identifierItem, String psn, HttpServletRequest request) {
    	// Decide on which get-method to use depending on the given information
    	if (Assertion.isNotNullOrEmpty(identifierItem.getIdentifier(), identifierItem.getIdType()) && psn == null) {
    		// Retrieve pseudonym through the identifier
    		return getPseudonymFromIdentifier(domainName, identifierItem, request);
    	} else if (Assertion.isNullOrEmpty(identifierItem.getIdentifier(), identifierItem.getIdType()) && psn != null) {
    		// Retrieve pseudonym through the pseudonym
    		PseudonymDTO p = getPseudonymFromPsn(domainName, psn, request);
    		return p == null ? null : List.of(p);
    	} else if (Assertion.isNotNullOrEmpty(identifierItem.getIdentifier(), identifierItem.getIdType(), psn)) {
    		// The identifier, idType, and pseudonym were given, check that all attributes are correct
    		List<PseudonymDTO> pseudonyms = getPseudonymFromIdentifier(domainName, identifierItem, request);
    		
    		if (pseudonyms == null) {
    			log.debug("Nothing was found for the given parameter configuration.");
        		return null;
    		}
    		
    		for (PseudonymDTO p : pseudonyms) {
    			if (p.getIdentifierItem().equals(identifierItem) && p.getPsn().equals(psn)) {
        			return List.of(p);
        		}
        	}
    		
    		log.debug("None of the records that were found matched the given pseudonym.");
    		return null;
    	} else {
    		log.debug("Invalid configuration of parameters. Either id and idType, the psn, or all three are needed.");
    		return null;
    	}
    }

    /**
     * Retrieves a pseudonym-record from a specified identifier.
     *
     * @param domainName the name of the domain to search in
     * @param identifierItem the identifierItem to search for
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return the pseudonymization data element referring to the given identifier, or
     * {@code null} when nothing is found or an error occurs
     */
    @Transactional
    public List<PseudonymDTO> getPseudonymFromIdentifier(String domainName, IdentifierItem identifierItem, HttpServletRequest request) {
        try {
            // Check that the domain name is valid
        	Domain d = domainDBAccessService.getDomainByName(domainName, null);
            if (d == null) {
                log.debug("The domain to search the pseudonym in, wasn't found.");
                return null;
            }
        	
        	// Build and execute the query
            List<PseudonymDTO> pseudonyms = dslCtx.selectFrom(PSEUDONYM)
                    .where(PSEUDONYM.IDENTIFIER.equal(identifierItem.getIdentifier()))
                    .and(PSEUDONYM.IDTYPE.equal(identifierItem.getIdType()))
                    .and(PSEUDONYM.DOMAINID.eq(d.getId()))
                    .fetchInto(PseudonymDTO.class);

            // Check if a pseudonym-record was found or not
            if (pseudonyms == null || pseudonyms.size() == 0) {
                log.debug("There is no element in the database matching the given identifier and type.");
                return null;
            } else {
                log.debug("Successfully retrieved " + pseudonyms.size() + " pseudonym" + (pseudonyms.size() == 1 ? "." : "s."));
                return pseudonyms;
            }
        } catch (Exception e) {
            log.error("Couldn't query the database: " + e.getClass() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves a pseudonym-record from a specified pseudonym.
     *
     * @param domainName the name of the domain to search in
     * @param psn the pseudonym to search for
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return the pseudonymization data element referring to the given pseudonym, or
     * {@code null} when nothing is found or an error occurs
     */
    @Transactional
    public PseudonymDTO getPseudonymFromPsn(String domainName, String psn, HttpServletRequest request) {
        try {
        	// Check that the domain name is valid
            Domain d = domainDBAccessService.getDomainByName(domainName, null);
            if (d == null) {
                log.debug("The domain to search the pseudonym in, wasn't found.");
                return null;
            }
        	
        	// Build and execute the query
            PseudonymDTO pseudonym = dslCtx.selectFrom(PSEUDONYM)
                    .where(PSEUDONYM.PSEUDONYM_.equal(psn))
                    .and(PSEUDONYM.DOMAINID.eq(d.getId()))
                    .fetchOneInto(PseudonymDTO.class);
            
            // Check if a pseudonym-record was found or not
            if (pseudonym == null) {
                log.debug("There is no element in the database matching the given pseudonym-value.");
                return null;
            } else {
            	log.debug("Successfully retrieved a pseudonym.");
                return pseudonym;
            }
        } catch (Exception e) {
            log.error("Couldn't query the database: " + e.getClass() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Method to update multiple pseudonyms at once in a batch. Not found pseudonyms will be ignored.
     * Updatable attributes are restricted to pseudonym, validFrom, validFromInherited,
     * validTo, and validToInherited.
     *
     * @param pseudonyms a list of pseudonyms that are to be updated in the database
     * @param domainId the ID of the domain in which the pseudonyms should be updated
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} when the batch update was successful, {@code false} otherwise
     */
    @Transactional
    public List<Boolean> updatePseudonymBatch(List<PseudonymDTO> pseudonyms, int domainId, HttpServletRequest request) {
        try {
        	List<Boolean> updateSuccess = new ArrayList<>();
        	
        	// Create a list of update statements
            List<UpdateConditionStep<PseudonymRecord>> updates = new ArrayList<>();
            for (PseudonymDTO p : pseudonyms) {
                updates.add(dslCtx.update(PSEUDONYM)
                        .set(PSEUDONYM.IDENTIFIER, p.getIdentifierItem().getIdentifier())
                        .set(PSEUDONYM.IDTYPE, p.getIdentifierItem().getIdType())
                        .set(PSEUDONYM.PSEUDONYM_, p.getPsn())
                        .set(PSEUDONYM.VALIDFROM, p.getValidFrom())
                        .set(PSEUDONYM.VALIDFROMINHERITED, p.getValidFromInherited())
                        .set(PSEUDONYM.VALIDTO, p.getValidTo())
                        .set(PSEUDONYM.VALIDTOINHERITED, p.getValidToInherited())
                        .set(PSEUDONYM.DOMAINID, domainDBAccessService.getDomainByName(p.getDomainName(), null).getId())
                        .where(PSEUDONYM.IDENTIFIER.equal(p.getIdentifierItem().getIdentifier()))
                        .and(PSEUDONYM.IDTYPE.equal(p.getIdentifierItem().getIdType()))
                        .and(PSEUDONYM.DOMAINID.equal(domainId)));
            }
            
        	// Batch the update statements and execute the batch
            int[] result = dslCtx.batch(updates).execute();

            // Process the result
            int updated = 0;
            int ignored = 0;
            
            for (int i = 0; i < result.length; i++) {
                if (result[i] == 1) {
                    // Successful update of exactly one record
                    updated++;
                    updateSuccess.add(true);
                } else if (result[i] == 0) {
                    // Update didn't affect any record (e.g. because the record to be updated wasn't found)
                    ignored++;
                    updateSuccess.add(false);
                } else {
                    // Unexpected result, abort the complete transaction by throwing an exception
                    throw new UnexpectedResultSizeException(1, result[i]);
                }
            }

            // Log information about the batch processing
            log.trace("Updated " + updated + " pseudonym(s).");
            log.trace("Ignored " + ignored + " pseudonym(s).");

            // Return the list of success or failure
            log.debug("Successfully updated " + updated + " out of " + updates.size() + " pseudonyms in the database.");
            return updateSuccess;
        } catch (UnexpectedResultSizeException e) {
            log.error("The update would have affected an unexpected number of records (" + e.getActual() + ") "
                    + "when it should have only affected " + e.getExpected() + " record(s). The complete update "
                    + "was therefore rolled back.");
            return null;
        } catch (Exception f) {
            log.error("Couldn't update the batch of pseudonyms in the database: " + f.getMessage() + "\n");
            return null;
        }
    }

    /**
     * Method to update pseudonym-records in the database.
     * Updates only those parameters that aren't {@code null}.
     *
     * @param oldPseudonym the old pseudonym object that should be updated
     * @param newPseudonym the new pseudonym object containing the data that should be updated
     * @param domainId the ID of the domain in which the pseudonym should be updated
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} if the update was successful and only one record was affected, {@code false} otherwise.
     */
    @Transactional
    public boolean updatePseudonym(PseudonymDTO oldPseudonym, PseudonymDTO newPseudonym, int domainId, HttpServletRequest request) {
        try {
            // Check if the record to be updated exists
            if (!exists(oldPseudonym)) {
                // Record is not in the DB. Use exception to break the transaction.
                throw new PseudonymNotFoundException(domainDBAccessService.getDomainByID(domainId, null).getName(), oldPseudonym.getIdentifierItem().getIdentifier(), oldPseudonym.getIdentifierItem().getIdType());
            }
            
            // Sanitize update values
            String identifier = Assertion.isNotNullOrEmpty(newPseudonym.getIdentifierItem().getIdentifier()) ? newPseudonym.getIdentifierItem().getIdentifier() : oldPseudonym.getIdentifierItem().getIdentifier();
            String idType = Assertion.isNotNullOrEmpty(newPseudonym.getIdentifierItem().getIdType()) ? newPseudonym.getIdentifierItem().getIdType() : oldPseudonym.getIdentifierItem().getIdType();
            String psn = Assertion.isNotNullOrEmpty(newPseudonym.getPsn()) ? newPseudonym.getPsn() : oldPseudonym.getPsn();
            LocalDateTime validFrom = (newPseudonym.getValidFrom() != null) ? newPseudonym.getValidFrom() : oldPseudonym.getValidFrom();
            Boolean validFromInh = (newPseudonym.getValidFromInherited() != null) ? newPseudonym.getValidFromInherited() : oldPseudonym.getValidFromInherited();
            LocalDateTime validTo = (newPseudonym.getValidTo() != null) ? newPseudonym.getValidTo() : oldPseudonym.getValidTo();
            Boolean validToInh = (newPseudonym.getValidToInherited() != null) ? newPseudonym.getValidToInherited() : oldPseudonym.getValidToInherited();
            int domID = domainDBAccessService.getDomainByName((newPseudonym.getDomainName() != null) ? newPseudonym.getDomainName() : oldPseudonym.getDomainName(), null).getId();
            
            // Create and execute the update query
            PseudonymRecord updatedRecord;
            try {
	            updatedRecord = dslCtx.update(PSEUDONYM)
	                    .set(PSEUDONYM.IDENTIFIER, identifier)
	                    .set(PSEUDONYM.IDTYPE, idType)
	                    .set(PSEUDONYM.PSEUDONYM_, psn)
	                    .set(PSEUDONYM.VALIDFROM, validFrom)
	                    .set(PSEUDONYM.VALIDFROMINHERITED, validFromInh)
	                    .set(PSEUDONYM.VALIDTO, validTo)
	                    .set(PSEUDONYM.VALIDTOINHERITED, validToInh)
	                    .set(PSEUDONYM.DOMAINID, domID)
	                    .where(PSEUDONYM.IDENTIFIER.equal(oldPseudonym.getIdentifierItem().getIdentifier()))
	                    .and(PSEUDONYM.IDTYPE.equal(oldPseudonym.getIdentifierItem().getIdType()))
	                    .and(PSEUDONYM.DOMAINID.equal(domainId))
	                    .returning()
	                    .fetchOneInto(PseudonymRecord.class);
            } catch (TooManyRowsException e) {
            	// An unexpected number of records was affected, abort by throwing an exception
                throw new UnexpectedResultSizeException(1, null);
            } catch (DataAccessException f) {
            	log.debug("Updating the pseudonym failed.", f);
    			return false;
            }
            
            if (updatedRecord == null) {
            	log.debug("Updating the pseudonym failed.");
    			return false;
            }
            
            // At this point the update was successful
            log.debug("Successfully updated the pseudonym in the database.");
            return true;
        } catch (PseudonymNotFoundException e) {
            log.info("The pseudonym-record is not in the database. Nothing to update.");
            return false;
        } catch (UnexpectedResultSizeException f) {
            log.error("The update would have affected an unexpected number of records. It should only affect 1 record.");
            return false;
        } catch (Exception g) {
            log.error("Couldn't update the record from the database: " + g.getClass() + ": " + g.getMessage());
            return false;
        }
    }

    /**
     * Method to delete multiple pseudonyms at once in a batch. Not found pseudonyms will be ignored.
     *
     * @param pseudonyms a list of pseudonyms that are to be deleted from the database
     * @param domainId the ID of the domain in which the pseudonym should be updated
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} when the batch deletion was successful, {@code false} otherwise
     */
    @Transactional
    public List<Boolean> deletePseudonymBatch(List<PseudonymDTO> pseudonyms, int domainId, HttpServletRequest request) {
        try {
        	List<Boolean> deleteSuccess = new ArrayList<>();
        	
        	// Create a list of delete statements
            List<DeleteConditionStep<PseudonymRecord>> deletions = new ArrayList<>();
            for (PseudonymDTO p : pseudonyms) {
                deletions.add(dslCtx.delete(PSEUDONYM)
                        .where(PSEUDONYM.IDENTIFIER.equal(p.getIdentifierItem().getIdentifier()))
                        .and(PSEUDONYM.IDTYPE.equal(p.getIdentifierItem().getIdType()))
                        .and(PSEUDONYM.PSEUDONYM_.equal(p.getPsn()))
                        .and(PSEUDONYM.DOMAINID.equal(domainId)));
            }

            // Batch the delete statements and execute the batch
            int[] result = dslCtx.batch(deletions).execute();

            // Process the result
            int deleted = 0;
            int ignored = 0;

            for (int i = 0; i < result.length; i++) {
                if (result[i] == 1) {
                    // Successful deletion of exactly one record
                    deleted++;
                    deleteSuccess.add(true);
                } else if (result[i] == 0) {
                    // Deletion didn't affect any record (e.g. because the record to be deleted wasn't found)
                    ignored++;
                    deleteSuccess.add(false);
                } else {
                    // Unexpected result, abort the complete transaction by throwing an exception
                    throw new UnexpectedResultSizeException(1, result[i]);
                }
            }

            // Log information about the batch processing
            log.trace("Deleted " + deleted + " pseudonym(s).");
            log.trace("Ignored " + ignored + " pseudonym(s).");

            // Return the list of deletion success or failure
            log.debug("Successfully deleted " + deleted + " out of " + deletions.size() + " pseudonyms in the database.");
            return deleteSuccess;
        } catch (UnexpectedResultSizeException e) {
            log.error("The deletion would have affected an unexpected number of records (" + e.getActual() + ") "
                    + "when it should have only affected " + e.getExpected() + " record(s). The deletion process "
                    + "was therefore rolled back.");
            return null;
        } catch (Exception f) {
            log.error("Couldn't delete a batch of pseudonyms from the database: " + f.getClass() + ": " + f.getMessage());
            return null;
        }
    }

    /**
     * Deletes a pseudonym-record from the pseudonym table.
     *
     * @param domainName the name of the domain to search in
     * @param identifierItem the identifierItem of the pseudonym you want to delete
     * @param psn the pseudonym-value of the pseudonym-record you want to delete
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} when the deletion was successful or the record
     * was not in the database, {@code false} otherwise
     */
    @Transactional
    public boolean deletePseudonym(String domainName, IdentifierItem identifierItem, String psn, HttpServletRequest request) {
        try {
        	// Retrieve the domain the pseudonym belongs to
            Domain d = domainDBAccessService.getDomainByName(domainName, null);
            if (d == null) {
                // Invalid domain name. Use exception to break the transaction.
                throw new DomainNotFoundException(domainName);
            }

            // Create and execute the deletion query
            int deletedRecords = dslCtx.deleteFrom(PSEUDONYM)
                    .where(PSEUDONYM.IDENTIFIER.equal(identifierItem.getIdentifier()))
                    .and(PSEUDONYM.IDTYPE.equal(identifierItem.getIdType()))
                    .and(PSEUDONYM.PSEUDONYM_.equal(psn))
                    .and(PSEUDONYM.DOMAINID.equal(d.getId()))
                    .execute();

            // Determine deletion success
            if (deletedRecords != 1) {
                // An unexpected number of records was affected, abort by throwing an exception
                log.error("Couldn't delete the record from the database.");
                throw new UnexpectedResultSizeException(1, deletedRecords);
            }

            // At this point the deletion was successful
            log.debug("Successfully deleted the record from the database.");
            return true;
        } catch (DomainNotFoundException e) {
            log.debug("The domain to search the record in (\"" + e.getDomainName() + "\"), wasn't found.");
            return false;
        } catch (UnexpectedResultSizeException g) {
            if (g.getActual() == 0) {
            	log.debug("The pseudonym that should be deleted was not found in the database.");
            } else {
            	log.error("The deletion would have affected an unexpected number of records. It should only affect 1 record, "
                    + "but affected " + g.getActual() + " records.");
            }
            
            return false;
        } catch (Exception h) {
            log.error("Couldn't delete the record from the database: " + h.getClass() + ": " + h.getMessage());
            return false;
        }
    }
}

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
import org.jooq.impl.DSL;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.trustdeck.exception.DomainNotFoundException;
import org.trustdeck.exception.DuplicateIdentifierException;
import org.trustdeck.exception.DuplicatePseudonymException;
import org.trustdeck.exception.PseudonymNotFoundException;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.jooq.generated.tables.daos.AuditeventDao;
import org.trustdeck.jooq.generated.tables.pojos.Auditevent;
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.jooq.generated.tables.pojos.Pseudonym;
import org.trustdeck.jooq.generated.tables.records.PseudonymRecord;
import org.trustdeck.security.audittrail.event.AuditEventBuilder;
import org.trustdeck.utils.Assertion;

import static org.trustdeck.jooq.generated.Tables.PSEUDONYM;

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
    
    /** The data access object for the audit trail table. */
    private AuditeventDao auditDao;
    
    /** Links the service that creates audit event objects. */
    @Autowired
    private AuditEventBuilder auditEventBuilder;

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
     * Method to retrieve the audit event data access object (or create it if it's {@code null}.)
     *
     * @return the audit event DAO
     */
    private AuditeventDao getAuditeventDao() {
        if (this.auditDao == null) {
            this.auditDao = new AuditeventDao(this.dslCtx.configuration());
        }

        return this.auditDao;
    }

    /**
     * Method to delete multiple pseudonyms at once in a batch. Not found pseudonyms will be ignored.
     *
     * @param pseudonyms a list of pseudonyms that are to be deleted from the database
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} when the batch deletion was successful, {@code false} otherwise
     */
    public boolean deletePseudonymBatch(List<Pseudonym> pseudonyms, HttpServletRequest request) {
        try {
            int removed = dslCtx.transactionResult(configuration -> {
                // Delete by creating multiple delete statements and executing them as a batch.
                DSLContext ctx = DSL.using(configuration);

                // Create a list of delete statements
                List<DeleteConditionStep<PseudonymRecord>> deletions = new ArrayList<>();
                for (Pseudonym p : pseudonyms) {
                    deletions.add(ctx.delete(PSEUDONYM)
                            .where(PSEUDONYM.IDENTIFIER.equal(p.getIdentifier()))
                            .and(PSEUDONYM.IDTYPE.equal(p.getIdtype()))
                            .and(PSEUDONYM.PSEUDONYM_.equal(p.getPseudonym()))
                            .and(PSEUDONYM.DOMAINID.equal(p.getDomainid())));
                }

                // Batch the delete statements and execute the batch
                int[] result = ctx.batch(deletions).execute();

                // Process the result
                int deleted = 0;
                int ignored = 0;

                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 1) {
                        // Successful deletion of exactly one record
                        deleted++;
                    } else if (result[i] == 0) {
                        // Deletion didn't affect any record (e.g. because the record to be deleted wasn't found)
                        ignored++;
                    } else {
                        // Unexpected result, abort the complete transaction by throwing an exception
                        throw new UnexpectedResultSizeException(1, result[i]);
                    }
                }

                // Log information about the batch processing
                log.debug("Deleted " + deleted + " pseudonym(s).");
                log.debug("Ignored " + ignored + " pseudonym(s).");

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);
	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }

                // Return the number of successful deletions
                return deleted;

                // Implicit transaction commit here
            });

            log.debug("Successfully deleted a batch of " + removed + " pseudonyms from the database.");
            return true;
        } catch (UnexpectedResultSizeException e) {
            log.error("The deletion would have affected an unexpected number of records (" + e.getActualSize() + ") "
                    + "when it should have only affected " + e.getExpectedSize() + " record(s). The complete deletion "
                    + "was therefore rolled back.\n" + e.getMessage());
            return false;
        } catch (Exception f) {
            log.error("Couldn't delete the batch of " + pseudonyms.size() + " pseudonyms from the database: " + f.getMessage() + "\n");
            return false;
        }
    }

    /**
     * Deletes a pseudonym-record from the pseudonym table.
     *
     * @param domainName the name of the domain to search in
     * @param identifier the identifier of the record you want to delete
     * @param idType the type of the identifier of the record you want to delete
     * @param pseudonym the pseudonym of the record you want to delete
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} when the deletion was successful or the record
     * was not in the database, {@code false} otherwise
     */
    public boolean deletePseudonym(String domainName, String identifier, String idType, String pseudonym, HttpServletRequest request) {
        try {
            dslCtx.transaction(configuration -> {
                // Retrieve the domain the pseudonym belongs to
                Domain d = domainDBAccessService.getDomainByName(domainName, null);
                if (d == null) {
                    // Invalid domain name. Use exception to break the transaction.
                    throw new DomainNotFoundException(domainName);
                }

                // Check if the record that should be deleted is in the database
                Pseudonym p = DSL.using(configuration).selectFrom(PSEUDONYM)
                        .where(PSEUDONYM.IDENTIFIER.equal(identifier))
                        .and(PSEUDONYM.IDTYPE.equal(idType))
                        .and(PSEUDONYM.PSEUDONYM_.equal(pseudonym))
                        .and(PSEUDONYM.DOMAINID.equal(d.getId()))
                        .fetchOneInto(Pseudonym.class);

                if (p == null) {
                    // Pseudonym wasn't found. Use exception to break the transaction.
                    throw new PseudonymNotFoundException(domainName, identifier, idType);
                }

                // Create and execute the deletion query
                int deletedRecords = DSL.using(configuration).deleteFrom(PSEUDONYM)
                        .where(PSEUDONYM.IDENTIFIER.equal(identifier))
                        .and(PSEUDONYM.IDTYPE.equal(idType))
                        .and(PSEUDONYM.PSEUDONYM_.equal(pseudonym))
                        .and(PSEUDONYM.DOMAINID.equal(d.getId()))
                        .execute();

                // Determine deletion success
                if (deletedRecords != 1) {
                    // An unexpected number of records was affected. Log it and abort by throwing
                    // an exception (which will rollback everything from the transaction).
                    log.error("Couldn't delete the record from the database.");
                    throw new UnexpectedResultSizeException(1, deletedRecords);
                }

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);
	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }

                // Implicit transaction commit here
            });

            // At this point the deletion was successful
            log.debug("Successfully deleted the record from the database.");
            return true;
        } catch (DomainNotFoundException e) {
            log.debug("The domain to search the record in (\"" + e.getDomainName() + "\"), wasn't found.");
            return false;
        } catch (PseudonymNotFoundException f) {
            log.info("The pseudonym-record is not in the database. Nothing to delete.");
            return true;
        } catch (UnexpectedResultSizeException g) {
            log.error("The deletion would have affected an unexpected number of records. It should only affect 1 record, "
                    + "but affected " + g.getActualSize() + " records.");
            return false;
        } catch (Exception h) {
            log.error("Couldn't delete the record from the database: " + h.getMessage() + "\n");
            return false;
        }
    }

    /**
     * Checks whether or not the given pseudonym-record can be found in the DB.
     * 
     * @param p the pseudonym-record to check the existence of
     * @return {@code true} when the pseudonym-record exists in the DB, {@code false} otherwise
     */
    public boolean exists(Pseudonym p) {
    	Domain d = domainDBAccessService.getDomainByID(p.getDomainid(), null);
    	
    	// Also include the pseudonym-value in the check if multiple pseudonym-values 
    	// per identifier&idType-combination are allowed
    	List<Pseudonym> results = getRecord(d.getName(), p.getIdentifier(), p.getIdtype(), (d.getMultiplepsnallowed() ? p.getPseudonym() : null), null);
    	
    	return results != null && results.size() == 1;
    }

    /**
     * Retrieves a pseudonym-record from a specified identifier.
     *
     * @param domainName the name of the domain to search in
     * @param identifier the identifier to search for
     * @param idType the type of the identifier
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return the pseudonymization data element referring to the given identifier, or
     * {@code null} when nothing is found or an error occurs
     */
    public List<Pseudonym> getRecordFromIdentifier(String domainName, String identifier, String idType, HttpServletRequest request) {
        try {
            List<Pseudonym> p = dslCtx.transactionResult(configuration -> {
                // Check that the domain name is valid
                Domain d = domainDBAccessService.getDomainByName(domainName, null);
                if (d == null) {
                    log.debug("The domain to search the record in, wasn't found.");
                    return null;
                }

                // Build and execute the query
                List<Pseudonym> pseudo = DSL.using(configuration)
                        .selectFrom(PSEUDONYM)
                        .where(PSEUDONYM.IDENTIFIER.equal(identifier))
                        .and(PSEUDONYM.IDTYPE.equal(idType))
                        .and(PSEUDONYM.DOMAINID.eq(d.getId()))
                        .fetchInto(Pseudonym.class);

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);
	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }
                
                return pseudo;

                // Implicit transaction commit here
            });

            // Check if a pseudonym-record was found or not
            if (p == null) {
                log.debug("There is no element in the database matching the given identifier and type.");
                return null;
            } else {
                log.debug("Successfully retrieved " + p.size() + " pseudonym" + (p.size() == 1 ? "." : "s."));
                return p;
            }
        } catch (Exception e) {
            log.error("Couldn't query the database: " + e.getMessage() + "\n");
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
    public Pseudonym getRecordFromPseudonym(String domainName, String psn, HttpServletRequest request) {
        try {
            Pseudonym p = dslCtx.transactionResult(configuration -> {
                // Check that the domain name is valid
                Domain d = domainDBAccessService.getDomainByName(domainName, null);
                if (d == null) {
                    log.debug("The domain to search the record in, wasn't found.");
                    return null;
                }

                // Build and execute the query
                Pseudonym pseudo = DSL.using(configuration)
                        .selectFrom(PSEUDONYM)
                        .where(PSEUDONYM.PSEUDONYM_.equal(psn))
                        .and(PSEUDONYM.DOMAINID.eq(d.getId()))
                        .fetchOneInto(Pseudonym.class);

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);
	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }
                
                return pseudo;

                // Implicit transaction commit here
            });

            // Check if a pseudonym-record was found or not
            if (p == null) {
                log.debug("There is no element in the database matching the given pseudonym.");
                return null;
            } else {
            	log.debug("Successfully retrieved a pseudonym.");
                return p;
            }
        } catch (Exception e) {
            log.error("Couldn't query the database: " + e.getMessage() + "\n");
            return null;
        }
    }
    
    /**
     * Method to retrieve pseudonym-records. This method actually only 
     * evaluates the given information and delegates the getting-process.
     * 
     * @param domainName the name of the domain to search in
     * @param identifier the identifier to search for
     * @param idType the type of the identifier
     * @param psn the pseudonym to search for
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return the pseudonymization data element referring to the given information, or
     * {@code null} when nothing is found or an error occurs
     */
    public List<Pseudonym> getRecord(String domainName, String identifier, String idType, String psn, HttpServletRequest request) {
    	// Decide on which get-method to use depending on the given information
    	if (Assertion.assertNotNullAll(identifier, idType) && psn == null) {
    		// Retrieve record through the identifier
    		return getRecordFromIdentifier(domainName, identifier, idType, request);
    	} else if (Assertion.assertNullAll(identifier, idType) && psn != null) {
    		// Retrieve record through the pseudonym
    		List<Pseudonym> pList = new ArrayList<Pseudonym>();
            Pseudonym p = getRecordFromPseudonym(domainName, psn, request);
            if (p != null){
                pList.add(p);
                return pList;
            }else{
                return null;
            }
    	} else if (Assertion.assertNotNullAll(identifier, idType, psn)) {
    		// The identifier, idType, and pseudonym were given. Check that all attributes are correct.
    		List<Pseudonym> res = getRecordFromIdentifier(domainName, identifier, idType, request);
    		
    		if (res == null) {
    			log.debug("Nothing was found for the given parameter configuration.");
        		return null;
    		}
    		
    		for (Pseudonym r : res) {
    			if (r.getIdentifier().equals(identifier) && r.getIdtype().equals(idType) && r.getPseudonym().equals(psn)) {
        			List<Pseudonym> singleRecordAsList = new ArrayList<Pseudonym>();
        			singleRecordAsList.add(r);
        			return singleRecordAsList;
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
     * Method to insert multiple pseudonyms at once in a batch. Duplicates will be ignored.
     *
     * @param pseudonyms a list of pseudonyms to insert into the database
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code INSERTION_SUCCESS} when the batch insertion was successful, {@code INSERTION_ERROR} otherwise
     */
    public String insertPseudonymBatch(List<Pseudonym> pseudonyms, HttpServletRequest request) {
        try {
            dslCtx.transaction(configuration -> {
                // Load the batch through mapping an array of pseudonyms (represented as an array of attributes)
                // to the database fields.
                Loader<PseudonymRecord> loader = DSL.using(configuration).loadInto(PSEUDONYM)
                        .onDuplicateKeyIgnore()
                        .loadArrays(pseudonyms.stream().map(
                                        t -> new Object[] {
                                                t.getIdentifier(),
                                                t.getIdtype(),
                                                t.getPseudonym(),
                                                t.getValidfrom(),
                                                t.getValidfrominherited(),
                                                t.getValidto(),
                                                t.getValidtoinherited(),
                                                t.getDomainid()
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
                log.debug("Inserted " + loader.stored() + " pseudonym(s).");
                log.debug("Ignored " + loader.ignored() + " pseudonym(s).");

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);
	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }

                // Implicit transaction commit here
            });

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
     * @param multiplePsnAllowed whether or not multiple pseudonyms per id&idType combination are allowed
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code INSERTION_SUCCESS} when the insertion was successful, {@code INSERTION_DUPLICATE}
     * when the record was already in the database, {@code INSERTION_ERROR} otherwise
     */
    public String insertPseudonym(Pseudonym pseudonym, boolean multiplePsnAllowed, HttpServletRequest request) {
        try {
            dslCtx.transaction(configuration -> {
            	// If no multiple pseudonyms per id&idType-combination are allowed, then check 
            	// if the id&idType-combination is already in the database
            	if (!multiplePsnAllowed) {
            		// Try retrieving duplicates
	            	List<Pseudonym> p = DSL.using(configuration)
							            	.selectFrom(PSEUDONYM)
							                .where(PSEUDONYM.IDENTIFIER.equal(pseudonym.getIdentifier()))
							                .and(PSEUDONYM.IDTYPE.equal(pseudonym.getIdtype()))
							                .and(PSEUDONYM.DOMAINID.eq(pseudonym.getDomainid()))
							                .fetchInto(Pseudonym.class);
	            	
	            	if (p != null && p.size() >= 1) {
	                    throw new DuplicateIdentifierException("", pseudonym.getIdentifier(), pseudonym.getIdtype());
	            	}
            	}

                // Insert into table
                int insertedRecords = 0;
                try {
					insertedRecords = DSL.using(configuration)
                        .insertInto(PSEUDONYM, PSEUDONYM.IDENTIFIER, PSEUDONYM.IDTYPE, PSEUDONYM.PSEUDONYM_, PSEUDONYM.VALIDFROM,
                                PSEUDONYM.VALIDFROMINHERITED, PSEUDONYM.VALIDTO, PSEUDONYM.VALIDTOINHERITED, PSEUDONYM.DOMAINID)
                        .values(pseudonym.getIdentifier(), pseudonym.getIdtype(), pseudonym.getPseudonym(), pseudonym.getValidfrom(),
                                pseudonym.getValidfrominherited(), pseudonym.getValidto(), pseudonym.getValidtoinherited(), pseudonym.getDomainid())
                        .execute();
				} catch (DataAccessException e) {
					// Check if the cause of the exception was a uniqueness violation
		            if (e.getCause() instanceof PSQLException) {
		                PSQLException psqlException = (PSQLException) e.getCause();
		                
		                if (psqlException.getSQLState().equals("23505") || psqlException.getMessage().contains("duplicate key")) {
		                	// Record is already in the DB. Use exception to break the transaction.
		                    throw new DuplicatePseudonymException(domainDBAccessService.getDomainByID(pseudonym.getDomainid(), null).getName(), pseudonym.getIdentifier(), pseudonym.getIdtype());
		                } else {
		                    // Re-throw the original exception
		                	throw psqlException;
		                }
		            } else {
		            	// Re-throw the original exception
	                	throw e;
		            }
		        }

                // Determine insertion success
                if (insertedRecords != 1) {
                    // An unexpected number of records was affected. Log it and abort by throwing
                    // an exception (which will rollback everything from the transaction).
                    log.error("Couldn't insert the record into the database.");
                    throw new UnexpectedResultSizeException(1, insertedRecords);
                }

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);

	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }

                // Implicit transaction commit here
            });

            log.debug("Successfully inserted the pseudonym into the database.");
            return INSERTION_SUCCESS;
        } catch (DuplicateIdentifierException e) {
        	log.debug("The identifier & idType combination that should be inserted was already there. No insertion performed.");
            return INSERTION_DUPLICATE_IDENTIFIER;
        } catch (DuplicatePseudonymException | DuplicateKeyException f) {
        	log.debug("The pseudonym that should be inserted was already there. No insertion performed.");
            return INSERTION_DUPLICATE_PSEUDONYM;
        } catch (UnexpectedResultSizeException g) {
            log.error("The insertion would have affected an unexpected number of records. It should only affect 1 record, "
                    + "but affected " + g.getActualSize() + " records.");
            return INSERTION_ERROR;
        } catch (Exception h) {
            log.error("Couldn't insert the record into the database: " + h.getMessage() + "\n");
            return INSERTION_ERROR;
        }
    }

    /**
     * Method to update multiple pseudonyms at once in a batch. Not found pseudonyms will be ignored.
     * Updatable attributes are restricted to pseudonym, validFrom, validFromInherited,
     * validTo, and validToInherited.
     *
     * @param pseudonyms a list of pseudonyms that are to be updated in the database
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} when the batch update was successful, {@code false} otherwise
     */
    public boolean updatePseudonymBatch(List<Pseudonym> pseudonyms, HttpServletRequest request) {
        try {
            int up = dslCtx.transactionResult(configuration -> {
                // Update by creating multiple update statements and executing them as a batch.
                DSLContext ctx = DSL.using(configuration);

                // Create a list of update statements
                List<UpdateConditionStep<PseudonymRecord>> updates = new ArrayList<>();
                for (Pseudonym p : pseudonyms) {
                    updates.add(ctx.update(PSEUDONYM)
                            //.set(PSEUDONYM.IDENTIFIER, p.getIdentifier())
                            //.set(PSEUDONYM.IDTYPE, p.getIdtype())
                            .set(PSEUDONYM.PSEUDONYM_, p.getPseudonym())
                            .set(PSEUDONYM.VALIDFROM, p.getValidfrom())
                            .set(PSEUDONYM.VALIDFROMINHERITED, p.getValidfrominherited())
                            .set(PSEUDONYM.VALIDTO, p.getValidto())
                            .set(PSEUDONYM.VALIDTOINHERITED, p.getValidtoinherited())
                            //.set(PSEUDONYM.DOMAINID, p.getDomainid())
                            .where(PSEUDONYM.IDENTIFIER.equal(p.getIdentifier()))
                            .and(PSEUDONYM.IDTYPE.equal(p.getIdtype()))
                            .and(PSEUDONYM.DOMAINID.equal(p.getDomainid())));
                }

                // Batch the update statements and execute the batch
                int[] result = ctx.batch(updates).execute();

                // Process the result
                int updated = 0;
                int ignored = 0;

                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 1) {
                        // Successful update of exactly one record
                        updated++;
                    } else if (result[i] == 0) {
                        // Update didn't affect any record (e.g. because the record to be updated wasn't found)
                        ignored++;
                    } else {
                        // Unexpected result, abort the complete transaction by throwing an exception
                        throw new UnexpectedResultSizeException(1, result[i]);
                    }
                }

                // Log information about the batch processing
                log.debug("Updated " + updated + " pseudonym(s).");
                log.debug("Ignored " + ignored + " pseudonym(s).");

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);
	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }

                // Return the number of successful deletions
                return updated;

                // Implicit transaction commit here
            });

            log.debug("Successfully updated a batch of " + up + " pseudonyms in the database.");
            return true;
        } catch (UnexpectedResultSizeException e) {
            log.error("The update would have affected an unexpected number of records (" + e.getActualSize() + ") "
                    + "when it should have only affected " + e.getExpectedSize() + " record(s). The complete update "
                    + "was therefore rolled back.\n" + e.getMessage());
            return false;
        } catch (Exception f) {
            log.error("Couldn't update the batch of " + pseudonyms.size() + " pseudonyms in the database: " + f.getMessage() + "\n");
            return false;
        }
    }

    /**
     * Method to update pseudonym-records in the database.
     * Updates only those parameters that aren't {@code null}.
     *
     * @param oldRecord the record object of the record that should be updated
     * @param newRecord the new record object containing the data that should be updated
     * @param request the request object that is needed for creating the audit database-entries. If no 
     * auditing should be performed, you can pass {@code null}.
     * @return {@code true} if the update was successful and only one record was affected, {@code false} otherwise.
     */
    public boolean updatePseudonym(Pseudonym oldRecord, Pseudonym newRecord, HttpServletRequest request) {
        try {
            dslCtx.transaction(configuration -> {
                // Check if the record to be updated exists
                if (!exists(oldRecord)) {
                    // Record is not in the DB. Use exception to break the transaction.
                    throw new PseudonymNotFoundException(domainDBAccessService.getDomainByID(oldRecord.getDomainid(), null).getName(), oldRecord.getIdentifier(), oldRecord.getIdtype());
                }

                // Create and execute the update query
                int updatedRecords = DSL.using(configuration).update(PSEUDONYM)
                        .set(PSEUDONYM.IDENTIFIER, (newRecord.getIdentifier() != null && newRecord.getIdentifier().trim() != "") ? newRecord.getIdentifier() : oldRecord.getIdentifier())
                        .set(PSEUDONYM.IDTYPE, (newRecord.getIdtype() != null && newRecord.getIdtype().trim() != "") ? newRecord.getIdtype() : oldRecord.getIdtype())
                        .set(PSEUDONYM.PSEUDONYM_, (newRecord.getPseudonym() != null && newRecord.getPseudonym().trim() != "") ? newRecord.getPseudonym() : oldRecord.getPseudonym())
                        .set(PSEUDONYM.VALIDFROM, (newRecord.getValidfrom() != null) ? newRecord.getValidfrom() : oldRecord.getValidfrom())
                        .set(PSEUDONYM.VALIDFROMINHERITED, (newRecord.getValidfrominherited() != null) ? newRecord.getValidfrominherited() : oldRecord.getValidfrominherited())
                        .set(PSEUDONYM.VALIDTO, (newRecord.getValidto() != null) ? newRecord.getValidto() : oldRecord.getValidto())
                        .set(PSEUDONYM.VALIDTOINHERITED, (newRecord.getValidtoinherited() != null) ? newRecord.getValidtoinherited() : oldRecord.getValidtoinherited())
                        .set(PSEUDONYM.DOMAINID, (newRecord.getDomainid() != null) ? newRecord.getDomainid() : oldRecord.getDomainid())
                        .where(PSEUDONYM.IDENTIFIER.equal(oldRecord.getIdentifier()))
                        .and(PSEUDONYM.IDTYPE.equal(oldRecord.getIdtype()))
                        .and(PSEUDONYM.DOMAINID.equal(oldRecord.getDomainid()))
                        .execute();

                // Determine success
                if (updatedRecords != 1) {
                    // An unexpected number of records was affected. Log it and abort by throwing
                    // an exception (which will rollback everything from the transaction).
                    throw new UnexpectedResultSizeException(1, updatedRecords);
                }

                // Create audit event object
                if (request != null) {
	                Auditevent auditEvent = auditEventBuilder.build(request);
	                if (auditEvent != null) {
	                	// Write audit information into database
	                	this.getAuditeventDao().insert(auditEvent);
	                }
                }

                // Implicit transaction commit here
            });
            
            // At this point the update was successful
            log.debug("Successfully updated the record in the database.");
            return true;
        } catch (PseudonymNotFoundException e) {
            log.info("The pseudonym-record is not in the database. Nothing to update.");
            return false;
        } catch (UnexpectedResultSizeException f) {
            log.error("The update would have affected an unexpected number of records. It should only affect 1 record, "
                    + "but affected " + f.getActualSize() + " records.");
            return false;
        } catch (Exception g) {
            log.error("Couldn't update the record from the database: " + g.getMessage() + "\n");
            return false;
        }
    }
}

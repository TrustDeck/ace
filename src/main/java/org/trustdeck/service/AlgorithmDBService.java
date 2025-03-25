///*
// * KING - Key Index of Names and General Identification Numbers
// * Copyright 2024-2025 Armin Müller
// * 
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// * 
// * http://www.apache.org/licenses/LICENSE-2.0
// * 
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.trustdeck.service;
//
//import org.jooq.Condition;
//import org.jooq.DSLContext;
//import org.jooq.exception.DataAccessException;
//import org.jooq.exception.MappingException;
//import org.jooq.exception.TooManyRowsException;
//import org.jooq.impl.DSL;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.trustdeck.exception.UnexpectedResultSizeException;
//import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
//import org.trustdeck.jooq.generated.tables.records.AlgorithmRecord;
//
//import lombok.extern.slf4j.Slf4j;
//
//import static org.trustdeck.jooq.generated.Tables.ALGORITHM;
//import static org.trustdeck.jooq.generated.Tables.PERSON;
//
//import java.security.SecureRandom;
//import java.util.List;
//
///**
// * This class encapsulates the database access for algorithm objects.
// */
//@Slf4j
//@Service
//public class AlgorithmDBService {
//    
//	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
//    @Autowired
//	private DSLContext dsl;
//
//    /** The name of the default algorithm. */
//    public static final String DEFAULT_ALGORITHM_NAME = "RANDOM";
//
//    /** The default alphabet for the above default algorithm. */
//    public static final String DEFAULT_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
//
//    /** The default number of pseudonyms that a randomness-based algorithm should be able to produce. */
//    public static final int DEFAULT_RANDOM_ALGORITHM_DESIRED_SIZE = 1_000_000_000;
//
//    /** The default success probability for creating a new pseudonym when using a randomness-based algorithm. */
//    public static final double DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY = 0.999999998;
//
//    /** The default starting point for consecutive numbers as pseudonyms. */
//    public static final long DEFAULT_CONSECUTIVE_VALUE_COUNTER = 1;
//
//    /** The default length for the pseudonyms. */
//    public static final int DEFAULT_PSEUDONYM_LENGTH = 10;
//
//    /** The default character used for padding the pseudonyms to the desired length. */
//    public static final String DEFAULT_PADDING_CHARACTER = "0";
//
//    /** The default value for whether or not to add a check digit to the pseudonym. */
//    public static final boolean DEFAULT_ADD_CHECK_DIGIT = true;
//
//    /** The default value for whether or not the check digit should be included in the pseudonym length. */
//    public static final boolean DEFAULT_LENGTH_INCLUDES_CHECK_DIGIT = true;
//
//    /** The default length of a newly generated salt value. */
//    public static final int DEFAULT_SALT_LENGTH = 32;
//
//    /** The minimum length a salt value given by the user is allowed to be. */
//	private static final int MINIMUM_SALT_LENGTH = 4;
//
//    /** The maximum length a salt value given by the user is allowed to be. */
//	private static final int MAXIMUM_SALT_LENGTH = 256;
//    
//	/**
//     * Creates an algorithm object in the database.
//     * 
//     * @param algorithm the algorithm POJO to store
//     * @return the ID of the newly created algorithm, or {@code null} if an error occurred.
//     */
//    @Transactional
//    public Integer createAlgorithm(Algorithm algorithm) {
//		// Insert new algorithm object into the database
//		int saltLength = (algorithm.getSaltlength() >= MINIMUM_SALT_LENGTH && algorithm.getSaltlength() <= MAXIMUM_SALT_LENGTH) ? algorithm.getSaltlength() : DEFAULT_SALT_LENGTH;
//		
//		AlgorithmRecord algorithmRecord = dsl.newRecord(ALGORITHM);
//        algorithmRecord.setName(algorithm.getName() != null ? algorithm.getName() : DEFAULT_ALGORITHM_NAME);
//        algorithmRecord.setAlphabet(generateAlphabet(algorithmRecord.getName(), algorithm.getAlphabet()));
//        algorithmRecord.setRandomalgorithmdesiredsize(algorithm.getRandomalgorithmdesiredsize() != null ? algorithm.getRandomalgorithmdesiredsize() : DEFAULT_RANDOM_ALGORITHM_DESIRED_SIZE);
//        algorithmRecord.setRandomalgorithmdesiredsuccessprobability(algorithm.getRandomalgorithmdesiredsuccessprobability() != null ? algorithm.getRandomalgorithmdesiredsuccessprobability() : DEFAULT_RANDOM_ALGORITHM_DESIRED_SUCCESS_PROBABILITY);
//        algorithmRecord.setConsecutivevaluecounter(algorithm.getConsecutivevaluecounter() != null ? algorithm.getConsecutivevaluecounter() : DEFAULT_CONSECUTIVE_VALUE_COUNTER);
//		algorithmRecord.setPseudonymlength(algorithm.getPseudonymlength() != null ? algorithm.getPseudonymlength() : DEFAULT_PSEUDONYM_LENGTH);
//		algorithmRecord.setPaddingcharacter(algorithm.getPaddingcharacter() != null ? algorithm.getPaddingcharacter() : DEFAULT_PADDING_CHARACTER);
//		algorithmRecord.setAddcheckdigit(algorithm.getAddcheckdigit() != null ? algorithm.getAddcheckdigit() : DEFAULT_ADD_CHECK_DIGIT);
//		algorithmRecord.setLengthincludescheckdigit(algorithm.getLengthincludescheckdigit() != null ? algorithm.getLengthincludescheckdigit() : DEFAULT_LENGTH_INCLUDES_CHECK_DIGIT);
//		algorithmRecord.setSalt(sanitizeOrGenerateSalt(algorithm.getSalt(), saltLength));
//		algorithmRecord.setSaltlength(saltLength);
//	
//    	// Store and determine success
//        int wasStored = algorithmRecord.insert();
//        log.debug("Creating the algorithm object \"" + algorithm.getName() + "\" " + ((wasStored == 1) ? "succeeded." : "failed."));
//        
//        // Return the new algorithm ID
//        return wasStored == 1 ? algorithmRecord.getId() : null;
//    }
//    
//	/**
//     * Creates an algorithm object in the database if it does not already exist.
//     * 
//     * @param algorithm the algorithm POJO to store
//     * @return the ID of the (already) created algorithm, or {@code null} if an error occurred.
//     */
//    @Transactional
//    public Integer createOrGetAlgorithm(Algorithm algorithm) {
//    	Integer id = getAlgorithmIdIfExistsInDatabase(algorithm);
//    	
//    	if (id != null) {
//    		log.debug("Algorithm already exists in the database. Returning ID instead of creating it anew.");
//    		return id;
//    	} else {
//    		return createAlgorithm(algorithm);
//    	}
//    }
//    
//    /**
//     * Deletes the algorithm object based on its unique ID.
//     * 
//     * @param ID the ID corresponding to the algorithm object of interest
//     * @return {@code true} if the deletion was successful, {@code false} otherwise
//     * @throws UnexpectedResultSizeException whenever the deletion would not exactly affect one entry
//     */
//    @Transactional
//    public boolean deleteAlgorithm(int ID) throws UnexpectedResultSizeException {
//    	if (ID <= 0) {
//    		log.debug("Cannot delete the algorithm from the database with an ID <= 0 (given ID: " + ID + ").");
//    		return false;
//    	}
//    	
//    	// Check if any person object still uses this algorithm object
//    	if (getNumberOfPersonsUsingAlgorithm(ID) != 0) { 
//    		log.debug("The algorithm is still in use and is therefore not deleted.");
//    		return false;
//    	}
//    	
//    	int deletedEntries = dsl.deleteFrom(ALGORITHM).where(ALGORITHM.ID.eq(ID)).execute();
//    	if (deletedEntries != 1) {
//    		// Deletion would not affect exactly one entry -> abort
//    		log.debug("Deletion of algorithm with ID " + ID + " would not affect exactly one entry. Aborting.");
//    		throw new UnexpectedResultSizeException(1, deletedEntries);
//    	}
//    	
//    	return true;
//    }
//	
//	/**
//	 * Method to generate the alphabet depending on the given algorithm.
//	 * 
//	 * @param algorithm the user-given algorithm
//	 * @param alphabet the alphabet provided by the user, if available
//	 * @return the alphabet that matches the algorithm as a String
//	 */
//	private String generateAlphabet(String algorithm, String alphabet) {
//		// The possible alphabets
//		String HEXADECIMAL_ALPHABET = "ABCDEF0123456789";
//		String NUMBERS_ONLY_ALPHABET = "0123456789";
//		String LETTERS_ONLY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
//		String LETTERS_AND_NUMBERS_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
//		String LETTERS_AND_NUMBERS_WITHOUT_BIOS_ALPHABET = "ACDEFGHJKLMNPQRTUVWXYZ0123456789";
//		
//		// If nothing was provided, return the default alphabet
//		if (algorithm == null || algorithm.isBlank()) {
//			return DEFAULT_ALPHABET;
//		}
//		
//		// Generate alphabet depending on the used algorithm
//		switch (algorithm.trim().toUpperCase()) {
//	        case "MD5":
//	        case "SHA1":
//	        case "SHA2":
//	        case "SHA3":
//	        case "BLAKE3":
//	        case "XXHASH": {
//	        	return HEXADECIMAL_ALPHABET;
//	        }
//	        case "CONSECUTIVE":
//	        case "RANDOM_NUM": {
//	        	return NUMBERS_ONLY_ALPHABET;
//	        }
//	        case "RANDOM": {
//	        	// If "RANDOM" was selected, use the user-provided alphabet or A-Z0-9 if nothing was provided
//	        	return (alphabet != null && !alphabet.isBlank()) ? alphabet : LETTERS_AND_NUMBERS_ALPHABET;
//	        }
//	        case "RANDOM_HEX": {
//	        	return HEXADECIMAL_ALPHABET;
//	        }
//	        case "RANDOM_LET": {
//	        	return LETTERS_ONLY_ALPHABET;
//	        }
//	        case "RANDOM_SYM": {
//	        	return LETTERS_AND_NUMBERS_ALPHABET;
//	        }
//	        case "RANDOM_SYM_BIOS": {
//	        	return LETTERS_AND_NUMBERS_WITHOUT_BIOS_ALPHABET;
//	        }
//	        default: {
//	            // Unrecognized algorithm
//	            log.debug("The pseudonymization algorithm that was requested (" + algorithm + ") wasn't recognized.");
//	            return DEFAULT_ALPHABET;
//	        }
//		}
//	}
//    
//    /**
//     * Retrieves the algorithm object based on its unique ID.
//     * 
//     * @param ID the ID corresponding to the algorithm object of interest
//     * @return the algorithm POJO, or {@code null} if nothing could be found
//     */
//    @Transactional
//    public Algorithm getAlgorithmByID(int ID) {
//    	if (ID <= 0) {
//    		log.debug("Cannot retrieve the algorithm from the database with an ID <= 0 (given ID: " + ID + ").");
//    		return null;
//    	}
//    	
//    	Algorithm algo = null;
//        try {
//	        // Execute the query
//	        algo = dsl.selectFrom(ALGORITHM)
//	                  .where(ALGORITHM.ID.eq(ID))
//	                  .fetchOneInto(Algorithm.class);
//        } catch (TooManyRowsException e) {
//        	log.debug("Found more than one algorithm.");
//    	} catch (MappingException f) {
//        	log.debug("Could not map the algorithm search result into the Algorithm-POJO.");
//        } catch (DataAccessException g) {
//        	log.debug("Searching for the algorithm in the database failed: " + g.getMessage());
//        }
//        
//        // Check if the search was successful
//        if (algo == null) {
//        	log.debug("No single algorithm could be found with the given ID.");
//        }
//    	
//    	return algo;
//    }
//    
//    /**
//     * Method to retrieve an algorithm-object from the database.
//     * If there is more than one result, this method will return 
//     * an arbitrary one that fits the search criteria.
//     * 
//     * @param name the name of the algorithm
//     * @param alphabet the alphabet used in the algorithm
//     * @param randomAlgoDesiredSize the desired number of possible pseudonyms in the output space of a randomness-based algorithm
//     * @param randomAlgoDesiredSuccessProbability the desired success probability for a randomness-based algorithm
//     * @param pseudonymLength the length of the pseudonyms
//     * @param paddingChar the character used to pad short pseudonyms to the desired length
//     * @param addCheckDigit whether or not to add a check digit to the pseudonym
//     * @param lengthIncludesCheckDigit whether or not the desired length includes the check digit
//     * @param salt the salt used for pseudonymization
//     * @param saltLength the length of the salt value
//     * @return the algorithm object that was found when searching for the given attributes, or {@code null} when nothing was found
//     */
//    @Transactional
//    public Algorithm getAlgorithmByValues(String name, String alphabet, Long randomAlgoDesiredSize, Double randomAlgoDesiredSuccessProbability, Integer pseudonymLength, String paddingChar, Boolean addCheckDigit, Boolean lengthIncludesCheckDigit, String salt, Integer saltLength) {
//    	// Build the query based on the given non-null attributes
//        Condition condition = DSL.trueCondition();
//        if (name != null) {
//            condition = condition.and(ALGORITHM.NAME.eq(name));
//        }
//        if (alphabet != null) {
//            condition = condition.and(ALGORITHM.ALPHABET.eq(alphabet));
//        }
//        if (randomAlgoDesiredSize != null) {
//            condition = condition.and(ALGORITHM.RANDOMALGORITHMDESIREDSIZE.eq(randomAlgoDesiredSize));
//        }
//        if (randomAlgoDesiredSuccessProbability != null) {
//            condition = condition.and(ALGORITHM.RANDOMALGORITHMDESIREDSUCCESSPROBABILITY.eq(randomAlgoDesiredSuccessProbability));
//        }
//        if (pseudonymLength != null) {
//            condition = condition.and(ALGORITHM.PSEUDONYMLENGTH.eq(pseudonymLength));
//        }
//        if (paddingChar != null) {
//            condition = condition.and(ALGORITHM.PADDINGCHARACTER.eq(paddingChar));
//        }
//        if (addCheckDigit != null) {
//            condition = condition.and(ALGORITHM.ADDCHECKDIGIT.eq(addCheckDigit));
//        }
//        if (lengthIncludesCheckDigit != null) {
//            condition = condition.and(ALGORITHM.LENGTHINCLUDESCHECKDIGIT.eq(lengthIncludesCheckDigit));
//        }
//        if (salt != null) {
//            condition = condition.and(ALGORITHM.SALT.eq(salt));
//        }
//        if (saltLength != null) {
//            condition = condition.and(ALGORITHM.SALTLENGTH.eq(saltLength));
//        }
//
//        List<Algorithm> algos = null;
//        try {
//	        // Execute the query
//	        algos = dsl.selectFrom(ALGORITHM)
//	                  .where(condition)
//	                  .fetchInto(Algorithm.class);
//        } catch (MappingException e) {
//        	log.debug("Could not map the algorithm search result into the Algorithm-POJO.");
//        } catch (DataAccessException f) {
//        	log.debug("Searching for the algorithm in the database failed: " + f.getMessage());
//        }
//        
//        // Check if the search was successful
//        if (algos == null || algos.size() == 0) {
//        	log.debug("No algorithm could be found with the given set of attributes.");
//        	return null;
//        } else if (algos.size() > 1) {
//        	log.debug("More than one algorithm object was found. The first result will be used.");
//        }
//        
//        return algos.getFirst();
//    }
//    
//    /**
//     * Check if the algorithm is already in the database.
//     * 
//     * @param algorithm the algorithm object to check
//     * @return the id of the algorithm in the database that is equivalent to the given one, or
//     * {@code null} if nothing was found or an error occurred.
//     */
//    @Transactional
//    private Integer getAlgorithmIdIfExistsInDatabase(Algorithm algorithm) {
//    	Integer id = null;
//    	
//    	try {
//    		id = dsl.select(ALGORITHM.ID)
//		        .from(ALGORITHM)
//		        .where(
//		            ALGORITHM.NAME.eq(algorithm.getName())
//		            .and(ALGORITHM.ALPHABET.eq(algorithm.getAlphabet()))
//		            .and(ALGORITHM.RANDOMALGORITHMDESIREDSIZE.eq(algorithm.getRandomalgorithmdesiredsize()))
//		            .and(ALGORITHM.RANDOMALGORITHMDESIREDSUCCESSPROBABILITY.eq(algorithm.getRandomalgorithmdesiredsuccessprobability()))
//		            .and(ALGORITHM.CONSECUTIVEVALUECOUNTER.eq(algorithm.getConsecutivevaluecounter()))
//		            .and(ALGORITHM.PSEUDONYMLENGTH.eq(algorithm.getPseudonymlength()))
//		            .and(ALGORITHM.PADDINGCHARACTER.eq(algorithm.getPaddingcharacter()))
//		            .and(ALGORITHM.ADDCHECKDIGIT.eq(algorithm.getAddcheckdigit()))
//		            .and(ALGORITHM.LENGTHINCLUDESCHECKDIGIT.eq(algorithm.getLengthincludescheckdigit()))
//		            .and(ALGORITHM.SALT.eq(algorithm.getSalt()))
//		            .and(ALGORITHM.SALTLENGTH.eq(algorithm.getSaltlength()))
//		        )
//		        .fetchOneInto(Integer.class); // returns null if no match is found
//    	} catch (TooManyRowsException e) {
//    	    log.debug("Too many entries found while searching for an algorithm object that should be unique: " + e.getMessage());
//    	} catch (MappingException f) {
//    		log.debug("Could not convert algorithm-id-search result into an integer: " + f.getMessage());
//    	} catch (DataAccessException g) {
//    	    log.debug("Could not retrieve the algorithm ID from database: " + g.getMessage());
//    	}
//    	
//    	return id;
//    }
//    
//    /**
//     * Helper method to retrieve the number of person objects 
//     * that are referencing the algorithm given by its ID.
//     * 
//     * @param algorithmID the algorithm's ID
//     * @return the number of person objects using the algorithm
//     */
//    public int getNumberOfPersonsUsingAlgorithm(int algorithmID) {
//    	return dsl.fetchCount(PERSON, PERSON.IDENTIFIERALGORITHM.eq(algorithmID));
//    }
//    
//    /**
//     * Helper method that determines if the given salt value is valid or not.
//     * @param salt the salt value to check
//     * @return {@code true} if the salt value is not empty and within the right length-constraints, {@code false} otherwise
//     */
//    private boolean isSaltValueValid(String salt) {
//    	return salt != null && !salt.isBlank() && salt.length() >= MINIMUM_SALT_LENGTH && salt.length() <= MAXIMUM_SALT_LENGTH;
//    }
//    
//    /**
//     * Returns a valid salt value by either returning the user-given one if it's valid or by generating a new one.
//     * 
//     * @param salt the user-given salt or {@code null} if not given
//     * @param saltLength the length for the salt value
//     * @return a valid salt value
//     */
//    private String sanitizeOrGenerateSalt(String salt, int saltLength) {
//    	// Create a salt if not already given, or if its not a valid value
//        if (isSaltValueValid(salt)) {
//        	String saltAlphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_+";
//	        SecureRandom rnd = new SecureRandom();
//	        StringBuilder sb = new StringBuilder(saltLength);
//	
//	        for (int i = 0; i < saltLength; i++) {
//	            sb.append(saltAlphabet.charAt(rnd.nextInt(saltAlphabet.length())));
//	        }
//	        
//	        log.debug("The given salt was null, too long, or too short. A new one was generated.");
//	        return sb.toString();
//        } else {
//        	return salt;
//        }
//    }
//    
//	/**
//     * Updates an algorithm object in the database.
//     * Null values in the updateAlgorithm object indicate that this specific
//     * attribute is to be kept as before.
//     * 
//     * @param oldAlgorithm the old algorithm POJO that should be updated
//     * @param updatedAlgorithm the algorithm POJO that contains all the updated values
//     * @return the ID of the updated algorithm, or {@code null} if an error occurred
//     */
//    @Transactional
//    public Integer updateAlgorithm(Algorithm oldAlgorithm, Algorithm updatedAlgorithm) {
//		// Fetch old algorithm object
//    	AlgorithmRecord algorithmRecord = null;
//    	try {
//			algorithmRecord = dsl.fetchOne(ALGORITHM, ALGORITHM.ID.eq(oldAlgorithm.getId()));
//		} catch (DataAccessException e) {
//			log.debug("Fetching the algorithm record that should be updated failed (ID: " + oldAlgorithm.getId() + ").");
//			return null;
//		}
//		
//		// Check if the old record was found
//		if (algorithmRecord == null) {
//			log.debug("The algorithm object that should be updated was not found (ID: " + oldAlgorithm.getId() + ").");
//			return null;
//		}
//		
//		// Sanitize the given values and update the attributes
//        algorithmRecord.setName(updatedAlgorithm.getName() != null && !updatedAlgorithm.getName().isBlank() ? updatedAlgorithm.getName() : oldAlgorithm.getName());
//        algorithmRecord.setAlphabet(updatedAlgorithm.getAlphabet() != null && !updatedAlgorithm.getName().isBlank() ? updatedAlgorithm.getAlphabet() : oldAlgorithm.getAlphabet());
//        algorithmRecord.setRandomalgorithmdesiredsize(updatedAlgorithm.getRandomalgorithmdesiredsize() != null && updatedAlgorithm.getRandomalgorithmdesiredsize() >= 1 ? updatedAlgorithm.getRandomalgorithmdesiredsize() : oldAlgorithm.getRandomalgorithmdesiredsize());
//        algorithmRecord.setRandomalgorithmdesiredsuccessprobability(updatedAlgorithm.getRandomalgorithmdesiredsuccessprobability() != null && updatedAlgorithm.getRandomalgorithmdesiredsuccessprobability() > 0 ? updatedAlgorithm.getRandomalgorithmdesiredsuccessprobability() : oldAlgorithm.getRandomalgorithmdesiredsuccessprobability());
//        algorithmRecord.setConsecutivevaluecounter(updatedAlgorithm.getConsecutivevaluecounter() != null && updatedAlgorithm.getConsecutivevaluecounter() >= 1 ? updatedAlgorithm.getConsecutivevaluecounter() : oldAlgorithm.getConsecutivevaluecounter());
//		algorithmRecord.setPseudonymlength(updatedAlgorithm.getPseudonymlength() != null && updatedAlgorithm.getPseudonymlength() >= 1 ? updatedAlgorithm.getPseudonymlength() : oldAlgorithm.getPseudonymlength());
//		algorithmRecord.setPaddingcharacter(updatedAlgorithm.getPaddingcharacter() != null && !updatedAlgorithm.getPaddingcharacter().isBlank() ? updatedAlgorithm.getPaddingcharacter() : oldAlgorithm.getPaddingcharacter());
//		algorithmRecord.setAddcheckdigit(updatedAlgorithm.getAddcheckdigit() != null ? updatedAlgorithm.getAddcheckdigit() : oldAlgorithm.getAddcheckdigit());
//		algorithmRecord.setLengthincludescheckdigit(updatedAlgorithm.getLengthincludescheckdigit() != null ? updatedAlgorithm.getLengthincludescheckdigit() : oldAlgorithm.getLengthincludescheckdigit());
//		algorithmRecord.setSalt(isSaltValueValid(updatedAlgorithm.getSalt()) ? updatedAlgorithm.getSalt() : oldAlgorithm.getSalt());
//		algorithmRecord.setSaltlength((updatedAlgorithm.getSaltlength() != null && updatedAlgorithm.getSaltlength() >= MINIMUM_SALT_LENGTH && updatedAlgorithm.getSaltlength() <= MAXIMUM_SALT_LENGTH) ? updatedAlgorithm.getSaltlength() : oldAlgorithm.getSaltlength());
//	
//    	// Store and determine success
//        int wasStored = algorithmRecord.update();
//        log.debug("Updating the algorithm object \"" + algorithmRecord.getName() + "\" (ID: " + algorithmRecord.getId() + ") " + ((wasStored == 1) ? "succeeded." : "failed."));
//        
//        // Return the algorithm ID when successful
//        return wasStored == 1 ? algorithmRecord.getId() : null;
//    }
//}

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

package org.trustdeck.dto;

import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for the exchange of algorithm data.
 *
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlgorithmDTO implements IObjectDTO<Algorithm, AlgorithmDTO> {
	
	/** The (internal) id of the algorithm object. */
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private Integer id;
	
	/** The name given to this algorithm object. */
	private String name;
	
	/** The alphabet used in creating the pseudonyms. */
	private String alphabet;
	
	/** The desired number of possible pseudonyms when using a randomness-based algorithm. */
    private long randomAlgorithmDesiredSize;
	
	/** The desired probability with which the pseudonymization should be successful when using a randomness-based algorithm. */
    private double randomAlgorithmDesiredSuccessProbability;
	
	/** The current value of the counter when using a consecutive value-based algorithm or when multiple pseudonyms per identifier are allowed. */
    private long consecutiveValueCounter;
	
	/** The length the pseudonym should have. */
    private int pseudonymLength;
	
	/** The character that should be used for padding if needed. */
    private String paddingCharacter;
	
	/** Whether or not to add a check digit to the pseudonym. */
    private boolean addCheckDigit;
	
	/** Whether or not the desired pseudonym length should include the check digit (thus, the check digit replaces the last character of the generated pseudonym). */
    private boolean lengthIncludesCheckDigit;
	
	/** The salt value. */
    private String salt;
	
	/** The desired length of the salt value. */
    private int saltLength;
	
	@JsonIgnore
	@Override
	public AlgorithmDTO assignPojoValues(Algorithm pojo) {
		if (pojo == null) {
	        return null;
	    }
	    
	    this.setId(pojo.getId());
	    this.setName(pojo.getName());
	    this.setAlphabet(pojo.getAlphabet());
	    this.setRandomAlgorithmDesiredSize(pojo.getRandomalgorithmdesiredsize());
	    this.setRandomAlgorithmDesiredSuccessProbability(pojo.getRandomalgorithmdesiredsuccessprobability());
	    this.setConsecutiveValueCounter(pojo.getConsecutivevaluecounter());
	    this.setPseudonymLength(pojo.getPseudonymlength());
	    this.setPaddingCharacter(pojo.getPaddingcharacter());
	    this.setAddCheckDigit(pojo.getAddcheckdigit());
	    this.setLengthIncludesCheckDigit(pojo.getLengthincludescheckdigit());
	    this.setSalt(pojo.getSalt());
	    this.setSaltLength(pojo.getSaltlength());
	    
	    return this;
	}
	
	@JsonIgnore
	public Algorithm convertToPOJO() {
		Algorithm algorithm = new Algorithm();
		algorithm.setId(this.getId());
		algorithm.setName(this.getName());
		algorithm.setAlphabet(this.getAlphabet());
		algorithm.setRandomalgorithmdesiredsize(this.getRandomAlgorithmDesiredSize());
		algorithm.setRandomalgorithmdesiredsuccessprobability(this.getRandomAlgorithmDesiredSuccessProbability());
		algorithm.setConsecutivevaluecounter(this.getConsecutiveValueCounter());
		algorithm.setPseudonymlength(this.getPseudonymLength());
		algorithm.setPaddingcharacter(this.getPaddingCharacter());
		algorithm.setAddcheckdigit(this.isAddCheckDigit());
		algorithm.setLengthincludescheckdigit(this.isLengthIncludesCheckDigit());
		algorithm.setSalt(this.getSalt());
		algorithm.setSaltlength(this.getSaltLength());
		
        return algorithm;
    }
	
	@JsonIgnore
	@Override
	public Boolean isValidStandardView() {
		// TODO Auto-generated method stub
		return null;
	}

	@JsonIgnore
	@Override
	public AlgorithmDTO toReducedStandardView() {
		// TODO Auto-generated method stub
		return null;
	}

	@JsonIgnore
	@Override
	public String toRepresentationString() {
		// TODO Auto-generated method stub
		return null;
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		// TODO Auto-generated method stub
		return null;
	}

}

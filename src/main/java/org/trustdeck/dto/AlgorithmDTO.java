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

@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlgorithmDTO implements IObjectDTO<Algorithm, AlgorithmDTO> {
	
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private Integer id;
	private String name;
	private String alphabet;
    private long randomAlgorithmDesiredSize;
    private double randomAlgorithmDesiredSuccessProbability;
    private long consecutiveValueCounter;
    private int pseudonymLength;
    private String paddingCharacter;
    private boolean addCheckDigit;
    private boolean lengthIncludesCheckDigit;
    private String salt;
    private int saltLength;
	
	@JsonIgnore
	@Override
	public AlgorithmDTO assignPojoValues(Algorithm pojo) {
		if (pojo == null) {
	        return null;
	    }

	    AlgorithmDTO dto = new AlgorithmDTO();
	    
	    dto.setId(pojo.getId());
	    dto.setName(pojo.getName());
	    dto.setAlphabet(pojo.getAlphabet());
	    dto.setRandomAlgorithmDesiredSize(pojo.getRandomalgorithmdesiredsize());
	    dto.setRandomAlgorithmDesiredSuccessProbability(pojo.getRandomalgorithmdesiredsuccessprobability());
	    dto.setConsecutiveValueCounter(pojo.getConsecutivevaluecounter());
	    dto.setPseudonymLength(pojo.getPseudonymlength());
	    dto.setPaddingCharacter(pojo.getPaddingcharacter());
	    dto.setAddCheckDigit(pojo.getAddcheckdigit());
	    dto.setLengthIncludesCheckDigit(pojo.getLengthincludescheckdigit());
	    dto.setSalt(pojo.getSalt());
	    dto.setSaltLength(pojo.getSaltlength());
	    
	    return dto;
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

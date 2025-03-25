/*
 * KING - Key Index of Names and General Identification Numbers
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
import org.trustdeck.jooq.generated.tables.pojos.Person;
import org.trustdeck.service.AlgorithmDBService;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonDTO implements IObjectDTO<Person, PersonDTO> {

	private Integer id;
    private String firstName;
    private String lastName;
    private String birthName;
    private String administrativeGender;
    private String dateOfBirth;
    private String street;
    private String postalCode;
    private String city;
    private String country;
    private String identifier;
    private String idType;
    private AlgorithmDTO algorithm;
    
    @JsonIgnore
    @Autowired
    private AlgorithmDBService algorithmDBService;

	@JsonIgnore
	@Override
	public PersonDTO assignPojoValues(Person pojo) {
		if (pojo == null) {
	        return null;
	    }
		
	    DateTimeFormatter dobFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	    PersonDTO dto = new PersonDTO();
	    
	    dto.setId(pojo.getId());
	    dto.setFirstName(pojo.getFirstname());
	    dto.setLastName(pojo.getLastname());
	    dto.setBirthName(pojo.getBirthname());
	    dto.setAdministrativeGender(pojo.getAdministrativegender());
	    dto.setDateOfBirth(pojo.getDateofbirth() != null ? pojo.getDateofbirth().format(dobFormatter) : null);
	    dto.setStreet(pojo.getStreet());
	    dto.setPostalCode(pojo.getPostalcode());
	    dto.setCity(pojo.getCity());
	    dto.setIdentifier(pojo.getIdentifier());
	    dto.setIdType(pojo.getIdtype());

	    // Insert the algorithm if the identifierAlgorithm (the ID of the used algorithm) is not null
	    if (pojo.getIdentifieralgorithm() != null) {
	    	Algorithm algo = algorithmDBService.getAlgorithmByID(pojo.getIdentifieralgorithm());
	        dto.setAlgorithm(new AlgorithmDTO().assignPojoValues(algo));
	    }

	    return dto;
	}
	
	@JsonIgnore
	public Person convertToPOJO() {
        DateTimeFormatter dobFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		
        Person person = new Person();
        person.setId(this.getId());
        person.setFirstname(this.getFirstName());
        person.setLastname(this.getLastName());
        person.setBirthname(this.getBirthName());
        person.setAdministrativegender(this.getAdministrativeGender());
        person.setDateofbirth(LocalDate.parse(this.getDateOfBirth(), dobFormatter));
        person.setStreet(this.getStreet());
        person.setPostalcode(this.getPostalCode());
        person.setCity(this.getCity());
        person.setIdentifier(this.getIdentifier());
        person.setIdtype(this.getIdType());
        person.setIdentifieralgorithm(this.getAlgorithm().getId());
        
        return person;
    }

	@JsonIgnore
	@Override
	public Boolean isValidStandardView() {
		// TODO Auto-generated method stub
		return null;
	}

	@JsonIgnore
	@Override
	public PersonDTO toReducedStandardView() {
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

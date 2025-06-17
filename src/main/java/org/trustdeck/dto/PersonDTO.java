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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.Algorithm;
import org.trustdeck.jooq.generated.tables.pojos.Person;
import org.trustdeck.service.AlgorithmDBService;
import org.trustdeck.utils.Assertion;
import org.trustdeck.utils.SpringBeanLocator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * This class offers an Data Transfer Object (DTO) for a person.
 *
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersonDTO implements IObjectDTO<Person, PersonDTO> {
	
	/** The (internal) ID of this person. Do not expose it to users. */
	@JsonIgnore
	private Integer id;
	
	/** The person's first (given) name(s). */
    private String firstName;
	
	/** The person's last (family) name. */
    private String lastName;
	
	/** The person's previous last (family) name (e.g. before marriage). */
    private String birthName;
	
	/** The person's administrative gender. */
    private String administrativeGender;
	
	/** The person's birth date. */
    private String dateOfBirth;
	
	/** The street and house number part of the person's primary address. */
    private String street;
	
	/** The postal (ZIP) code for the person's primary address. */
    private String postalCode;
	
	/** The city where the person lives (primarily) in. */
    private String city;
	
	/** The country in which the person lives (primarily). */
    private String country;
	
	/** An (external) identifier for this person (e.g. a pseudonym, a SAP-ID, ...). */
    private String identifier;
	
	/** The type of the person's (external) identifier. */
    private String idType;
	
	/** The algorithm which was used to generate the identifier (pseudonym) for the person. */
    private AlgorithmDTO algorithm;
	
	/** Allows interaction with the algorithm objects in the database. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
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
	    dto.setCountry(pojo.getCountry());
	    dto.setIdentifier(pojo.getIdentifier());
	    dto.setIdType(pojo.getIdtype());

	    // Insert the algorithm if the identifierAlgorithm (the ID of the used algorithm) is not null
	    if (pojo.getIdentifieralgorithm() != null) {
	    	if (this.algorithmDBService == null) {
	    		this.algorithmDBService = SpringBeanLocator.getBean(AlgorithmDBService.class);
	    	}
	    	
	    	Algorithm algo = algorithmDBService.getAlgorithmByID(pojo.getIdentifieralgorithm());
	        dto.setAlgorithm(new AlgorithmDTO().assignPojoValues(algo));
	    }

	    return dto;
	}
	
	/**
	 * This method transforms a person DTO into a plain java object.
	 * 
	 * @return
	 */
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
        person.setCountry(this.getCountry());
        person.setIdentifier(this.getIdentifier());
        person.setIdtype(this.getIdType());
        person.setIdentifieralgorithm(this.getAlgorithm().getId());
        
        return person;
    }

	@JsonIgnore
	@Override
	public Boolean isValidStandardView() {
		// Currently not needed
		return null;
	}

	@JsonIgnore
	@Override
	public PersonDTO toReducedStandardView() {
		// Currently not needed
		return null;
	}

	@JsonIgnore
	@Override
	public String toRepresentationString() {
	    String out = "";

	    out += (this.getId() != null) ? "id: " + this.getId().toString() + ", " : "";
	    out += (this.getFirstName() != null) ? "firstName: " + this.getFirstName() + ", " : "";
	    out += (this.getLastName() != null) ? "lastName: " + this.getLastName() + ", " : "";
	    out += (this.getBirthName() != null) ? "birthName: " + this.getBirthName() + ", " : "";
	    out += (this.getAdministrativeGender() != null) ? "administrativeGender: " + this.getAdministrativeGender() + ", " : "";
	    out += (this.getDateOfBirth() != null) ? "dateOfBirth: " + this.getDateOfBirth() + ", " : "";
	    out += (this.getStreet() != null) ? "street: " + this.getStreet() + ", " : "";
	    out += (this.getPostalCode() != null) ? "postalCode: " + this.getPostalCode() + ", " : "";
	    out += (this.getCity() != null) ? "city: " + this.getCity() + ", " : "";
	    out += (this.getCountry() != null) ? "country: " + this.getCountry() + ", " : "";
	    out += (this.getIdentifier() != null) ? "identifier: " + this.getIdentifier() + ", " : "";
	    out += (this.getIdType() != null) ? "idType: " + this.getIdType() + ", " : "";
	    //out += (this.getAlgorithm() != null) ? "algorithm: {" + this.getAlgorithm().toRepresentationString() + "}, " : "";

	    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		return Assertion.assertNotNullAll(this.getFirstName(), this.getLastName(), this.getDateOfBirth());
	}

}

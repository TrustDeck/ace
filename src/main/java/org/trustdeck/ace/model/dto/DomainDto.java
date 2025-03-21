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

package org.trustdeck.ace.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.context.annotation.Scope;
import org.trustdeck.ace.jooq.generated.tables.interfaces.IDomain;
import org.trustdeck.ace.jooq.generated.tables.pojos.Domain;
import org.trustdeck.ace.service.DomainDBAccessService;
import org.trustdeck.ace.utils.Assertion;
import org.trustdeck.ace.utils.SpringBeanLocator;

import java.time.LocalDateTime;

/**
 * Object for the representation (transfer) in a response
 *
 * @author Armin Müller & Eric Wündisch
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainDto implements IRepresentation<IDomain, DomainDto> {
	
	/** Enables the access to the domain specific database access methods. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
    @JsonIgnore
    DomainDBAccessService domainDBAccessService = SpringBeanLocator.getBean(DomainDBAccessService.class);
	
    /** The id of this domain. */
    private Integer id;

    /** The name of the domain. */
    private String name;

    /** The prefix for the domain. Used in the pseudonyms. */
    private String prefix;

    /** The date (and time) when the validity period of the entry starts. */
    private LocalDateTime validFrom;

    /** Determines if the validFrom value was inherited from the super domain. */
    private Boolean validFromInherited;

    /** The date (and time) when the validity period of the entry ends. */
    private LocalDateTime validTo;
    
    /** An amount of time a domain should be valid for. (Only needed for the creation.) */
    private String validityTime;

    /** Determines if the validTo value was inherited from the super domain. */
    private Boolean validToInherited;

    /** An option to ensure that the valid-from date of entries is always after or equal to the domain one. */
    private Boolean enforceStartDateValidity;

    /** Indicates whether or not the enforceStartDateValidity option was inherited. */
    private Boolean enforceStartDateValidityInherited;

    /** An option to ensure that the valid-to date of entries is always before or equal to the domain one. */
    private Boolean enforceEndDateValidity;

    /** Indicates whether or not the enforceEndDateValidity option was inherited. */
    private Boolean enforceEndDateValidityInherited;

    /** The algorithm used for pseudonymization. */
    private String algorithm;

    /** Determines if the algorithm value was inherited from the super domain. */
    private Boolean algorithmInherited;
    
    /** The alphabet that should be used for generating new pseudonyms. */
    private String alphabet;
    
    /** Determines whether or not the alphabet value was inherited from the super domain. */
    private Boolean alphabetInherited;
    
    /** The amount of pseudonyms the user wants to be able to store in this domain. */
    private Long randomAlgorithmDesiredSize;
    
    /** Determines whether or not the desired minimal size value was inherited from the super domain. */
    private Boolean randomAlgorithmDesiredSizeInherited;
    
    /** The probability with which the algorithm should successfully generate new pseudonyms in an already partly filled domain. */
    private Double randomAlgorithmDesiredSuccessProbability;
    
    /** Determines whether or not the desired success probability value was inherited from the super domain. */
    private Boolean randomAlgorithmDesiredSuccessProbabilityInherited;
    
    /** Determines if the domain can store multiple pseudonyms per given identifier or only one (1:n vs. 1:1). */
    private Boolean multiplePsnAllowed;
    
    /** Determines whether or not the multiple allowed pseudonyms option was inherited from the super domain. */
    private Boolean multiplePsnAllowedInherited;
	
	/** A counter for consecutive numbering as the pseudonymization algorithm. */
	private Long consecutiveValueCounter;

    /** The length of the pseudonyms for this domain */
    private Integer pseudonymLength;

    /** Determines if the pseudonym length was inherited. */
    private Boolean pseudonymLengthInherited;

    /** The character that should be used for padding the pseudonyms, if necessary. */
    private Character paddingCharacter;

    /** Determines if the padding character was inherited. */
    private Boolean paddingCharacterInherited;
    
    /** Determines whether or not a check digit should be added to the pseudonym. */
    private Boolean addCheckDigit;
    
    /** Determines if the check digit is was inherited. */
    private Boolean addCheckDigitInherited;
    
    /** Determines whether or not the check digit should be part of the pseudonym length. */
    private Boolean lengthIncludesCheckDigit;
    
    /** Determines if the check digit in length inclusion status is was inherited */
    private Boolean lengthIncludesCheckDigitInherited;

    /** The salt for this domain. */
    private String salt;

    /** The salt length for this domain. */
    private Integer saltLength;

    /** A description of the domain. */
    private String description;

    /** The super-domain ID of this domain. */
    private Integer superDomainID;

    /** The super-domain name of this domain. */
    private String superDomainName;

    /**
     * Maps all values from jOOQ's Domain object to a DomainDto object.
     */
    @JsonIgnore
    @Override
    public DomainDto assignPojoValues(IDomain pojo) {
    	this.setId(pojo.getId() == null ? null : pojo.getId());
        this.setName(pojo.getName());
        this.setPrefix(pojo.getPrefix());
        this.setValidFrom(pojo.getValidfrom());
        this.setValidFromInherited(pojo.getValidfrominherited());
        this.setValidTo(pojo.getValidto());
        this.setValidToInherited(pojo.getValidtoinherited());
        this.setEnforceStartDateValidity(pojo.getEnforcestartdatevalidity());
        this.setEnforceStartDateValidityInherited(pojo.getEnforcestartdatevalidityinherited());
        this.setEnforceEndDateValidity(pojo.getEnforceenddatevalidity());
        this.setEnforceEndDateValidityInherited(pojo.getEnforceenddatevalidityinherited());
        this.setAlgorithm(pojo.getAlgorithm());
        this.setAlgorithmInherited(pojo.getAlgorithminherited());
        this.setAlphabet(pojo.getAlphabet());
        this.setAlphabetInherited(pojo.getAlphabetinherited());
        this.setRandomAlgorithmDesiredSize(pojo.getRandomalgorithmdesiredsize());
        this.setRandomAlgorithmDesiredSizeInherited(pojo.getRandomalgorithmdesiredsizeinherited());
        this.setRandomAlgorithmDesiredSuccessProbability(pojo.getRandomalgorithmdesiredsuccessprobability());
        this.setRandomAlgorithmDesiredSuccessProbabilityInherited(pojo.getRandomalgorithmdesiredsuccessprobabilityinherited());
        this.setMultiplePsnAllowed(pojo.getMultiplepsnallowed());
        this.setMultiplePsnAllowedInherited(pojo.getMultiplepsnallowedinherited());
        this.setConsecutiveValueCounter(pojo.getConsecutivevaluecounter());
        this.setPseudonymLength(pojo.getPseudonymlength());
        this.setPseudonymLengthInherited(pojo.getPseudonymlengthinherited());
        this.setPaddingCharacter(pojo.getPaddingcharacter() != null ? pojo.getPaddingcharacter().charAt(0) : null);
        this.setPaddingCharacterInherited(pojo.getPaddingcharacterinherited());
        this.setAddCheckDigit(pojo.getAddcheckdigit());
        this.setAddCheckDigitInherited(pojo.getAddcheckdigitinherited());
        this.setLengthIncludesCheckDigit(pojo.getLengthincludescheckdigit());
        this.setLengthIncludesCheckDigitInherited(pojo.getLengthincludescheckdigitinherited());
        this.setSalt(pojo.getSalt());
        this.setSaltLength(pojo.getSaltlength());
        this.setDescription(Assertion.isNotNullOrEmpty(pojo.getDescription()) ? pojo.getDescription() : null);
        Domain d = (pojo.getSuperdomainid() != null && pojo.getSuperdomainid() > 0) ? domainDBAccessService.getDomainByID(pojo.getSuperdomainid(), null) : null;
        this.setSuperDomainName(d != null ? d.getName() : null);
        this.setSuperDomainID(d != null ? d.getId() : null);
        
        return this;
    }

    @Override
    @JsonIgnore
    public Boolean isValidStandardView() {
        return Assertion.assertNullAll(this.getId(),
        		this.getValidFromInherited(),
                this.getValidToInherited(),
                this.getEnforceStartDateValidity(),
                this.getEnforceStartDateValidityInherited(),
                this.getEnforceEndDateValidity(),
                this.getEnforceEndDateValidityInherited(),
                this.getAlgorithm(),
                this.getAlgorithmInherited(),
                this.getAlphabet(),
                this.getAlphabetInherited(),
                this.getRandomAlgorithmDesiredSize(),
                this.getRandomAlgorithmDesiredSizeInherited(),
                this.getRandomAlgorithmDesiredSuccessProbability(),
                this.getRandomAlgorithmDesiredSuccessProbabilityInherited(),
                this.getMultiplePsnAllowed(),
                this.getMultiplePsnAllowedInherited(),
                this.getConsecutiveValueCounter(),
                this.getPseudonymLength(),
                this.getPseudonymLengthInherited(),
                this.getPaddingCharacter(),
                this.getPaddingCharacterInherited(),
                this.getAddCheckDigit(),
                this.getAddCheckDigitInherited(),
                this.getLengthIncludesCheckDigit(),
                this.getLengthIncludesCheckDigitInherited(),
                this.getSalt(),
                this.getSaltLength(),
                this.getSuperDomainID());
    }
    
    /**
     * This method creates a reduced standard view by setting all 
     * attributes that shouldn't be displayed by default to {@code null},
     * so that they are excluded in the JSON representation.
     * 
     * @return the altered domain DTO containing only the standard 
     * information (name, prefix, validFrom, validTo, description, 
     * superDomainName)
     */
    @Override
    @JsonIgnore
    public DomainDto toReducedStandardView() {
    	this.setId(null);
        this.setValidFromInherited(null);
        this.setValidToInherited(null);
        this.setValidityTime(null);
        this.setEnforceStartDateValidity(null);
        this.setEnforceStartDateValidityInherited(null);
        this.setEnforceEndDateValidity(null);
        this.setEnforceEndDateValidityInherited(null);
        this.setAlgorithm(null);
        this.setAlgorithmInherited(null);
        this.setAlphabet(null);
        this.setAlphabetInherited(null);
        this.setRandomAlgorithmDesiredSize(null);
        this.setRandomAlgorithmDesiredSizeInherited(null);
        this.setRandomAlgorithmDesiredSuccessProbability(null);
        this.setRandomAlgorithmDesiredSuccessProbabilityInherited(null);
        this.setMultiplePsnAllowed(null);
        this.setMultiplePsnAllowedInherited(null);
        this.setConsecutiveValueCounter(null);
        this.setPseudonymLength(null);
        this.setPseudonymLengthInherited(null);
        this.setPaddingCharacter(null);
        this.setPaddingCharacterInherited(null);
        this.setAddCheckDigit(null);
        this.setAddCheckDigitInherited(null);
        this.setLengthIncludesCheckDigit(null);
        this.setLengthIncludesCheckDigitInherited(null);
        this.setSalt(null);
        this.setSaltLength(null);
        this.setSuperDomainID(null);
    	
    	return this;
    }

    /**
     * Creates a readable string representation.
     */
    @JsonIgnore
    @Override
    public String toRepresentationString() {
        String out = "";
        out += (this.getId() != null) ? "id: " + this.getId().toString() + ", " : "";
        out += (this.getName() != null) ? "name: " + this.getName() + ", " : "";
        out += (this.getPrefix() != null) ? "prefix: " + this.getPrefix() + ", " : "";
        out += (this.getValidFrom() != null) ? "validFrom: " + this.getValidFrom().toString() + ", " : "";
        out += (this.getValidFromInherited() != null) ? "validFromInherited: " + this.getValidFromInherited() + ", " : "";
        out += (this.getValidTo() != null) ? "validTo: " + this.getValidTo().toString() + ", " : "";
        out += (this.getValidToInherited() != null) ? "validToInherited: " + this.getValidToInherited() + ", " : "";
        out += (this.getEnforceStartDateValidity() != null) ? "enforceStartDateValidity: " + this.getEnforceStartDateValidity() + ", " : "";
        out += (this.getEnforceStartDateValidityInherited() != null) ? "enforceStartDateValidityInherited: " + this.getEnforceStartDateValidityInherited() + ", " : "";
        out += (this.getEnforceEndDateValidity() != null) ? "enforceEndDateValidity: " + this.getEnforceEndDateValidity() + ", " : "";
        out += (this.getEnforceEndDateValidityInherited() != null) ? "enforceEndDateValidityInherited: " + this.getEnforceEndDateValidityInherited() + ", " : "";
        out += (this.getAlgorithm() != null) ? "algorithm: " + this.getAlgorithm() + ", " : "";
        out += (this.getAlgorithmInherited() != null) ? "algorithmInherited: " + this.getAlgorithmInherited() + ", " : "";
        out += (this.getAlphabet() != null) ? "alphabet: " + this.getAlphabet() + ", " : "";
        out += (this.getAlphabetInherited() != null) ? "alphabetInherited: " + this.getAlphabetInherited() + ", " : "";
        out += (this.getRandomAlgorithmDesiredSize() != null) ? "randomAlgorithmDesiredSize: " + this.getRandomAlgorithmDesiredSize().toString() + ", " : "";
        out += (this.getRandomAlgorithmDesiredSizeInherited() != null) ? "randomAlgorithmDesiredSizeInherited: " + this.getRandomAlgorithmDesiredSizeInherited() + ", " : "";
        out += (this.getRandomAlgorithmDesiredSuccessProbability() != null) ? "randomAlgorithmDesiredSuccessProbability: " + this.getRandomAlgorithmDesiredSuccessProbability().toString() + ", " : "";
        out += (this.getRandomAlgorithmDesiredSuccessProbabilityInherited() != null) ? "randomAlgorithmDesiredSuccessProbabilityInherited: " + this.getRandomAlgorithmDesiredSuccessProbabilityInherited() + ", " : "";
        out += (this.getMultiplePsnAllowed() != null) ? "multiplePsnAllowed: " + this.getMultiplePsnAllowed() + ", " : "";
        out += (this.getMultiplePsnAllowedInherited() != null) ? "multiplePsnAllowedInherited: " + this.getMultiplePsnAllowedInherited() + ", " : "";
        out += (this.getConsecutiveValueCounter() != null) ? "consecutiveValueCounter: " + this.getConsecutiveValueCounter().toString() + ", " : "";
        out += (this.getPseudonymLength() != null) ? "pseudonymLength: " + this.getPseudonymLength().toString() + ", " : "";
        out += (this.getPseudonymLengthInherited() != null) ? "pseudonymLengthInherited: " + this.getPseudonymLengthInherited() + ", " : "";
        out += (this.getPaddingCharacter() != null) ? "paddingCharacter: " + this.getPaddingCharacter() + ", " : "";
        out += (this.getPaddingCharacterInherited() != null) ? "paddingCharacterInherited: " + this.getPaddingCharacterInherited() + ", " : "";
        out += (this.getAddCheckDigit() != null) ? "addCheckDigit: " + this.getAddCheckDigit() + ", " : "";
        out += (this.getAddCheckDigitInherited() != null) ? "addCheckDigitInherited: " + this.getAddCheckDigitInherited() + ", " : "";
        out += (this.getLengthIncludesCheckDigit() != null) ? "lengthIncludesCheckDigit: " + this.getLengthIncludesCheckDigit() + ", " : "";
        out += (this.getLengthIncludesCheckDigitInherited() != null) ? "lengthIncludesCheckDigitInherited: " + this.getLengthIncludesCheckDigitInherited() + ", " : "";
        out += (this.getSalt() != null) ? "salt: " + this.getSalt() + ", " : "";
        out += (this.getSaltLength() != null) ? "saltLength: " + this.getSaltLength() + ", " : "";
        out += (this.getDescription() != null) ? "description: " + this.getDescription() + ", " : "";
        out += (this.getSuperDomainID() != null) ? "superDomainID: " + this.getSuperDomainID() + ", " : "";
        out += (this.getSuperDomainName() != null) ? "superDomainName: " + this.getSuperDomainName() + ", " : "";

        return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
    }

    @Override
    @JsonIgnore
    public Boolean validate() {
        if (this.getName() == null || this.getName().trim().equals("") || this.getPrefix() == null) {
            return false;
        }

        return true;
    }
}

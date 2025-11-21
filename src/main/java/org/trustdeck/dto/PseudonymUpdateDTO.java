/*
 * Trust Deck Services
 * Copyright 2022-2025 Armin Müller & Eric Wündisch
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

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.model.IdentifierItem;
import org.trustdeck.utils.Assertion;
import java.time.LocalDateTime;

/**
 * This class offers an Data Transfer Object (DTO) for updating a pseudonym.
 *
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class PseudonymUpdateDTO {

    /** Stores the current identifier for that entry. */
    private IdentifierItem oldIdentifierItem;

    /** The current pseudonym belonging to the identifier. */
    private String oldPsn;

    /** The current name of the domain this record belongs to. */
    private Domain oldDomain;

    /** UDPATED DATA */
    /** Stores the identifier for that entry. */
    private IdentifierItem newIdentifierItem;

    /** The pseudonym belonging to the identifier. */
    private String newPsn;

    /** The date (and time) when the validity period of the entry starts. */
    private LocalDateTime validFrom;

    /** Determines if the validFrom value was inherited from the super domain. */
    private Boolean validFromInherited;

    /** The date (and time) when the validity period of the entry ends. */
    private LocalDateTime validTo;

    /** Determines if the validFrom value was inherited from the super domain. */
    private Boolean validToInherited;
    
    /** An amount of time a record should be valid for (only needed for the calculation of the validTo attribute). */
    private String validityTime;

    /** The new domain this record belongs to. */
    private Domain newDomain;
    
    /** The name of the new domain this record belongs to. */
    private String newDomainName;
    
    public boolean hasIdentifyingInformation() {
    	if (Assertion.isNotNullOrEmpty(this.getOldPsn())) {
    		// psn was given, which always identifies the object
    		return true;
    	}
    	
    	if (Assertion.assertNullAll(this.getOldIdentifierItem(), this.getOldPsn())) {
    		// No identifierItem and no psn was given that could be used to find the object that should be updated
    		return false;
    	}
    	
    	// Check if the domain of the object allows multiple psns
    	if (this.getOldDomain().getMultiplepsnallowed()) {
    		// Multiple psns are allowed, so we need the psn to uniquely identify the object in the database
    		// Since the first if-clause in this method already checks if the psn was given, it is not available at this point
    		return false;
    	}
    	
    	// Check that the identifier item is given
    	if (this.getOldIdentifierItem() != null && Assertion.isNotNullOrEmpty(this.getOldIdentifierItem().getIdentifier(), this.getOldIdentifierItem().getIdType())) {
    		return true;
    	} else {
    		return false;
    	}
    }
}

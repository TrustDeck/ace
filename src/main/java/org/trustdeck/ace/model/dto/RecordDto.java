/*
 * ACE - Advanced Confidentiality Engine
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
import org.trustdeck.ace.jooq.generated.tables.interfaces.IPseudonym;
import org.trustdeck.ace.service.DomainDBAccessService;
import org.trustdeck.ace.utils.Assertion;
import org.trustdeck.ace.utils.SpringBeanLocator;

import java.time.LocalDateTime;

/**
 * This class offers an Data Transfer Object (DTO) for the representation in a response to a REST-API-request.
 *
 * @author Armin Müller & Eric Wündisch
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordDto implements IRepresentation<IPseudonym, RecordDto> {

    /** Enables the access to the domain specific database access methods. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
    @JsonIgnore
    private DomainDBAccessService domainDBAccessService = SpringBeanLocator.getBean(DomainDBAccessService.class);

    /** Stores the identifier for that entry. */
    private String id;

    /** The type of the identifier. */
    private String idType;

    /** The pseudonym belonging to the identifier. */
    private String psn;

    /** The date (and time) when the validity period of the entry starts. */
    private LocalDateTime validFrom;

    /** Determines if the validFrom value was inherited from the super domain. */
    private Boolean validFromInherited;

    /** The date (and time) when the validity period of the entry ends. */
    private LocalDateTime validTo;

    /** Determines if the validFrom value was inherited from the super domain. */
    private Boolean validToInherited;
    
    /** An amount of time a record should be valid for. (Only needed for the creation.) */
    private String validityTime;

    /** The name of the domain this record belongs to. */
    private String domainName;

    /** The domain object this entry belongs to. */
    private DomainDto domain;

    /**
     * Maps all values from jOOQ's Pseudonym object to a RecordDto object.
     */
    @JsonIgnore
    @Override
    public RecordDto assignPojoValues(IPseudonym pojo) {
        this.setId(pojo.getIdentifier() != null ? pojo.getIdentifier() : "");
        this.setIdType(pojo.getIdtype() != null ? pojo.getIdtype() : "");
        this.setPsn(pojo.getPseudonym() != null ? pojo.getPseudonym() : "");
        this.setValidFrom(pojo.getValidfrom() != null ? pojo.getValidfrom() : null);
        this.setValidFromInherited(pojo.getValidfrominherited());
        this.setValidTo(pojo.getValidto() != null ? pojo.getValidto() : null);
        this.setValidToInherited(pojo.getValidtoinherited());
        DomainDto d = pojo.getDomainid() != null ? new DomainDto().assignPojoValues(domainDBAccessService.getDomainByID(pojo.getDomainid(), null)) : null;
        this.setDomainName(d != null ? d.getName() : null);
        this.setDomain(d != null ? d : null);

        return this;
    }

    @Override
    @JsonIgnore
    public Boolean isValidStandardView() {
        return Assertion.assertNullAll(
        		this.getValidFromInherited(),
                this.getValidToInherited(),
                this.getDomain());
    }

    /**
     * This method creates a reduced standard view by setting all
     * attributes that shouldn't be displayed by default to {@code null},
     * so that they are excluded in the JSON representation.
     *
     * @return the altered record DTO containing only the standard
     * information (identifier, idType, psn, vFrom, vTo, domainName)
     */
    @Override
    @JsonIgnore
    public RecordDto toReducedStandardView() {
        this.setValidFromInherited(null);
        this.setValidToInherited(null);
        this.setValidityTime(null);
        this.setDomain(null);
        
        return this;
    }

    /**
     * Creates a readable string representation.
     */
    @JsonIgnore
    @Override
    public String toRepresentationString() {
        String out = "";
        out += (this.getId() != null) ? "id: " + this.getId() + ", " : "";
        out += (this.getIdType() != null) ? "idType: " + this.getIdType() + ", " : "";
        out += (this.getPsn() != null) ? "pseudonym: " + this.getPsn() + ", " : "";
        out += (this.getValidFrom() != null) ? "validFrom: " + this.getValidFrom().toString() + ", " : "";
        out += (this.getValidFromInherited() != null) ? "validFromInherited: " + this.getValidFromInherited() + ", " : "";
        out += (this.getValidTo() != null) ? "validTo: " + this.getValidTo().toString() + ", " : "";
        out += (this.getValidToInherited() != null) ? "validToInherited: " + this.getValidToInherited() + ", " : "";
        out += (this.getDomainName() != null) ? "domainName: " + this.getDomainName() + ", " : "";
        //out += (this.getDomain() != null) ? "domain: {" + this.getDomain().toRepresentationString() + "}" : "";

        return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
    }

    @Override
    @JsonIgnore
    public Boolean validate() {
        if (this.getId() == null || this.getId().trim().equals("") || this.getIdType() == null || this.getIdType().trim().equals("")) {
            return false;
        }

        return true;
    }
}

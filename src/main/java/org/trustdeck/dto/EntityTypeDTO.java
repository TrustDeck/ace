/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller
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

import org.jooq.JSONB;
import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.Entitytype;
import org.trustdeck.utils.Assertion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class represents a Data Transfer Object (DTO) for an entity type.
 *
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityTypeDTO implements IObjectDTO<Entitytype, EntityTypeDTO> {
	
	/** The (internal) ID of this entity type. Do not expose it to users. */
	@JsonIgnore
	private Integer id;
	
	/** The object type's name. */
	private String name;
	
	/** The version number of this entity type. */
	private String version;
	
	/** Flag that determines if this type is a base type that is defined by TrustDeck admins. Other types can extend from this. */
	private Boolean isBaseType;
	
	/** The type definition of this entity. */
	private JSONB typeDefinition;
	
	/** The ID of the project where this type is defined in. */
	private Integer projectID;

	@JsonIgnore
	@Override
	public EntityTypeDTO assignPojoValues(Entitytype pojo) {
		if (pojo == null) {
	        return null;
	    }
		
		EntityTypeDTO dto = new EntityTypeDTO();
	    
	    dto.setId(pojo.getId());
	    dto.setName(pojo.getName());
	    dto.setVersion(pojo.getVersion());
	    dto.setIsBaseType(pojo.getIsbasetype());
	    dto.setTypeDefinition(pojo.getTypedef());
	    dto.setProjectID(pojo.getProjectid());

	    return dto;
	}

	@JsonIgnore
	@Override
	public Boolean isValidStandardView() {
		// Currently not needed
		return null;
	}

	@JsonIgnore
	@Override
	public EntityTypeDTO toReducedStandardView() {
		// Currently not needed
		return null;
	}

	@JsonIgnore
	@Override
	public String toRepresentationString() {
		String out = "";

	    out += (this.getId() != null) ? "id: " + this.getId().toString() + ", " : "";
	    out += (this.getName() != null) ? "name: " + this.getName() + ", " : "";
	    out += (this.getVersion() != null) ? "version: " + this.getVersion() + ", " : "";
	    out += (this.getIsBaseType() != null) ? "isBaseType: " + this.getIsBaseType() + ", " : "";
	    out += (this.getTypeDefinition() != null) ? "typeDefinition: " + this.getTypeDefinition().toString() + ", " : "";
	    out += (this.getProjectID() != null) ? "projectID: " + this.getProjectID() + ", " : "";
	    
	    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		return Assertion.assertNotNullAll(this.getName(), this.getVersion());
	}
}

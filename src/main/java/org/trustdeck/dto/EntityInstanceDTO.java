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
import org.trustdeck.jooq.generated.tables.pojos.Entityinstance;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class represents a Data Transfer Object (DTO) for an entity instance.
 *
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityInstanceDTO implements IObjectDTO<Entityinstance, EntityInstanceDTO> {
	
	/** The (internal) ID of this entity instance. Do not expose it to users. */
	@JsonIgnore
	private Long id;
	
	/** The ID of the type of this entity instance. */
	private Integer entityTypeID;
	
	/** This entity instance's data (i.e. the attributes and values). */
	private JSONB data;

	@JsonIgnore
	@Override
	public EntityInstanceDTO assignPojoValues(Entityinstance pojo) {
		if (pojo == null) {
	        return null;
	    }
		
		EntityInstanceDTO dto = new EntityInstanceDTO();
	    
	    dto.setId(pojo.getId());
	    dto.setEntityTypeID(pojo.getEntitytypeid());
	    dto.setData(pojo.getData());

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
	public EntityInstanceDTO toReducedStandardView() {
		// Currently not needed
		return null;
	}

	@JsonIgnore
	@Override
	public String toRepresentationString() {
		String out = "";

	    out += (this.getId() != null) ? "id: " + this.getId().toString() + ", " : "";
	    out += (this.getEntityTypeID() != null) ? "entityTypeID: " + this.getEntityTypeID().toString() + ", " : "";
	    out += (this.getData() != null) ? "data: " + this.getData().toString() + ", " : "";

	    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		return this.getEntityTypeID() != null;
	}
}

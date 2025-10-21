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

import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.EntityInstance;
import org.trustdeck.service.EntityTypeDBService;
import org.trustdeck.service.ProjectDBService;
import org.trustdeck.utils.Assertion;

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
public class EntityInstanceDTO implements IObjectDTO<EntityInstance, EntityInstanceDTO> {
	
	/** The (internal) ID of this entity instance. Do not expose it to users. */
	@JsonIgnore
	private Long id;
	
	/** The unique UUID for this entity instance, used for publicly accessing this instance. */
	private UUID trustdeckID;
	
	/** The ID of the project where this entity instance is scoped in. */
	@JsonIgnore
	private Integer projectID;
	
	/** The name of the project where this entity instance is scoped in. */
	private String projectName;
	
	/** The ID of the type of this entity instance. */
	@JsonIgnore
	private Integer entityTypeID;
	
	/** The name of the type of this entity instance. */
	private String entityTypeName;
	
	/** This entity instance's data (i.e. the attributes and values). */
	private JSONB data;
	
	/** Flag that determines if this type is marked as deleted. */
	private Boolean isDeleted;
	
	/** The date and time when this entity instance was created. */
	private OffsetDateTime createdAt;
	
	/** The date and time when this entity instance was last updated. */
	private OffsetDateTime updatedAt;
	
	/** Enables access to the project specific database functions. */
	@Autowired
	@JsonIgnore
	private ProjectDBService pdbs;
	
	/** Enables access to the entity type database functions. */
	@Autowired
	@JsonIgnore
	private EntityTypeDBService etdbs;

	@JsonIgnore
	@Override
	public EntityInstanceDTO assignPojoValues(EntityInstance pojo) {
		if (pojo == null) {
	        return null;
	    }
		
		EntityInstanceDTO dto = new EntityInstanceDTO();
	    
	    dto.setId(pojo.getId());
	    dto.setTrustdeckID(pojo.getTrustdeckId());
	    dto.setProjectID(pojo.getProjectId());
	    dto.setProjectName(pdbs.getProjectByID(pojo.getProjectId(), null).getName());
	    dto.setEntityTypeID(pojo.getEntityTypeId());
	    dto.setEntityTypeName(etdbs.getEntityTypeById(pojo.getEntityTypeId(), pojo.getProjectId(), null).getName());
	    dto.setData(pojo.getData());
	    dto.setIsDeleted(pojo.getIsDeleted());
	    dto.setCreatedAt(pojo.getCreatedAt());
	    dto.setUpdatedAt(pojo.getUpdatedAt());

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
	    out += (this.getTrustdeckID() != null) ? "trustDeckID: " + this.getTrustdeckID().toString() + ", " : "";
	    out += (this.getProjectID() != null) ? "projectID: " + this.getProjectID().toString() + ", " : "";
	    out += (this.getProjectName() != null) ? "projectName: " + this.getProjectName() + ", " : "";
	    out += (this.getEntityTypeID() != null) ? "entityTypeID: " + this.getEntityTypeID().toString() + ", " : "";
	    out += (this.getEntityTypeName() != null) ? "entityTypeName: " + this.getEntityTypeName() + ", " : "";
	    out += (this.getData() != null) ? "data: " + this.getData().toString() + ", " : "";
	    out += (this.getIsDeleted() != null) ? "isDeleted: " + this.getIsDeleted().toString() + ", " : "";
	    out += (this.getCreatedAt() != null) ? "createdAt: " + this.getCreatedAt().toString() + ", " : "";
	    out += (this.getUpdatedAt() != null) ? "updatedAt: " + this.getUpdatedAt().toString() + ", " : "";

	    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		return this.getEntityTypeID() != null && this.getData() != null && !this.getIsDeleted() 
				&& Assertion.isNotNullOrEmpty(this.getTrustdeckID().toString(), this.getProjectID().toString());
	}
}

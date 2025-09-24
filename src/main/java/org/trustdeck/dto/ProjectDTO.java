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

import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.Project;
import org.trustdeck.service.ProjectDBService;
import org.trustdeck.utils.SpringBeanLocator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * This class represents a Data Transfer Object (DTO) for a project.
 *
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectDTO implements IObjectDTO<Project, ProjectDTO> {
	
	/** The (internal) ID of this project. Do not expose it to users. */
	@JsonIgnore
	private Integer id;
	
	/** The project's unique name. */
	private String name;
	
	/** The unique abbreviation for the project's name. */
	private String abbreviation;
	
	/** The project's start date. */
	private String startDate;
	
	/** The project's end date. */
	private String endDate;
	
	/** A flag determining whether or not entities are stored in the project. */
	private Boolean storeEntities;
	
	/** A flag determining whether or not pseudonyms are created in the project. */
	private Boolean storePseudonyms;
	
	/** The project description. */
	private String description;
	
	/** A list of the names of the associated object types. */
	private String[] associatedEntityTypes;
	
	/** Allows interaction with the project objects in the database. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
	@JsonIgnore
	@Autowired
	private ProjectDBService projectDBService;

	@JsonIgnore
	@Override
	public ProjectDTO assignPojoValues(Project pojo) {
		if (pojo == null) {
	        return null;
	    }
		
	    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	    ProjectDTO dto = new ProjectDTO();
	    
	    dto.setId(pojo.getId());
	    dto.setName(pojo.getName());
	    dto.setAbbreviation(pojo.getAbbreviation());
	    dto.setStartDate(pojo.getStartDate() != null ? pojo.getStartDate().format(dateFormatter) : null);
	    dto.setEndDate(pojo.getEndDate() != null ? pojo.getEndDate().format(dateFormatter) : null);
	    dto.setStoreEntities(pojo.getStoreEntities());
	    dto.setStorePseudonyms(pojo.getStorePseudonyms());
	    dto.setDescription(pojo.getDescription());
	    
	    // Insert the object type names
	    if (this.projectDBService == null) {
	    	this.projectDBService = SpringBeanLocator.getBean(ProjectDBService.class);
	    }
    	dto.setAssociatedEntityTypes(projectDBService.getEntityTypeNames(pojo.getAssociatedEntityTypeIds()));

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
	public ProjectDTO toReducedStandardView() {
		// Currently not needed
		return null;
	}

	@JsonIgnore
	@Override
	public String toRepresentationString() {
		String out = "";

	    out += (this.getId() != null) ? "id: " + this.getId().toString() + ", " : "";
	    out += (this.getName() != null) ? "name: " + this.getName() + ", " : "";
	    out += (this.getAbbreviation() != null) ? "abbreviation: " + this.getAbbreviation() + ", " : "";
	    out += (this.getStartDate() != null) ? "startDate: " + this.getStartDate() + ", " : "";
	    out += (this.getEndDate() != null) ? "endDate: " + this.getEndDate() + ", " : "";
	    out += (this.getStoreEntities() != null) ? "storeEntities: " + this.getStoreEntities().toString() + ", " : "";
	    out += (this.getStorePseudonyms() != null) ? "createPseudonyms: " + this.getStorePseudonyms().toString() + ", " : "";
	    out += (this.getDescription() != null) ? "description: " + this.getDescription() + ", " : "";
	    if (this.getAssociatedEntityTypes() != null) {
	    	out += "associatedEntityTypes: [";
	    	
	    	for (int i = 0; i < this.getAssociatedEntityTypes().length; i++) {
	    		out += this.getAssociatedEntityTypes()[i] + ((i == this.getAssociatedEntityTypes().length-1) ? "" : ", ");
	    	}
	    	out += "]";
	    }
	    
	    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		return !this.getName().isBlank() && !this.getAbbreviation().isBlank();
	}
}

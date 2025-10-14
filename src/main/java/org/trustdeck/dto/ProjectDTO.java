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
	
	/** The project's start date. */
	private String startDate;
	
	/** The project's end date. */
	private String endDate;
	
	/** The project's main contact point (e.g. address, phone-number, email-address, PI name). */
	private String mainContact;
	
	/** A list of the names of the associated object types. */
	private String[] associatedObjects;
	
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
	    dto.setStartDate(pojo.getStartdate() != null ? pojo.getStartdate().format(dateFormatter) : null);
	    dto.setEndDate(pojo.getEnddate() != null ? pojo.getEnddate().format(dateFormatter) : null);
	    dto.setMainContact(pojo.getMainContact());
	    
	    // Insert the object type names
	    if (this.projectDBService == null) {
	    	this.projectDBService = SpringBeanLocator.getBean(ProjectDBService.class);
	    }
	    	
    	dto.setAssociatedObjects(projectDBService.getObjectTypeNames(pojo.getAssociatedObjecttypeIds()));

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
	    out += (this.getStartDate() != null) ? "startDate: " + this.getStartDate() + ", " : "";
	    out += (this.getEndDate() != null) ? "endDate: " + this.getEndDate() + ", " : "";
	    out += (this.getMainContact() != null) ? "mainContact: " + this.getMainContact() + ", " : "";
	    if (this.getAssociatedObjects() != null) {
	    	out += "associatedObjects: [";
	    	
	    	for (int i = 0; i < this.getAssociatedObjects().length; i++) {
	    		out += this.getAssociatedObjects()[i] + ((i == this.getAssociatedObjects().length-1) ? "" : ", ");
	    	}
	    	
	    	out += "]";
	    }
	    
	    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		return !this.getName().isBlank();
	}
}

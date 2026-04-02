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

import org.springframework.context.annotation.Scope;
import org.trustdeck.jooq.generated.tables.pojos.ProjectImage;
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
 * This class represents a Data Transfer Object (DTO) for a project image.
 *
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectImageDTO implements IObjectDTO<ProjectImage, ProjectImageDTO> {

	/** The (internal) ID of this project. Do not expose it to users. */
	@JsonIgnore
	private Integer id;

	/** Owning project's internal ID. Do not expose it to users. */
	@JsonIgnore
	private Integer projectId;

	/** Owning project's name. */
	private String projectName;

	/** Raw image bytes. Kept out of JSON since it can get big. */
	@JsonIgnore
	private byte[] imageBytes;

	/** Size of the image in MiB. */
	private Float imageSize;

	/** The image's MIME type, e.g. image/png, image/jpeg, image/webp, image/svg+xml. */
	private String mimeType;

	/** Enables access to project specific database functions. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
	@JsonIgnore
	private ProjectDBService pdbs = SpringBeanLocator.getBean(ProjectDBService.class);

	@JsonIgnore
	@Override
	public ProjectImageDTO assignPojoValues(ProjectImage pojo) {
		if (pojo == null) {
			return null;
		}
		
		ProjectDTO project = pdbs.getProjectByID(pojo.getProjectId());

		this.setId(pojo.getId());
		this.setProjectId(pojo.getProjectId());
		this.setProjectName(project == null ? null : project.getName());
		this.setImageBytes(pojo.getImage());
		this.setImageSize(pojo.getImageSizeBytes() / (1024.0f * 1024.0f));
		this.setMimeType(pojo.getMimeType());
		
		return this;
	}

	@JsonIgnore
	@Override
	public Boolean isValidStandardView() {
		// Not used
		return null;
	}

	@JsonIgnore
	@Override
	public ProjectImageDTO toReducedStandardView() {
		// Not used
		return null;
	}

	@JsonIgnore
	@Override
	public String toRepresentationString() {
		String out = "";

	    out += (this.getId() != null) ? "id: " + this.getId().toString() + ", " : "";
	    out += (this.getProjectId() != null) ? "projectID: " + this.getProjectId().toString() + ", " : "";
	    out += (this.getProjectName() != null) ? "projectName: " + this.getProjectName() + ", " : "";
	    out += (this.getImageBytes() != null) ? "imageBytes: " + this.getImageBytes().toString() + ", " : "";
	    out += (this.getImageSize() != null) ? "imageSize: " + this.getImageSize().toString() + ", " : "";
	    out += (this.getMimeType() != null) ? "mimeType: " + this.getMimeType() + ", " : "";
	    
	    return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@JsonIgnore
	@Override
	public Boolean validate() {
		return projectId != null && mimeType != null && !mimeType.isBlank();
	}
}

/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller and Eric Wündisch
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.context.annotation.Scope;

/**
 * Data Transfer Object (DTO) for permissions. This class represents the
 * permissions of a user for a specific domain/project and role.
 *
 * @author Eric Wündisch, Armin Müller
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionDTO implements IObjectDTO<String, PermissionDTO> {

	/** The domain this permission applies to. Either this or the projectName should be used. */
	private String domainName;
	
	/** The project this permission applies to. Either this or the domainName should be used. */
	private String projectName;

	/** The role associated with the permission. */
	private String role;

	/** The user ID associated with the permission. */
	private String userId;
	
	/**
	 * Unused.
	 */
	@Override
	public PermissionDTO assignPojoValues(String pojo) {
		// Unused
		return null;
	}

	/**
	 * Checks whether the default view of the object is valid.
	 *
	 * @return {@code true} if the default view is valid, otherwise {@code false}
	 */
	@Override
	@JsonIgnore
	public Boolean isValidStandardView() {
		return null;
	}

	/**
	 * Reduces the object to its standard view.
	 *
	 * @return the reduced PermissionDTO object
	 */
	@Override
	@JsonIgnore
	public PermissionDTO toReducedStandardView() {
		return this;
	}

	/**
	 * Returns a string representation of the object. The representation includes
	 * the values for role, domain, and user ID.
	 *
	 * @return a string representation of the object
	 */
	@Override
	@JsonIgnore
	public String toRepresentationString() {
		String out = "";

		out += (this.getRole() != null) ? "role: " + this.getRole() + ", " : "";
		out += (this.getDomainName() != null) ? "domainName: " + this.getDomainName() + ", " : "";
		out += (this.getProjectName() != null) ? "projectName: " + this.getProjectName() + ", " : "";
		out += (this.getUserId() != null) ? "userId: " + this.getUserId() + ", " : "";

		return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@Override
	public Boolean validate() {
		// Unimplemented
		return null;
	}
}
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

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

import org.springframework.context.annotation.Scope;
import org.trustdeck.configuration.RoleConfig;
import org.trustdeck.jooq.generated.tables.daos.DomainDao;
import org.trustdeck.jooq.generated.tables.daos.ProjectDao;
import org.trustdeck.security.authentication.configuration.JwtProperties;
import org.trustdeck.utils.Assertion;
import org.trustdeck.utils.SpringBeanLocator;

/**
 * Data Transfer Object (DTO) for permissions. This class represents the
 * permissions of a user for a specific domain/project and role.
 *
 * @author Eric Wündisch, Armin Müller
 */
@Data
@NoArgsConstructor
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

	/** Properties for the JWT configuration. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
	@JsonIgnore
	private JwtProperties jwtProperties = SpringBeanLocator.getBean(JwtProperties.class);

	/** Configuration of roles and operations. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
	@JsonIgnore
	private RoleConfig roleConfig = SpringBeanLocator.getBean(RoleConfig.class);

	/** Service for accessing domains. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
	@JsonIgnore
	private DomainDao domainDao = SpringBeanLocator.getBean(DomainDao.class);

	/** Service for accessing projects. */
	@Getter(value=AccessLevel.NONE)
    @Setter(value=AccessLevel.NONE)
	@JsonIgnore
	private ProjectDao projectDao = SpringBeanLocator.getBean(ProjectDao.class);

	/**
	 * Constructor that creates the DTO from a given group path.
	 * 
	 * @param groupPath the flat path including the domain name and the permitted role
	 * @param userId the user ID associated with the permission
	 */
	@JsonIgnore
	public PermissionDTO(String groupPath, String userId) {
		if (Assertion.isNullOrEmpty(groupPath)) {
			// Empty group path
			return;
		}
		
		// Remove the name of the role-bucket from the path
		String path;
		if (groupPath.startsWith("/" + jwtProperties.getDomainRoleGroupContextName())) {
			path = groupPath.substring(("/" + jwtProperties.getDomainRoleGroupContextName()).length());
		} else if (groupPath.startsWith("/" + jwtProperties.getProjectRoleGroupContextName())) {
			path = groupPath.substring(("/" + jwtProperties.getProjectRoleGroupContextName()).length());
		} else {
			path = groupPath;
		}
		
		// Remove leading slashes
		path = path.startsWith("/") ? path.substring(1) : path;
		
		// Now only one slash should be in the path which divides role and domain/project; extract these
		String[] splitPath = path.split("/");
		if (splitPath.length == 2) {
			this.setRole(splitPath[0]);
			
			// Check if this role is ACE- or KING-specific and assign the second part of the path accordingly
			if (roleConfig.getACERoles().contains(this.getRole())) {
				this.setDomainName(splitPath[1]);
			} else if (roleConfig.getKINGRoles().contains(this.getRole())) {
				this.setProjectName(splitPath[1]);
			}
		} else {
			return;
		}
		
		// Set userId
		this.userId = Assertion.isNotNullOrEmpty(userId) ? userId.trim() : "";
	}
	
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
		return this.validate();
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

	/**
	 * Validates the permission. Checks whether the role is valid and whether
	 * the domain or project exists.
	 *
	 * @return {@code true} if the permission is valid, otherwise {@code false}
	 */
	@Override
	@JsonIgnore
	public Boolean validate() {
		if (roleConfig.getACERoles().contains(this.getRole()) && domainDao.fetchOneByName(this.getDomainName()) != null) {
			return true;
		} else if (roleConfig.getKINGRoles().contains(this.getRole()) && projectDao.fetchOneByName(this.getProjectName()) != null) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Returns the partial path for the role.
	 *
	 * @return the path up until the role
	 */
	@JsonIgnore
	public String getRolePath() {
		if (roleConfig.getACERoles().contains(this.getRole())) {
			return "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + this.getRole();
		} else if (roleConfig.getKINGRoles().contains(this.getRole())) {
			return "/" + jwtProperties.getProjectRoleGroupContextName() + "/" + this.getRole();
		} else {
			return null;
		}
	}

	/**
	 * Returns the path for the domain. Includes the role.
	 *
	 * @return the path including the role and the domain name
	 */
	@JsonIgnore
	public String getDomainPath() {
		return this.getRolePath() + "/" + this.getDomainName();
	}

	/**
	 * Returns the path for the project. Includes the role.
	 *
	 * @return the path including the role and the project name
	 */
	@JsonIgnore
	public String getProjectPath() {
		return this.getRolePath() + "/" + this.getProjectName();
	}
	
	/**
	 * Checks if a given permission is already present in the list of permissions.
	 *
	 * @param permissions the list of permissions to check against
	 * @return {@code true} if the permission is found in the list, {@code false} otherwise
	 */
	@JsonIgnore
	public boolean isPermissionInList(List<PermissionDTO> permissions) {
		for (PermissionDTO permission : permissions) {
			if (permission.equals(this)) {
				return true;
			}
		}
		
		return false;
	}
}
/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller & Eric Wündisch
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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.trustdeck.configuration.RoleConfig;
import org.trustdeck.jooq.generated.tables.daos.DomainDao;
import org.trustdeck.security.authentication.configuration.JwtProperties;
import org.trustdeck.utils.Assertion;
import org.trustdeck.utils.SpringBeanLocator;

/**
 * Data Transfer Object (DTO) for permissions. This class represents the
 * permissions of a user for a specific domain and operation.
 *
 * @author Eric Wündisch, Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionDTO implements IObjectDTO<String, PermissionDTO> {

	/** The domain for which the permission applies. */
	private String domainName;

	/** The operation associated with the permission. */
	private String operation;

	/** The user ID associated with the permission. */
	private String userId;

	/** Properties for the JWT configuration. */
	@JsonIgnore
	@Autowired
	private JwtProperties jwtProperties;

	/** Configuration of roles and operations. */
	@JsonIgnore
	@Autowired
	private RoleConfig roleConfig;

	/** Service for accessing domain database methods. */
	@JsonIgnore
	@Autowired
	private DomainDao domainDao;

	/**
	 * Constructor that creates the DTO from a given group path.
	 * 
	 * @param groupPath the flat path including the domain name and the permitted operation
	 * @param userId the user ID associated with the permission
	 */
	@JsonIgnore
	public PermissionDTO(String groupPath, String userId) {
		if (Assertion.isNullOrEmpty(groupPath)) {
			// Empty group path
			return;
		}
		
		// Ensure that we have access to the JWT properties
		if (jwtProperties == null) {
			jwtProperties = SpringBeanLocator.getBean(JwtProperties.class);
		}
		
		// Remove the name of the role-bucket from the path
		String path;
		if (groupPath.startsWith("/" + jwtProperties.getDomainRoleGroupContextName())) {
			path = groupPath.substring(("/" + jwtProperties.getDomainRoleGroupContextName()).length());
		} else {
			path = groupPath;
		}
		
		// Remove leading slashes
		path = path.startsWith("/") ? path.substring(1) : path;
		
		// Now only one slash should be in the path which divides operation and domain; extract these
		String[] splitPath = path.split("/");
		if (splitPath.length == 2) {
			this.setOperation(splitPath[0]);
			this.setDomainName(splitPath[1]);
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
	 * the values for operation, domain, and user ID.
	 *
	 * @return a string representation of the object
	 */
	@Override
	@JsonIgnore
	public String toRepresentationString() {
		String out = "";

		out += (this.getOperation() != null) ? "operation: " + this.getOperation() + ", " : "";
		out += (this.getDomainName() != null) ? "domainName: " + this.getDomainName() + ", " : "";
		out += (this.getDomainName() != null) ? "userId: " + this.getUserId() + ", " : "";

		return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	/**
	 * Validates the permission. Checks whether the operation is valid and whether
	 * the domain exists.
	 *
	 * @return {@code true} if the permission is valid, otherwise {@code false}
	 */
	@Override
	@JsonIgnore
	public Boolean validate() {
		// Ensure that we have access to the role configuration
		if (roleConfig == null) {
			roleConfig = SpringBeanLocator.getBean(RoleConfig.class);
		}
		
		// Ensure that we have access to the domain database service
		if (domainDao == null) {
			domainDao = SpringBeanLocator.getBean(DomainDao.class);
		}
		
		return roleConfig.getOperations().contains(this.getOperation()) && domainDao.fetchOneByName(this.getDomainName()) != null;
	}

	/**
	 * Returns the partial path for the operation.
	 *
	 * @return the path up until the operation
	 */
	@JsonIgnore
	public String getOperationPath() {
		return "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + this.getOperation();
	}

	/**
	 * Returns the path for the domain. Includes the operation.
	 *
	 * @return the path including the operation and the domain name
	 */
	@JsonIgnore
	public String getDomainPath() {
		return this.getOperationPath() + "/" + this.getDomainName();
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
/*
 * Trust Deck Services
 * Copyright 2024-2026 Armin Müller and Eric Wündisch
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

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.context.annotation.Scope;

/**
 * Data Transfer Object (DTO) for an user. This class represents the user's
 * details such as user ID, username, first name, last name, email, federation,
 * and federation ID.
 * 
 * @author Eric Wündisch, Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO implements IObjectDTO<UserRepresentation, UserDTO> {

	/** A unique identifier of the user. */
	private String userId;

	/** The username of the user. */
	private String username;

	/** The first name of the user. */
	private String firstName;

	/** The last name of the user. */
	private String lastName;

	/** The email address of the user. */
	private String email;
	
	/** Keycloak client roles assigned to the user. */
    private List<String> keycloakRoles;

    /** Permissions the user has in the service. */
    private List<EffectivePermissionDTO> effectivePermissions;

	/** The name of the federation provider associated with the user. */
	private String federationProviderName;

	/** The ID of the user storage provider (federation provider) that the user is linked to e.g., an LDAP provider. */
	@JsonIgnore
	private String federationProviderId;

	@Override
	@JsonIgnore
	public UserDTO assignPojoValues(UserRepresentation pojo) {
		
		this.setUserId(pojo.getId());
		this.setUsername(pojo.getUsername());
		this.setFirstName(pojo.getFirstName());
		this.setLastName(pojo.getLastName());
		this.setEmail(pojo.getEmail());
		this.setFederationProviderId(pojo.getFederationLink());

		return this;
	}
	
	@Override
	@JsonIgnore
	public Boolean isValidStandardView() {
		// Unused
		return null;
	}

	@Override
	@JsonIgnore
	public UserDTO toReducedStandardView() {
		// Unused
		return null;
	}

	/**
	 * Converts the UserDTO into a string representation.
	 *
	 * @return the string representation of this DTO
	 */
	@Override
	@JsonIgnore
	public String toRepresentationString() {
		String out = "";

		out += (this.getUserId() != null) ? "userId: " + this.getUserId() + ", " : "";
		out += (this.getUsername() != null) ? "username: " + this.getUsername() + ", " : "";
		out += (this.getFirstName() != null) ? "firstName: " + this.getFirstName() + ", " : "";
		out += (this.getLastName() != null) ? "lastName: " + this.getLastName() + ", " : "";
		out += (this.getEmail() != null) ? "email: " + this.getEmail() + ", " : "";
		out += (this.getKeycloakRoles() != null) ? "keycloakRoles: " + this.getKeycloakRoles() + ", " : "";
		out += (this.getEffectivePermissions() != null) ? "effectivePermissions: " + this.getEffectivePermissions() + ", " : "" ;
		out += (this.getFederationProviderName() != null) ? "federationProviderName: " + this.getFederationProviderName() + ", " : "";
		out += (this.getFederationProviderId() != null) ? "federationProviderId: " + this.getFederationProviderId() + ", " : "";

		return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
	}

	@Override
	@JsonIgnore
	public Boolean validate() {
		// Unused
		return null;
	}
}

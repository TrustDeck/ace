/*
 * Trust Deck Services
 * Copyright 2026 Armin Müller and Eric Wündisch
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

package org.trustdeck.service;

import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.configuration.RoleConfig;
import org.trustdeck.dto.PermissionDTO;
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.security.authentication.configuration.JwtProperties;
import org.trustdeck.utils.Assertion;
import org.trustdeck.utils.Utility;

import lombok.extern.slf4j.Slf4j;

import static org.trustdeck.jooq.generated.Tables.DOMAIN;
import static org.trustdeck.jooq.generated.Tables.PROJECT;

/**
 * This class encapsulates the methods handling permissions.
 * 
 * @author Armin Müller
 */
@Slf4j
@Service
public class PermissionService {

	/** Configuration for roles and operations. This is used to validate the operations and permissions. */
	@Autowired
	private RoleConfig roleConfig;

    /** JWT configuration properties, handling token attributes like expiration and signing. */
    @Autowired
    private JwtProperties jwtProperties;
	
	/** References a jOOQ configuration object that configures jOOQ's behavior when executing queries. */
    @Autowired
	private DSLContext dsl;

	/** Enables the access to the project specific database access methods. */
	@Autowired
	private ProjectDBService projectDBService;

	/** Enables the access to the domain specific database access methods. */
	@Autowired
	private DomainDBAccessService domainDBAccessService;

	/** Provides functionality to ensure proper rights and roles when accessing the endpoints. */
	@Autowired
	private OidcService oidcService;
    
    /**
	 * Retrieves a list of the current permissions of a user for a specific domain.
	 *
	 * @param domainName the name of the domain
	 * @param userId the ID of the user
	 * @return a list of current permissions for the user in the specified domain
	 * @throws IllegalArgumentException when this exception was thrown inside of another method called from this method
	 */
	public List<PermissionDTO> getCurrentACEPermissions(String domainName, String userId) throws IllegalArgumentException {
		// Retrieve the domain tree
		List<String> domainNames = getFlatDomainTree(domainName);

		// Extract permissions for the given domain from the list of all permissions
		List<PermissionDTO> currentPermissions = new ArrayList<>();
		List<String> groupPaths = Utility.extractGroupPaths(oidcService.getGroupsByUserId(userId), true);

		for (String groupPath : groupPaths) {
			// Transform group path into permission DTO
			PermissionDTO permissionDTO = createFromPathAndUserId(groupPath, userId);

			if (validatePermission(permissionDTO) && permissionDTO.getDomainName() != null && permissionDTO.getDomainName().equals(domainName) && domainNames.contains(permissionDTO.getDomainName())) {
				currentPermissions.add(permissionDTO);
			}
		}

		return currentPermissions;
	}

	/**
	 * Retrieves a list of the current permissions of a user for a specific project.
	 *
	 * @param projectAbbreviation the abbreviation of the project
	 * @param userId the ID of the user
	 * @return a list of current permissions for the user in the specified project
	 * @throws IllegalArgumentException when this exception was thrown inside of another method called from this method
	 */
	public List<PermissionDTO> getCurrentKINGPermissions(String projectAbbreviation, String userId) throws IllegalArgumentException {
		// Retrieve the project names
		List<String> projectNames = projectDBService.getAllProjectNames(null);
		String projectName = projectDBService.getProjectByAbbreviation(projectAbbreviation, null).getName();

		// Extract permissions for the given domain from the list of all permissions
		List<PermissionDTO> currentPermissions = new ArrayList<>();
		List<String> groupPaths = Utility.extractGroupPaths(oidcService.getGroupsByUserId(userId), true);
		for (String groupPath : groupPaths) {
			// Transform group path into permission DTO
			PermissionDTO permissionDTO = createFromPathAndUserId(groupPath, userId);

			if (validatePermission(permissionDTO) && permissionDTO.getProjectName() != null && permissionDTO.getProjectName().equals(projectName) && projectNames.contains(projectName)) {
				currentPermissions.add(permissionDTO);
			}
		}

		return currentPermissions;
	}

	/**
	 * Checks that the role is valid by checking if it is a project- or 
	 * domain-role and then checking if the respective project or domain exist.
	 * @return {@code true} if the permission is valid, otherwise {@code false}
	 */
	public Boolean validatePermission(PermissionDTO permission) {
		if (permission == null || permission.getRole() == null) {
			return false;
		}
		
		if (roleConfig.getACERoles().contains(permission.getRole())) {
			if (Assertion.isNullOrEmpty(permission.getDomainName())) {
				return false;
			}
			
			return dsl.fetchExists(dsl.selectOne().from(DOMAIN).where(DOMAIN.NAME.eq(permission.getDomainName())));
		} else if (roleConfig.getKINGRoles().contains(permission.getRole())) {
			if (Assertion.isNullOrEmpty(permission.getProjectName())) {
				return false;
			}
			
			return dsl.fetchExists(dsl.selectOne().from(PROJECT).where(PROJECT.NAME.eq(permission.getProjectName())));
		} else {
			return false;
		}
	}

	/**
	 * Returns the partial path for the role.
	 *
	 * @param permission the permission DTO from which the path should be extracted
	 * @return the path up until the role
	 */
	public String getRolePath(PermissionDTO permission) {
		if (roleConfig.getACERoles().contains(permission.getRole())) {
			return "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + permission.getRole();
		} else if (roleConfig.getKINGRoles().contains(permission.getRole())) {
			return "/" + jwtProperties.getProjectRoleGroupContextName() + "/" + permission.getRole();
		} else {
			return null;
		}
	}

	/**
	 * Returns the path for the domain. Includes the role.
	 *
	 * @param permission the permission DTO from which the path should be extracted
	 * @return the path including the role and the domain name
	 */
	public String getDomainPath(PermissionDTO permission) {
		return this.getRolePath(permission) + "/" + permission.getDomainName();
	}

	/**
	 * Returns the path for the project. Includes the role.
	 *
	 * @param permission the permission DTO from which the path should be extracted
	 * @return the path including the role and the project name
	 */
	public String getProjectPath(PermissionDTO permission) {
		return this.getRolePath(permission) + "/" + permission.getProjectName();
	}
	
	/**
	 * Method to create a permission DTO from a group path and a user ID.
	 * This method extracts the role and project/domain name from the group path.
	 * 
	 * @param groupPath the path of the keycloak group representing this permission
	 * @param userId the keycloak ID of the user
	 * @return a permission DTO containing the proper role 
	 */
	private PermissionDTO createFromPathAndUserId(String groupPath, String userId) {
	    if (Assertion.isNullOrEmpty(groupPath, userId)) {
			// Empty group path
			return null;
		}
		
	    PermissionDTO dto = new PermissionDTO();
		dto.setUserId(userId.trim());
		
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
			dto.setRole(splitPath[0]);
			
			// Check if this role is ACE- or KING-specific and assign the second part of the path accordingly
			if (roleConfig.getACERoles().contains(dto.getRole())) {
				dto.setDomainName(splitPath[1]);
			} else if (roleConfig.getKINGRoles().contains(dto.getRole())) {
				dto.setProjectName(splitPath[1]);
			}
		} else {
			return null;
		}
		
		return dto;
	}

	/**
	* Retrieves a flat list of domain names for a given domain that represents the domain-
	* tree that the given domain is in.
	*
	* @param domainName the name of the domain to retrieve the tree structure for
	* @return a list of domain names in a flat structure
	* @throws IllegalArgumentException when the domain or it's tree could not be retrieved
	*/
	public List<String> getFlatDomainTree(String domainName) throws IllegalArgumentException {
		Domain domain = domainDBAccessService.getDomainByName(domainName, null);
		List<Domain> domainTree = domainDBAccessService.getDomainTreeStructure(domain);

		// Check if the tree has at least one domain in it
		if (domainTree == null || domainTree.size() <= 0) {
			throw new IllegalArgumentException("No domains found for the specified domain name.");
		}

		// Transform the flat tree of domain objects into one of domain names only
		List<String> domainNames = new ArrayList<>();
		for (Domain thisDomain : domainTree) {
			domainNames.add(thisDomain.getName());
		}

		return domainNames;
	}
}

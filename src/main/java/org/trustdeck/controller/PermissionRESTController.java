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

package org.trustdeck.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trustdeck.configuration.RoleConfig;
import org.trustdeck.dto.UserDTO;
import org.trustdeck.dto.PermissionDTO;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.service.OidcService;
import org.trustdeck.service.PermissionService;
import org.trustdeck.service.ResponseService;
import org.trustdeck.utils.Assertion;
import org.trustdeck.utils.Utility;

/**
 * Provides the REST API for the permission endpoints.
 * 
 * This controller handles requests related to users and permissions.
 *
 * @author Eric Wündisch, Armin Müller
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api/permissions")
public class PermissionRESTController {

	/** Enables services for better working with responses. */
	@Autowired
	private ResponseService responseService;

	/** Provides functionality to ensure proper rights and roles when accessing the endpoints. */
	@Autowired
	private OidcService oidcService;

	/** Configuration for roles and operations. This is used to validate the operations and permissions. */
	@Autowired
	private RoleConfig roleConfig;

    /** Provides functionality to handle permissions. */
    @Autowired
    private PermissionService permissionService;

	/** The default number of maximum allowed query results. If a query would result in more records, the surplus is omitted. */
	private static final int DEFAULT_MAX_NUMBER_OF_QUERY_RESULTS = 20;

	/**
	 * Searches for users based on a search term.
	 *
	 * @param query the search term
	 * @param responseContentType (optional) the response content type
	 * @param request the request object, injected by Spring Boot
	 * @return <li>a <b>200-OK</b> status with the (possibly empty) list of
	 * 		   users matching the query</li>
	 *         <li>a <b>206-PARTIAL_CONTENT</b> status with a truncated list
	 *         when more than the maximum number of results are available</li>
	 *         <li>a <b>400-BAD_REQUEST</b> status when the required 
	 *         <i>query</i> parameter is missing</li>
	 */
	@GetMapping("/users")
	@PreAuthorize("hasRole('permission-manager')")
	@Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.HUMAN)
	public ResponseEntity<?> searchUsers(@RequestParam(name = "query", required = true) String query,
										 @RequestHeader(name = "accept", required = false) String responseContentType,
										 HttpServletRequest request) {
		List<UserDTO> foundUsers = new ArrayList<>();
		List<UserRepresentation> users = oidcService.searchUsers(query);

		boolean wasTruncated = false;
		if (!users.isEmpty()) {
			int i = 0;
			
			// Add the federation provider's name to the user info
			Map<String, String> federationProviderMap = oidcService.getFederationProviderMap();
			for (UserRepresentation user : users) {
				if (i > DEFAULT_MAX_NUMBER_OF_QUERY_RESULTS) {
					wasTruncated = true;
					break;
				}
				
				UserDTO userDTO = new UserDTO();
				userDTO.assignPojoValues(user);
				userDTO.setFederationProviderName(federationProviderMap.get(userDTO.getFederationProviderId()));
				foundUsers.add(userDTO);
				
				i++;
			}
		}
		
		// Check if user list was truncated and notify the user if necessary
		if (wasTruncated) {
			responseService.partialContent(responseContentType, foundUsers);
		}

		return responseService.ok(responseContentType, foundUsers);
	}

	/**
	 * Retrieves the permissions of a user for a specific domain.
	 *
	 * @param domainName the name of the domain
	 * @param userId the ID of the user
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return <li>a <b>200-OK</b> status with the list of permissions 
     * 		   for the user in the given domain</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when <i>domainName</i> 
     *         or <i>userId</i> is missing or empty</li>
	 */
	@GetMapping("/domains/{domainName}")
	@PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'permission-manager')")
	@Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> getACEPermissions(@PathVariable("domainName") String domainName,
											   @RequestParam(name = "userId", required = true) String userId,
											   @RequestHeader(name = "accept", required = false) String responseContentType,
											   HttpServletRequest request) {
		// Sanitize
		if (Assertion.isNullOrEmpty(domainName, userId)) {
			return responseService.badRequest(responseContentType);
		}
		
		return responseService.ok(responseContentType, permissionService.getCurrentACEPermissions(domainName, userId));
	}

	/**
	 * Retrieves the permissions of a user for a specific project.
	 *
	 * @param projectAbbreviation the abbreviation of the project
	 * @param userId the ID of the user
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return <li>a <b>200-OK</b> status with the list of permissions 
     * 		   for the user in the given domain</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when <i>domainName</i> 
     *         or <i>userId</i> is missing or empty</li>
	 */
	@GetMapping("/projects/{projectAbbreviation}")
	@PreAuthorize("@auth.hasProjectRoleRelationship(#root, #projectAbbreviation, 'permission-manager')")
	@Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> getKINGPermissions(@PathVariable("projectAbbreviation") String projectAbbreviation,
												@RequestParam(name = "userId", required = true) String userId,
												@RequestHeader(name = "accept", required = false) String responseContentType,
												HttpServletRequest request) {
		// Sanitize
		if (Assertion.isNullOrEmpty(projectAbbreviation, userId)) {
			return responseService.badRequest(responseContentType);
		}
		
		return responseService.ok(responseContentType, permissionService.getCurrentKINGPermissions(projectAbbreviation, userId));
	}

	/**
	 * Updates the ACE-specific permission for a user in a domain.
	 *
	 * @param domainName the name of the domain
	 * @param userId the ID of the user
	 * @param permissions the list of permissions to be created
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return <li>a <b>200-OK</b> status when the user's permissions were 
     * 		   successfully synchronized with the provided list</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the provided permissions 
     *         list is empty or contains invalid entries</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the specified domain does 
     *         not exist or cannot be resolved</li>
	 */
	@PutMapping("/domains/{domainName}")
	@PreAuthorize("hasRole('permission-manager')")
	@Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> updateACEPermission(@PathVariable("domainName") String domainName,
												 @RequestParam(name = "userId", required = true) String userId,
												 @RequestBody List<PermissionDTO> permissions,
												 @RequestHeader(name = "accept", required = false) String responseContentType,
												 HttpServletRequest request) {

		// Check if the given list of updated permissions is empty
		if (permissions == null || permissions.size() == 0) {
			return responseService.badRequest(responseContentType);
		}

		// Retrieve the domain tree that includes the domain of interest as a list
		List<String> domainNames;
		try {
			domainNames = permissionService.getFlatDomainTree(domainName);
		} catch (IllegalArgumentException e) {
			return responseService.notFound(responseContentType);
		}

		// Get the paths of the groups
		Map<String, String> groupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);

		// Check if the permissions are valid
		for (PermissionDTO permissionDTO : permissions) {
			if (!domainNames.contains(permissionDTO.getDomainName())
					|| !roleConfig.getACERoles().contains(permissionDTO.getRole())
					|| !permissionDTO.getUserId().equals(userId)
					|| !groupPaths.containsValue(permissionService.getDomainPath(permissionDTO))) {
				return responseService.badRequest(responseContentType);
			}
		}

		// Get the current permissions of the user
		List<PermissionDTO> currentPermissions;
		try {
			currentPermissions = permissionService.getCurrentACEPermissions(domainName, userId);
		} catch (IllegalArgumentException e) {
			return responseService.notFound(responseContentType);
		}

		// Create missing permissions
		for (PermissionDTO permissionDTO : permissions) {
			if (!currentPermissions.contains(permissionDTO)) {
				// Get the key from the group paths by the value
				Map.Entry<String, String> domainEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getDomainPath(permissionDTO));
				Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getRolePath(permissionDTO));
				
				if (domainEntry != null && operationEntry != null) {
					oidcService.addUserToGroup(domainEntry.getKey(), userId);
					oidcService.addUserToGroup(operationEntry.getKey(), userId);
				} else {
					log.debug("No group found for path: " + permissionService.getDomainPath(permissionDTO));
				}
			}
		}

		// Delete permissions that are not in the new list
		for (PermissionDTO permissionDTO : currentPermissions) {
			if (!permissions.contains(permissionDTO)) {
				Map.Entry<String, String> domainEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getDomainPath(permissionDTO));
				Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getRolePath(permissionDTO));
				
				if (domainEntry != null && operationEntry != null) {
					oidcService.removeUserFromGroup(domainEntry.getKey(), userId);
					oidcService.removeUserFromGroup(operationEntry.getKey(), userId);
				} else {
					log.debug("No group found for path: " + permissionService.getDomainPath(permissionDTO));
				}
			}
		}

		return this.responseService.ok(responseContentType);
	}

	/**
	 * Updates the KING-specific permission for a user in a project.
	 *
	 * @param projectAbbreviation the abbreviation of the project
	 * @param userId the ID of the user
	 * @param permissions the list of permissions to be created
	 * @param responseContentType (optional) the response content type
     * @param request the request object, injected by Spring Boot
     * @return <li>a <b>200-OK</b> status when the user's permissions were 
     * 		   successfully synchronized with the provided list</li>
     *         <li>a <b>400-BAD_REQUEST</b> status when the provided permissions 
     *         list is empty or contains invalid entries</li>
     *         <li>a <b>404-NOT_FOUND</b> status when the specified domain does 
     *         not exist or cannot be resolved</li>
	 */
	@PutMapping("/projects/{projectAbbreviation}")
	@PreAuthorize("hasRole('permission-manager')")
	@Audit(eventType = AuditEventType.UPDATE, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> updateKINGPermission(@PathVariable("projectAbbreviation") String projectAbbreviation,
												  @RequestParam(name = "userId", required = true) String userId,
												  @RequestBody List<PermissionDTO> permissions,
												  @RequestHeader(name = "accept", required = false) String responseContentType,
												  HttpServletRequest request) {

		// Check if the given list of updated permissions is empty
		if (permissions == null || permissions.size() == 0) {
			return responseService.badRequest(responseContentType);
		}

		// Get the paths of the groups
		Map<String, String> groupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);

		// Check if the permissions are valid
		for (PermissionDTO permissionDTO : permissions) {
			if (!roleConfig.getKINGRoles().contains(permissionDTO.getRole()) || !permissionDTO.getUserId().equals(userId)) {
				return responseService.badRequest(responseContentType);
			}
		}

		// Get the current permissions of the user
		List<PermissionDTO> currentPermissions;
		try {
			currentPermissions = permissionService.getCurrentKINGPermissions(projectAbbreviation, userId);
		} catch (IllegalArgumentException e) {
			return responseService.notFound(responseContentType);
		}

		// Create missing permissions
		for (PermissionDTO permissionDTO : permissions) {
			if (!currentPermissions.contains(permissionDTO)) {
				// Get the key from the group paths by the value
				// TODO
				Map.Entry<String, String> projectEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getProjectPath(permissionDTO));
				Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getRolePath(permissionDTO));
				
				if (projectEntry != null && operationEntry != null) {
					oidcService.addUserToGroup(projectEntry.getKey(), userId);
					oidcService.addUserToGroup(operationEntry.getKey(), userId);
				} else {
					log.debug("No group found for path: " + permissionService.getProjectPath(permissionDTO));
				}
			}
		}

		// Delete permissions that are not in the new list
		for (PermissionDTO permissionDTO : currentPermissions) {
			if (!permissions.contains(permissionDTO)) {
				Map.Entry<String, String> projectEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getProjectPath(permissionDTO));
				Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(groupPaths, permissionService.getRolePath(permissionDTO));
				
				if (projectEntry != null && operationEntry != null) {
					oidcService.removeUserFromGroup(projectEntry.getKey(), userId);
					oidcService.removeUserFromGroup(operationEntry.getKey(), userId);
				} else {
					log.debug("No group found for path: " + permissionService.getProjectPath(permissionDTO));
				}
			}
		}

		return this.responseService.ok(responseContentType);
	}

	
}

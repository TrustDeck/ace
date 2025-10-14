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
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.service.DomainDBAccessService;
import org.trustdeck.service.OidcService;
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
public class PermissionRestController {

	/** Enables the access to the domain specific database access methods. */
	@Autowired
	private DomainDBAccessService domainDBAccessService;

	/** Enables services for better working with responses. */
	@Autowired
	private ResponseService responseService;

	/** Provides functionality to ensure proper rights and roles when accessing the endpoints. */
	@Autowired
	private OidcService oidcService;

	/** Configuration for roles and operations. This is used to validate the operations and permissions. */
	@Autowired
	private RoleConfig roleConfig;
	
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
	@GetMapping("/{domainName}")
	@PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'permission-manager')")
	@Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.ALL)
	public ResponseEntity<?> getPermissions(@PathVariable("domainName") String domainName,
											@RequestParam(name = "userId", required = true) String userId,
											@RequestHeader(name = "accept", required = false) String responseContentType,
											HttpServletRequest request) {
		// Sanitize
		if (Assertion.isNullOrEmpty(domainName, userId)) {
			return responseService.badRequest(responseContentType);
		}
		
		return responseService.ok(responseContentType, getCurrentPermissions(domainName, userId));
	}

	/**
	 * Updates the permission for a user in a domain.
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
	@PutMapping("/{domainName}")
	@PreAuthorize("@auth.hasDomainRoleRelationship(#root, #domainName, 'permission-manager')")
	public ResponseEntity<?> updatePermission(@PathVariable("domain") String domainName,
											  @RequestParam(name = "userId", required = true) String userId,
											  @RequestBody List<PermissionDTO> permissions,
											  @RequestHeader(name = "accept", required = false) String responseContentType, 
											  HttpServletRequest request) {

		// Check if the given list of updated permissions is empty
		if (permissions == null || permissions.size() == 0) {
			return responseService.badRequest(responseContentType);
		}

		// Check if the domain exists
		List<String> domainNames;
		try {
			domainNames = getFlatDomainTree(domainName);
		} catch (IllegalArgumentException e) {
			return responseService.notFound(responseContentType);
		}

		// Get the paths of the groups
		Map<String, String> groupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);

		// Check if the permissions are valid
		for (PermissionDTO permissionDTO : permissions) {
			if (!domainNames.contains(permissionDTO.getDomainName())
					|| !roleConfig.getOperations().contains(permissionDTO.getOperation())
					|| !permissionDTO.getUserId().equals(userId)
					|| !groupPaths.containsValue(permissionDTO.getDomainPath())) {
				return responseService.badRequest(responseContentType);
			}
		}

		// Get the current permissions of the user
		List<PermissionDTO> currentPermissions;
		try {
			currentPermissions = getCurrentPermissions(domainName, userId);
		} catch (IllegalArgumentException e) {
			return responseService.notFound(responseContentType);
		}

		// Create missing permissions
		for (PermissionDTO permissionDTO : permissions) {
			if (!permissionDTO.isPermissionInList(currentPermissions)) {
				// Get the key from the group paths by the value
				Map.Entry<String, String> domainEntry = Utility.findGroupEntryByPath(groupPaths, permissionDTO.getDomainPath());
				Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(groupPaths, permissionDTO.getOperationPath());
				
				if (domainEntry != null && operationEntry != null) {
					oidcService.addUserToGroup(domainEntry.getKey(), userId);
					oidcService.addUserToGroup(operationEntry.getKey(), userId);
				} else {
					log.debug("No group found for path: {}", permissionDTO.getDomainPath());
				}
			}
		}

		// Delete permissions that are not in the new list
		for (PermissionDTO permissionDTO : currentPermissions) {
			if (!permissionDTO.isPermissionInList(permissions)) {
				Map.Entry<String, String> domainEntry = Utility.findGroupEntryByPath(groupPaths, permissionDTO.getDomainPath());
				Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(groupPaths, permissionDTO.getOperationPath());
				
				if (domainEntry != null && operationEntry != null) {
					oidcService.removeUserFromGroup(domainEntry.getKey(), userId);
					oidcService.removeUserFromGroup(operationEntry.getKey(), userId);
				} else {
					log.debug("No group found for path: {}", permissionDTO.getDomainPath());
				}
			}
		}

		return this.responseService.ok(responseContentType);
	}

	/**
	* Retrieves a flat list of domain names for a given domain.
	*
	* @param domainName the name of the domain to retrieve the tree structure for
	* @return a list of domain names in a flat structure
	*/
	private List<String> getFlatDomainTree(String domainName) {
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

	/**
	 * Retrieves a list of the current permissions of a user for a specific domain.
	 *
	 * @param domainName the name of the domain
	 * @param userId the ID of the user
	 * @return a list of current permissions for the user in the specified domain
	 */
	private List<PermissionDTO> getCurrentPermissions(String domainName, String userId) {
		// Retrieve the domain tree
		List<String> domainNames = getFlatDomainTree(domainName);

		// Extract permissions for the given domain from the list of all permissions
		List<PermissionDTO> currentPermissions = new ArrayList<>();
		List<String> groupPaths = Utility.extractGroupPaths(oidcService.getGroupsByUserId(userId), true);
		for (String groupPath : groupPaths) {
			// Transform group path into permission DTO
			PermissionDTO permissionDTO = new PermissionDTO(groupPath, userId);

			if (permissionDTO.validate() && domainNames.contains(permissionDTO.getDomainName())) {
				currentPermissions.add(permissionDTO);
			}
		}

		return currentPermissions;
	}
}

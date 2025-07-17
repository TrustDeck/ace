/*
 * Trust Deck Services
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
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
import org.trustdeck.dto.OperatorDTO;
import org.trustdeck.dto.PermissionDTO;
import org.trustdeck.jooq.generated.tables.pojos.Domain;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.security.authentication.configuration.JwtProperties;
import org.trustdeck.service.DomainDBAccessService;
import org.trustdeck.service.OidcService;
import org.trustdeck.service.ResponseService;
import org.trustdeck.utils.Utility;

/**
 * Provides the REST API for the service endpoints.
 * <p>
 * This controller handles requests related to operators search and permission management. It
 * ensures that only authorized users can access these endpoints.
 * </p>
 *
 * @author Eric Wündisch
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api/service")
public class ServiceRestController {

  /**
   * Enables the access to the domain specific database access methods.
   */
  @Autowired
  private DomainDBAccessService domainDBAccessService;

  /**
   * Enables services for better working with responses.
   */
  @Autowired
  private ResponseService responseService;

  /**
   * Provides functionality to ensure proper rights and roles when accessing the endpoints.
   */
  @Autowired
  private OidcService oidcService;

  /**
   * Configuration for roles and operations. This is used to validate the operations and
   * permissions.
   */
  @Autowired
  private RoleConfig roleConfig;

  /**
   * Properties for the JWT configuration. This is used to validate the JWT tokens and their
   * properties.
   */
  @Autowired
  private JwtProperties jwtProperties;

  /**
   * Retrieves a flat list of domain names for a given domain.
   *
   * @param domainName The name of the domain to retrieve the tree structure for.
   * @param request    The HTTP request object.
   * @return A list of domain names in a flat structure.
   */
  private List<String> getFlatDomainTree(String domainName, HttpServletRequest request) {

    Domain domain = domainDBAccessService.getDomainByName(domainName, request);

    List<Domain> domains = domainDBAccessService.getDomainTreeStructure(domain);

    if (domains == null || domains.size() <= 0) {
      throw new IllegalArgumentException("No domains found for the specified domain name");
    }

    List<String> domainNames = new ArrayList<>();
    for (Domain thisDomain : domains) {
      domainNames.add(thisDomain.getName());
    }

    return domainNames;
  }

  /**
   * Retrieves the current permissions of a user for a specific domain.
   *
   * @param domainName The name of the domain.
   * @param userId     The ID of the user.
   * @param request    The HTTP request object.
   * @return A list of current permissions for the user in the specified domain.
   */
  private List<PermissionDTO> getCurrentPermission(String domainName, String userId,
      HttpServletRequest request) {

    List<String> domainNames = getFlatDomainTree(domainName, request);

    List<PermissionDTO> currentPermissions = new ArrayList<>();
    List<String> groups = Utility.extractGroupPaths(oidcService.getGroupsByUserId(userId), true);
    for (String groupPath : groups) {

      PermissionDTO permissionDTO = new PermissionDTO();
      permissionDTO.assignPojoValues(groupPath);
      permissionDTO.setUserId(userId);

      if (permissionDTO.validate() && domainNames.contains(permissionDTO.getDomain())) {
        currentPermissions.add(permissionDTO);
      }
    }

    return currentPermissions;
  }

  /**
   * Checks if a given permission is already present in the list of permissions.
   *
   * @param permissions   The list of permissions to check against.
   * @param permissionDTO The permission to check for.
   * @return true if the permission is found in the list, false otherwise.
   */
  private boolean inPermissionList(List<PermissionDTO> permissions, PermissionDTO permissionDTO) {
    for (PermissionDTO permission : permissions) {
      if (permission.equals(permissionDTO)) {
        return true;
      }
    }
    return false;

  }

  /**
   * Searches for operators based on a search term.
   *
   * @param query               The search term, which must be at least 3 characters long.
   * @param responseContentType The desired content type of the response (optional).
   * @param request             The HTTP request object.
   * @return A list of operators matching the search criteria.
   */
  @GetMapping("/operators/search")
  @PreAuthorize("hasRole('permission-manager')")
  @Audit(eventType = AuditEventType.READ, auditFor = AuditUserType.HUMAN, message = "Searching for operators in the system.")
  public ResponseEntity<?> searchOperator(
      @RequestParam(name = "query", required = true) String query,
      @RequestHeader(name = "accept", required = false) String responseContentType,
      HttpServletRequest request) {

    if (query.length() <= 2) {
      return responseService.ok(responseContentType, new ArrayList<>());
    }

    List<OperatorDTO> finalOperatorList = new ArrayList<>();
    List<UserRepresentation> users = oidcService.searchUsers(query);

    if (!users.isEmpty()) {
      Map<String, String> federations = oidcService.getFederations();
      for (UserRepresentation user : users) {
        OperatorDTO operatorDTO = new OperatorDTO();
        operatorDTO.assignPojoValues(user);
        operatorDTO.setFederation(federations.get(operatorDTO.getFederationId()));
        finalOperatorList.add(operatorDTO);
      }
    }

    return responseService.ok(responseContentType, finalOperatorList);
  }

  /**
   * Retrieves the permissions of a user for a specific domain.
   *
   * @param domainName          The name of the domain.
   * @param userId              The ID of the user.
   * @param responseContentType The desired content type of the response (optional).
   * @param request             The HTTP request object.
   * @return A list of permissions of the user for the domain.
   */
  @GetMapping("/{domain}/permissions")
  @PreAuthorize("@auth.hasDomainRootRoleRelationship(#root, #domainName, 'permission-manager')")
  public ResponseEntity<?> getPermissions(
      @PathVariable("domain") String domainName,
      @RequestParam(name = "userId", required = true) String userId,
      @RequestHeader(name = "accept", required = false) String responseContentType,
      HttpServletRequest request) {

    List<PermissionDTO> currentPermissions = getCurrentPermission(domainName, userId, request);
    return responseService.ok(responseContentType, currentPermissions);
  }

  /**
   * Updates the permission for a user in a domain.
   *
   * @param domainName          The name of the domain.
   * @param permissions         The list of permissions to be created.
   * @param responseContentType The desired content type of the response (optional).
   * @param request             The HTTP request object.
   * @return The server's response (not yet implemented).
   */
  @PutMapping("/{domain}/permissions")
  @PreAuthorize("@auth.hasDomainRootRoleRelationship(#root, #domainName, 'permission-manager')")
  public ResponseEntity<?> updatePermission(
      @PathVariable("domain") String domainName,
      @RequestParam(name = "userId", required = true) String userId,
      @RequestBody List<PermissionDTO> permissions,
      @RequestHeader(name = "accept", required = false) String responseContentType,
      HttpServletRequest request) {

    //check if the user has the permission to manage permissions
    if (permissions == null) {
      return responseService.badRequest(responseContentType);
    }

    //check if the domain exists
    List<String> domainNames;
    try {
      domainNames = getFlatDomainTree(domainName, request);
    } catch (IllegalArgumentException e) {
      return responseService.notFound(responseContentType);
    }

    //get the paths of the groups
    Map<String, String> finalGroupPaths = Utility.flattenGroupIDToPathMapping(
        oidcService.getRealmGroups(), true);

    //check if the permissions are valid
    for (PermissionDTO permissionDTO : permissions) {
      if (!domainNames.contains(permissionDTO.getDomain()) ||
          !roleConfig.getOperations().contains(permissionDTO.getOperation()) ||
          !permissionDTO.getUserId().equals(userId) ||
          !finalGroupPaths.containsValue(permissionDTO.getDomainPath())
      ) {
        return responseService.badRequest(responseContentType);
      }
    }

    //get the current permissions of the user
    List<PermissionDTO> currentPermissions;
    try {
      currentPermissions = getCurrentPermission(domainName, userId, request);
    } catch (IllegalArgumentException e) {
      return responseService.notFound(responseContentType);
    }

    //create missing permissions
    for (PermissionDTO permissionDTO : permissions) {
      if (!inPermissionList(currentPermissions, permissionDTO)) {
        //get the key from finalGroupPaths by the value
        Map.Entry<String, String> domainEntry = Utility.findGroupEntryByPath(finalGroupPaths,
            permissionDTO.getDomainPath());
        Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(finalGroupPaths,
            permissionDTO.getOperationPath());
        if (domainEntry != null && operationEntry != null) {
          oidcService.addUserToGroup(domainEntry.getKey(), userId);
          oidcService.addUserToGroup(operationEntry.getKey(), userId);
        } else {
          log.debug("No group found for path: {}", permissionDTO.getDomainPath());
        }
      }
    }

    //delete permissions that are not in the new list
    for (PermissionDTO permissionDTO : currentPermissions) {
      if (!inPermissionList(permissions, permissionDTO)) {
        Map.Entry<String, String> domainEntry = Utility.findGroupEntryByPath(finalGroupPaths,
            permissionDTO.getDomainPath());
        Map.Entry<String, String> operationEntry = Utility.findGroupEntryByPath(finalGroupPaths,
            permissionDTO.getOperationPath());
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


}

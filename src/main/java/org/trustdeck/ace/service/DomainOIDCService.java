/*
 * ACE - Advanced Confidentiality Engine
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

package org.trustdeck.ace.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.ace.configuration.RoleConfig;
import org.trustdeck.ace.security.authentication.configuration.JwtProperties;
import org.trustdeck.ace.utils.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for handling OIDC (OpenID Connect) related operations for domain-level interactions
 * within the application. E.g. for rights and roles management in Keycloak.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Slf4j
@Service
public class DomainOIDCService {

    /** OIDC service for managing OpenID Connect operations such as token retrieval and validation. */
    @Autowired
    private OidcService oidcService;

    /** JWT configuration properties, handling token attributes like expiration and signing. */
    @Autowired
    protected JwtProperties jwtProperties;

    /** Role configuration to manage role-based access control and authorization in the domain. */
    @Autowired
    protected RoleConfig roleConfig;

    /** Caching service to improve performance by caching frequently used OIDC data. */
    @Autowired
    protected CachingService cachingService;

    /**
     * Checks if a domain name can be used as a group name within the Keycloak realm.
     * This method iterates through all operation-groups and checks if any sub-group
     * with the specified domain name already exists. If a matching group is found, it
     * returns {@code false}, indicating that the domain name is already in use.
     *
     * @param domainName the domain name to be checked
     * @return {@code true} if the domain name is not in use, or {@code false} if a group with this name already exists
     */
    public Boolean canBeUsedAsDomainGroup(String domainName) {
        // Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);

        // Iterate over each operation-role defined in the configuration
        for (String role : roleConfig.getOperations()) {
            // Check if a sub-group with the specified domain name already exists
            Map.Entry<String, String> matches = Utility.findGroupEntryByPath(flatGroupPaths, "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + role + "/" + domainName);

            // Return false if a matching group is found
            if (matches != null) {
                return false;
            }
        }

        // Return true if no matching group is found
        return true;
    }

    /**
     * Creates a new set of domain-related groups and roles, and assigns them to a user.
     *
     * @param domainName the name of the domain to be created
     * @param userId the unique identifier of the user to be added to the groups
     * @throws NullPointerException if any group or role creation fails, or the user cannot be added to the groups
     */
    public void createDomainGroups(String domainName, String userId) {
        // Create a new role for the specified domain name
        RoleRepresentation roleRepresentation = oidcService.createClientRole(domainName);

        if (!canBeUsedAsDomainGroup(domainName)) {
            throw new NullPointerException("The domain name cannot be used in the Keycloak realm.6");
        }

        // List to store the IDs of the newly created domain groups in
        List<String> domainGroupIds = new ArrayList<>();

        // Iterate over each operation group and create a subgroup for the domain
        for (String operationGroupId : oidcService.getOperationGroupIDs()) {
            GroupRepresentation groupRepresentation = oidcService.createSubGroup(operationGroupId, domainName);

            // Ensure the subgroup was successfully created
            if (groupRepresentation == null) {
                throw new NullPointerException("Group for domain \"" + domainName + "\" could not be created.");
            }

            // Assign the new role to the created subgroup
            oidcService.assignRoleToGroup(groupRepresentation.getId(), roleRepresentation);

            // Add the newly created group's ID to the list
            domainGroupIds.add(groupRepresentation.getId());
        }

        // Add the user to both the operation groups and the newly created domain groups
        if (!oidcService.joinGroups(oidcService.getOperationGroupIDs(), userId) || !oidcService.joinGroups(domainGroupIds, userId)) {
            throw new NullPointerException("Could not register user in all groups.");
        }

        cachingService.flushAndReCacheMatchingGroups(userId, domainName, true);
    }

    /**
     * Removes all domain-related groups for the specified domain name.
     *
     * @param domainName the name of the domain whose related groups are to be removed
     */
    public void deleteDomainGroups(String domainName, String userId) {
        // Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);

        // Iterate over each operation-role defined in the configuration
        for (String operation : roleConfig.getOperations()) {
            // Construct the path for the group that is to be deleted
            String path = "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + operation + "/" + domainName;

            // Find the group that matches the current path
            Map.Entry<String, String> group = Utility.findGroupEntryByPath(flatGroupPaths, path);

            // If a matching group is found, remove it by its ID
            if (group != null) {
                oidcService.removeGroupById(group.getKey());
            }
        }

        cachingService.flushAndReCacheMatchingGroups(userId, domainName);
    }

    /**
     * Updates the name of domain-related groups and roles within the Keycloak server.
     *
     * @param oldDomainName the name of the domain to be updated
     * @param newDomainName the new name to be assigned to the domain-related roles and groups
     * @throws NullPointerException if the new role cannot be created, or the old role cannot be deleted, or a group name update fails
     */
    public void updateDomainGroups(String oldDomainName, String newDomainName, String userId) {
        // Retrieve the existing role by the old domain name
        RoleRepresentation oldRole = oidcService.getClientRoleByName(oldDomainName);

        // Create a new role with the new domain name
        RoleRepresentation newRole = oidcService.createClientRole(newDomainName);

        // Ensure the new role was successfully created
        if (newRole == null) {
            throw new NullPointerException("Creating the updated Keycloak role failed.");
        }

        // Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);

        // Remove the old role from the system
        if (!oidcService.removeClientRole(oldRole)) {
            throw new NullPointerException("Role could not be deleted.");
        }

        // Iterate over each operation-role defined in the configuration
        for (String operation : roleConfig.getOperations()) {
            // Construct the path for the group to be updated
            String path = "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + operation + "/" + oldDomainName;

            // Find the group that matches the path
            Map.Entry<String, String> group = Utility.findGroupEntryByPath(flatGroupPaths, path);

            if (group != null) {
                // Assign the new role to the group
                oidcService.assignRoleToGroup(group.getKey(), newRole);

                // Update the group's name to the new domain name
                if (!oidcService.updateGroupName(group.getKey(), newDomainName)) {
                    throw new NullPointerException("Updating group name in path \"" + path + "\" failed.");
                }
            }
        }

        cachingService.flushAndReCacheMatchingGroups(userId, oldDomainName);
    }
}

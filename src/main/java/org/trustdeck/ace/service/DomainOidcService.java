/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2024 Armin Müller & Eric Wündisch
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
 * DomainOidcService is a service component responsible for handling OIDC (OpenID Connect) related operations
 * for domain-level interactions within the application.
 *
 * <p>This service utilizes the following dependencies:
 * <ul>
 *     <li>OidcService - Manages OIDC specific operations like token retrieval and validation.</li>
 *     <li>JwtProperties - Provides configuration for handling JWT (JSON Web Token) properties.</li>
 *     <li>RoleConfig - Configures roles used for access control and authorization.</li>
 *     <li>CachingService - Manages caching for improved performance of OIDC interactions.</li>
 * </ul>
 *
 * <p>The class is annotated with @Slf4j to provide logging capabilities, and @Service to indicate that it is a
 * Spring-managed service component. Dependencies are injected using @Autowired annotations.
 */
@Slf4j
@Service
public class DomainOidcService {
    /**
     * Injected OIDC service for managing OpenID Connect operations such as token retrieval and validation.
     */
    @Autowired
    private OidcService oidcService;

    /**
     * Injected properties for JWT configuration, handling token attributes like expiration and signing.
     */
    @Autowired
    protected JwtProperties jwtProperties;

    /**
     * Injected role configuration to manage role-based access control and authorization in the domain.
     */
    @Autowired
    protected RoleConfig roleConfig;

    /**
     * Injected caching service to improve performance by caching frequently used OIDC data.
     */
    @Autowired
    protected CachingService cachingService;


    /**
     * Checks if a domain name can be used as a group name within the Keycloak realm.
     * <p>
     * This method iterates through all operational groups and checks if any sub-group
     * already exists with the specified domain name. If a matching group is found, it
     * returns {@code false}, indicating that the domain name is already in use.
     * </p>
     *
     * @param domainName The domain name to be checked.
     * @return {@code true} if the domain name is not in use, or {@code false} if a group with this name already exists.
     */
    public Boolean canUseAsDomainGroup(String domainName) {
        // Retrieve a flat map of group IDs to paths for all groups in the realm.
        Map<String, String> flatGroupPaths = Utility.flatGroups(oidcService.getRealmGroups(), true);

        // Iterate over each operational role defined in the configuration.
        for (String role : roleConfig.getOperations()) {
            // Check if a sub-group with the specified domain name already exists.
            Map.Entry<String, String> g = Utility.getMatchByPath(flatGroupPaths, "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + role + "/" + domainName);

            // Return false if a matching group is found.
            if (g != null) {
                return false;
            }
        }

        // Return true if no matching group is found.
        return true;
    }


    /**
     * Removes all domain-related groups for the specified domain name.
     * <p>
     * This method traverses all operational groups and searches for sub-groups matching the specified
     * domain name. If a matching group is found, it is removed from the Keycloak realm.
     * </p>
     *
     * @param domainName The name of the domain whose related groups are to be removed.
     */
    public void removeDomainGroups(String domainName, String userId) {
        // Retrieve a flat map of group IDs to paths for all groups in the realm.
        Map<String, String> flatGroupPaths = Utility.flatGroups(oidcService.getRealmGroups(), true);

        // Iterate over each operational role defined in the configuration.
        for (String operation : roleConfig.getOperations()) {
            // Construct the current path for the group to be deleted.
            String currentPath = "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + operation + "/" + domainName;

            // Find the group that matches the current path.
            Map.Entry<String, String> group = Utility.getMatchByPath(flatGroupPaths, currentPath);

            // If a matching group is found, remove it by its ID.
            if (group != null) {
                oidcService.removeGroupById(group.getKey());
            }
        }

        cachingService.flushAndReCacheMatchingGroups(userId, domainName);
    }


    /**
     * Updates the name of domain-related groups and roles within the Keycloak server.
     * <p>
     * This method performs the following operations:
     * <ul>
     *     <li>Retrieves the existing role by the old domain name.</li>
     *     <li>Creates a new role with the new domain name.</li>
     *     <li>Removes the old role and assigns the new role to the relevant groups.</li>
     *     <li>Updates the names of the affected groups to reflect the new domain name.</li>
     * </ul>
     * If any of these operations fail, the method throws a {@link NullPointerException}.
     * </p>
     *
     * @param oldDomainName The name of the domain to be updated.
     * @param newDomainName The new name to be assigned to the domain-related roles and groups.
     * @throws NullPointerException If the new role cannot be created, the old role cannot be deleted, or a group name update fails.
     */
    public void updateDomainGroups(String oldDomainName, String newDomainName, String userId) {
        // Retrieve the existing role by the old domain name.
        RoleRepresentation oldRole = oidcService.getClientRoleByName(oldDomainName);

        // Create a new role with the new domain name.
        RoleRepresentation newRole = oidcService.createClientRole(newDomainName);

        // Ensure the new role was successfully created.
        if (newRole == null) {
            throw new NullPointerException("Role can't be created but should");
        }

        // Retrieve a flat map of group IDs to paths for all groups in the realm.
        Map<String, String> flatGroupPaths = Utility.flatGroups(oidcService.getRealmGroups(), true);

        // Remove the old role from the system.
        if (!oidcService.removeClientRole(oldRole)) {
            throw new NullPointerException("Role could not be deleted");
        }

        // Iterate over each operational role defined in the configuration.
        for (String operation : roleConfig.getOperations()) {
            // Construct the current path for the group to be updated.
            String currentPath = "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + operation + "/" + oldDomainName;

            // Find the group that matches the current path.
            Map.Entry<String, String> group = Utility.getMatchByPath(flatGroupPaths, currentPath);

            if (group != null) {
                // Assign the new role to the group.
                oidcService.assignRoleToGroup(group.getKey(), newRole);

                // Update the group's name to the new domain name.
                if (!oidcService.updateGroupName(group.getKey(), newDomainName)) {
                    throw new NullPointerException("Groupname in " + currentPath + " failed");
                }
            }
        }

        cachingService.flushAndReCacheMatchingGroups(userId, oldDomainName);
    }


    /**
     * Creates a new set of domain-related groups and roles, and assigns them to a user.
     * <p>
     * This method performs the following operations:
     * <ul>
     *     <li>Creates a new role for the specified domain name.</li>
     *     <li>Creates subgroups under each operational group for the domain.</li>
     *     <li>Assigns the new role to each of the created subgroups.</li>
     *     <li>Adds the user to both operational and newly created groups.</li>
     * </ul>
     * If any operation fails, the method throws a {@link NullPointerException}.
     * </p>
     *
     * @param domainName The name of the domain to be created.
     * @param userId     The unique identifier of the user to be added to the groups.
     * @throws NullPointerException If any group or role creation fails, or the user cannot be added to the groups.
     */
    public void createDomainGroups(String domainName, String userId) {
        // Create a new role for the specified domain name.
        RoleRepresentation roleRepresentation = oidcService.createClientRole(domainName);

        // List to store the IDs of the newly created domain groups.
        List<String> domainGroupIds = new ArrayList<>();

        // Iterate over each operational group ID and create a subgroup for the domain.
        for (String operationGroupId : this.oidcService.getOperationGroupIds()) {
            GroupRepresentation groupRepresentation = oidcService.createSubGroup(operationGroupId, domainName);

            // Ensure the subgroup was successfully created.
            if (groupRepresentation == null) {
                throw new NullPointerException("Group could not be created");
            }

            // Assign the new role to the created subgroup.
            oidcService.assignRoleToGroup(groupRepresentation.getId(), roleRepresentation);

            // Add the newly created group's ID to the list.
            domainGroupIds.add(groupRepresentation.getId());
        }

        // Add the user to both the operational groups and the newly created domain groups.
        if (!oidcService.joinGroups(this.oidcService.getOperationGroupIds(), userId) || !oidcService.joinGroups(domainGroupIds, userId)) {
            throw new NullPointerException("Could not join all groups");
        }

        cachingService.flushAndReCacheMatchingGroups(userId, domainName, true);
    }
}

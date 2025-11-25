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

package org.trustdeck.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.configuration.AuditUserTypeConfiguration;
import org.trustdeck.configuration.RoleConfig;
import org.trustdeck.exception.ProjectOIDCException;
import org.trustdeck.security.authentication.configuration.JwtProperties;
import org.trustdeck.utils.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for handling OIDC (OpenID Connect) related operations for project-level interactions
 * within the application. E.g. for rights and roles management in Keycloak.
 *
 * @author Eric Wündisch and Armin Müller
 */
@Slf4j
@Service
public class ProjectOIDCService {

    /** OIDC service for managing OpenID Connect operations such as token retrieval and validation. */
    @Autowired
    private OidcService oidcService;

    /** JWT configuration properties, handling token attributes like expiration and signing. */
    @Autowired
    protected JwtProperties jwtProperties;

    /** Role configuration to manage role-based access control and authorization in the project. */
    @Autowired
    protected RoleConfig roleConfig;

    /** Audit trail configuration that includes the names of the user types. */
    @Autowired
    private AuditUserTypeConfiguration auditUserConfig;

    /** Caching service to improve performance by caching frequently used OIDC data. */
    @Autowired
    protected CachingService cachingService;

    /**
     * Creates a new set of project-related groups and roles, and assigns them to a user.
     *
     * @param projectName the name of the project to be created
     * @param userId the unique identifier of the user to be added to the groups
     */
    public void createProjectGroupsAndRolesAndJoin(String projectName, String userId) {
        // Create a new role for the specified project name
        RoleRepresentation roleRepresentation = oidcService.createClientRole(projectName);

        // List to store the IDs of the newly created project groups in
        List<String> projectGroupIds = new ArrayList<>();

        // Iterate over each role group and create a subgroup for the project
		for (String roleGroupId : oidcService.getKingRoleGroupIDs()) {
			// Create a subgroup for the project name under the current role group
			GroupRepresentation groupRepresentation = oidcService.createSubGroup(roleGroupId, projectName);

			// Ensure the subgroup was successfully created
			if (groupRepresentation == null) {
				log.debug("Group for project \"" + projectName + "\" could not be created.");
				throw new ProjectOIDCException(projectName);
			}

			// Assign the new role to the created subgroup
			oidcService.assignRoleToGroup(groupRepresentation.getId(), roleRepresentation);

			// Add the newly created group's ID to the list
			projectGroupIds.add(groupRepresentation.getId());
		}

        // Ensure that the user is added to all role-groups
        // Note: adding a user to a group he/she is already part of should not return any errors
        if (!oidcService.addUserToGroups(oidcService.getKingRoleGroupIDs(), userId)) {
            log.debug("Could not add the user \"" + userId + "\" to all operation-groups.");
            throw new ProjectOIDCException(projectName);
        }

        // Add the user to the newly created project groups
        if (!oidcService.addUserToGroups(projectGroupIds, userId)) {
            log.debug("Could not add the user \"" + userId + "\" to newly created groups for project \"" + projectName + "\".");
            throw new ProjectOIDCException(projectName);
        }

        // Update cache info
        cachingService.flushAndReCacheMatchingGroups(userId, projectName, true);

        // Add the project name as a role to the user
        oidcService.addRoleToUser(projectName, userId);
    }

    /**
     * Removes all users from groups containing this project name and deletes the groups.
     * Updates the cache entries for the affected users.
     * Additionally, the role for this projectName is removed from all users and then deleted.
     *
     * @param projectName the name of the project that should be purged from Keycloak
     */
    public void leaveAndDeleteProjectGroupsAndRoles(String projectName) {
        // Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroupsWithSubGroups(), true);

        // Iterate over all groups to find those that need to be removed
        for (Map.Entry<String, String> e : flatGroupPaths.entrySet()) {
            if (e.getValue().endsWith(projectName)) {
                // Found a group that needs to be removed; iterate over all users in it and remove it
                for (UserRepresentation user : oidcService.getKeycloakRealm().groups().group(e.getKey()).members()) {
                    log.debug("Removing user \"" + user.getId() + "\" from group: " + e.getValue());
                    oidcService.removeUserFromGroup(e.getKey(), user.getId());

                    // Update cache for the user
                    cachingService.flushAndReCacheMatchingGroups(user.getId(), projectName);
                }

                // Additionally, remove the associated group from the Keycloak client entirely
                log.debug("Removing group from Keycloak: " + e.getValue());
                oidcService.removeGroupById(e.getKey());
            }
        }

        // Remove the group role from all users
        for (UserRepresentation user : oidcService.getClientResource().roles().get(projectName).getUserMembers()) {
            log.debug("Removing role \"" + projectName + "\" from user \"" + user.getId() + "\".");
            oidcService.removeRoleFromUser(projectName, user.getId());
        }

        // Additionally, remove the role from the Keycloak client entirely
        log.debug("Removing role \"" + projectName + "\" from Keycloak.");
        oidcService.removeClientRole(projectName);
    }

    /**
     * Removes all role-groups from Keycloak except the basic, 
     * non-project-specific roles by first removing all users 
     * from the group and then removing the group from Keycloak.
     * Initiates a cache update on the removed users.
     * The audit trail user type names are also kept as groups.
     */
    public void deleteAllProjectGroups() {
        List<String> allRroles = roleConfig.getAllRoles();
        List<String> projectRoles = roleConfig.getKINGRoles();
        List<String> auditUserTypeNames = getAuditUserGroupNames();

        // Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroupsWithSubGroups(), true);

        // Iterate over all available groups
        for (Map.Entry<String, String> e : flatGroupPaths.entrySet()) {
            // Extract the last part of the group path
            String groupPathEnding = e.getValue().substring(e.getValue().lastIndexOf("/") + 1);

            // If the last part is an audit user type group or the group context (either domain- or project-specific), keep the group
            if (auditUserTypeNames.contains(groupPathEnding) 
            		|| jwtProperties.getDomainRoleGroupContextName().equals(groupPathEnding)
            		|| jwtProperties.getProjectRoleGroupContextName().equals(groupPathEnding)) {
                continue;
            }

            // If the last part is a role name, keep the group; if it's a domain name, keep it; if it's a project name, remove it
            if (!allRroles.contains(groupPathEnding)) {
                // Found a domain or project name in the group path ending; check if the path contains a project-role-name
            	String pathWithoutEnding = e.getValue().substring(0, e.getValue().length() - groupPathEnding.length() - 1);
            	String roleName = pathWithoutEnding.substring(pathWithoutEnding.lastIndexOf("/") + 1);
            	if (projectRoles.contains(roleName)) {
            		// Found a project name, remove the associated group for every user that has it
	                for (UserRepresentation user : oidcService.getKeycloakRealm().groups().group(e.getKey()).members()) {
	                    log.debug("Removing user from group: " + e.getValue());
	                    oidcService.removeUserFromGroup(e.getKey(), user.getId());
	
	                    // Update cache for the user
	                    cachingService.flushAndReCacheMatchingGroups(user.getId(), groupPathEnding);
	                }
	
	                // Additionally, remove the role from the Keycloak client entirely
	                log.debug("Removing group from Keycloak: " + e.getValue());
	                oidcService.removeGroupById(e.getKey());
            	}
            }
        }
    }

    /**
     * Removes all non-operation roles for all users. Deletes the roles from Keycloak.
     */
    public void deleteAllProjectRoles() {
        List<String> roles = roleConfig.getKINGRoles();

        // Remove any non-operation role
        for (String role : oidcService.getClientRoles()) {
            if (roles.contains(role)) {
                // Found a project name-role, delete it for every user that has it
                for (UserRepresentation user : oidcService.getClientResource().roles().get(role).getUserMembers()) {
                    log.debug("Removing role from user: " + role);
                    oidcService.removeRoleFromUser(role, user.getId());
                }

                // Additionally, remove the role from the Keycloak client entirely
                log.debug("Removing role from Keycloak: " + role);
                oidcService.removeClientRole(role);
            }
        }
    }

    /**
     * Updates the name of project-related groups and roles within the Keycloak server.
     *
     * @param oldProjectName the name of the project to be updated
     * @param newProjectName the new name to be assigned to the project-related roles and groups
     */
    public void updateProjectGroups(String oldProjectName, String newProjectName, String userId) {
        this.leaveAndDeleteProjectGroupsAndRoles(oldProjectName);
        this.createProjectGroupsAndRolesAndJoin(newProjectName, userId);
    }
    
    /**
     * Checks if a project name can be used as a group name within the Keycloak realm.
     * This method iterates through all role-groups and checks if any sub-group
     * with the specified project name already exists. If a matching group is found, it
     * returns {@code false}, indicating that the project name is already in use.
     *
     * @param projectName the project name to be checked
     * @return {@code true} if the project name is not in use, or {@code false} if a group with this name already exists
     */
    public Boolean canBeUsedAsProjectGroup(String projectName) {
        // Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroupsWithSubGroups(), true);

        // Iterate over each operation-role defined in the configuration
        for (String role : roleConfig.getKINGRoles()) {
            // Check if a sub-group with the specified project name already exists
            Map.Entry<String, String> matches = Utility.findGroupEntryByPath(flatGroupPaths, "/" + jwtProperties.getProjectRoleGroupContextName() + "/" + role + "/" + projectName);

            // Return false if a matching group is found
            if (matches != null) {
                return false;
            }
        }

        // Return true if no matching group is found
        return true;
    }

    /**
     * Helper method to store the names of the audit user type group names in a list.
     *
     * @return the audit user type group names in a list
     */
    private List<String> getAuditUserGroupNames() {
        List<String> auditUserGroupNames = new ArrayList<String>();

        auditUserGroupNames.add(auditUserConfig.getAuditEverythingUserGroupName());
        auditUserGroupNames.add(auditUserConfig.getHumanUserGroupName());
        auditUserGroupNames.add(auditUserConfig.getNoAuditingUserGroupName());
        auditUserGroupNames.add(auditUserConfig.getTechnicalUserGroupName());

        return auditUserGroupNames;
    }
}

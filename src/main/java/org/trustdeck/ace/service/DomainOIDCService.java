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

package org.trustdeck.ace.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.ace.configuration.RoleConfig;
import org.trustdeck.ace.exception.DomainOIDCException;
import org.trustdeck.ace.security.audittrail.usertype.AuditUserTypeConfiguration;
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
    
    /** Audit trail configuration that includes the names of the user types. */
    @Autowired
    private AuditUserTypeConfiguration auditUserConfig;

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
    public void createDomainGroupsAndRolesAndJoin(String domainName, String userId) {
        // Create a new role for the specified domain name
        RoleRepresentation roleRepresentation = oidcService.createClientRole(domainName);

        if (!canBeUsedAsDomainGroup(domainName)) {
        	log.debug("The domain name cannot be used in Keycloak (there might already exist a role with this name).");
            throw new DomainOIDCException(domainName);
        }

        // List to store the IDs of the newly created domain groups in
        List<String> domainGroupIds = new ArrayList<>();

        // Iterate over each operation group and create a subgroup for the domain
        for (String operationGroupId : oidcService.getOperationGroupIDs()) {
            GroupRepresentation groupRepresentation = oidcService.createSubGroup(operationGroupId, domainName);

            // Ensure the subgroup was successfully created
            if (groupRepresentation == null) {
            	log.debug("Group for domain \"" + domainName + "\" could not be created.");
                throw new DomainOIDCException(domainName);
            }

            // Assign the new role to the created subgroup
            oidcService.assignRoleToGroup(groupRepresentation.getId(), roleRepresentation);

            // Add the newly created group's ID to the list
            domainGroupIds.add(groupRepresentation.getId());
        }

        // Ensure that the user is added to all operation-groups
        // Note: adding a user to a group he/she is already part of should not return any errors
        if (!oidcService.joinGroups(oidcService.getOperationGroupIDs(), userId)) {
        	log.debug("Could not add the user \"" + userId + "\" to all operation-groups.");
        	throw new DomainOIDCException(domainName);
        }
        
        // Add the user to the newly created domain groups
        if (!oidcService.joinGroups(domainGroupIds, userId)) {
            log.debug("Could not add the user \"" + userId + "\" to newly created groups for domain \"" + domainName + "\".");
        	throw new DomainOIDCException(domainName);
        }

        // Update cache info
        cachingService.flushAndReCacheMatchingGroups(userId, domainName, true);
        
        // Add the domain name as a role to the user
        oidcService.addRoleToUser(domainName, userId);
    }
    
    /**
     * Removes all users from groups containing this domain name and deletes the groups.
     * Updates the cache entries for the affected users.
     * Additionally, the role for this domainName is removed from all users and then deleted.
     * 
     * @param domainName the name of the domain that should be purged from Keycloak
     */
    public void leaveAndDeleteDomainGroupsAndRoles(String domainName) {
    	// Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);
    	
        // Iterate over all groups to find those that need to be removed
        for (Map.Entry<String, String> e : flatGroupPaths.entrySet()) {
        	if (e.getValue().endsWith(domainName)) {
        		// Found a group that needs to be removed; iterate over all users in it and remove it
				for (UserRepresentation user : oidcService.getKeycloakRealm().groups().group(e.getKey()).members()) {
		    		log.debug("Removing user \"" + user.getId() + "\" from group: " + e.getValue());
					oidcService.removeUserFromGroup(e.getKey(), user.getId());
					
					// Update cache for the user
					cachingService.flushAndReCacheMatchingGroups(user.getId(), domainName);
				}
				
				// Additionally, remove the associated group from the Keycloak client entirely
				log.debug("Removing group from Keycloak: " + e.getValue());
		        oidcService.removeGroupById(e.getKey());
        	}
        }
        
        // Remove the group role from all users
        for (UserRepresentation user : oidcService.getClientResource().roles().get(domainName).getUserMembers()) {
    		log.debug("Removing role \"" + domainName + "\" from user \"" + user.getId() + "\".");
			oidcService.removeRoleFromUser(domainName, user.getId());
		}
		
		// Additionally, remove the role from the Keycloak client entirely
		log.debug("Removing role \"" + domainName + "\" from Keycloak.");
        oidcService.removeClientRole(domainName);
    }

    /**
     * Removes all non-operation-role-groups from Keycloak by first removing all users 
     * from the group and then removing the group from Keycloak.
     * Initiates a cache update on the removed users.
     * The audit trail user type names are also kept as groups.
     */
    public void deleteAllDomainGroups() {
        List<String> operations = roleConfig.getOperations();
        List<String> auditUserTypeNames = getAuditUserGroupNames();
        
    	// Retrieve a flat map of group paths for all groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(oidcService.getRealmGroups(), true);

        // Iterate over all available groups
        for (Map.Entry<String, String> e : flatGroupPaths.entrySet()) {
        	// Extract the last part of the group path
        	String groupPathEnding = e.getValue().substring(e.getValue().lastIndexOf("/") + 1);
        	
        	// If the last part is an audit user type group or the group context, keep the group
        	if (auditUserTypeNames.contains(groupPathEnding) || jwtProperties.getDomainRoleGroupContextName().equals(groupPathEnding)) {
        		continue;
        	}
        	
        	// If the last part is a operation role name, keep the group; if it's a domain name, remove it
        	if (!operations.contains(groupPathEnding)) {
        		// Found a domain name, remove the associated group for every user that has it
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
    
    /**
     * Removes all non-operation roles for all users. Deletes the roles from Keycloak.
     */
    public void deleteAllDomainRoles() {
        List<String> operations = roleConfig.getOperations();
        
        // Remove any non-operation role
        for (String role : oidcService.getClientRoles()) {
        	if (!operations.contains(role)) {
        		// Found a domain name-role, delete it for every user that has it
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
     * Updates the name of domain-related groups and roles within the Keycloak server.
     *
     * @param oldDomainName the name of the domain to be updated
     * @param newDomainName the new name to be assigned to the domain-related roles and groups
     * @throws NullPointerException if the new role cannot be created, or the old role cannot be deleted, or a group name update fails
     */
    public void updateDomainGroups(String oldDomainName, String newDomainName, String userId) {
        this.leaveAndDeleteDomainGroupsAndRoles(oldDomainName);
        this.createDomainGroupsAndRolesAndJoin(newDomainName, userId);
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

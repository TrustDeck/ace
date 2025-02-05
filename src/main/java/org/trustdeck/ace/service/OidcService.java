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

import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.ace.configuration.RoleConfig;
import org.trustdeck.ace.exception.OIDCException;
import org.trustdeck.ace.exception.UnexpectedResultSizeException;
import org.trustdeck.ace.security.authentication.configuration.JwtProperties;
import org.trustdeck.ace.utils.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class provides services for managing clients, roles, and groups
 * within a Keycloak realm. It uses the Keycloak Admin Client to interact with the server and
 * offers various operations, such as creating roles, groups, and assigning roles to groups.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Slf4j
@Service
public class OidcService implements InitializingBean {

    /** Configuration properties for JWT (JSON Web Token) authentication. */
    @Autowired
    protected JwtProperties jwtProperties;

    /** Configuration for defining the operation-roles required by the application. */
    @Autowired
    protected RoleConfig roleConfig;

    /** Keycloak client instance used for interacting with the Keycloak Admin API. */
    @Getter
    private Keycloak keycloak;

    /** Unique identifier (UUID) for the configured Keycloak client. */
    @Getter
    private String clientUUID;

    /** List of group IDs representing the operation groups in the Keycloak server. */
    @Getter
    private List<String> operationGroupIDs;

    /**
     * Initializes the Keycloak client and sets up the necessary roles and groups.
     * Called by the Spring framework after the OidcService-Bean's properties have been set.
     */
    @Override
    public void afterPropertiesSet() {
        init();
    }

    /**
     * Retrieves the Keycloak realm resource based on the configured realm name.
     * The realm resource is the access point for managing all aspects of a Keycloak realm.
     *
     * @return {@link RealmResource} the Keycloak realm resource for the configured realm.
     */
    protected RealmResource getKeycloakRealm() {
        //TODO this is currently needed because the test destroys the Keycloak instance and then uses it.
        this.keycloak = initKeycloak();
        return this.keycloak.realm(jwtProperties.getRealm());
    }

    /**
     * Retrieves the client resource from Keycloak based on the configured client ID.
     * If the client UUID is already known, it uses it directly; otherwise, it searches for the client by its ID.
     *
     * @return {@link ClientResource} the client resource for the specified client.
     * @throws UnexpectedResultSizeException if the number of clients with the given ID is not exactly one.
     */
    protected ClientResource getClientResource() throws UnexpectedResultSizeException {
        ClientsResource clients = this.getKeycloakRealm().clients();

        // Check if the client UUID is already stored.
        if (this.clientUUID != null) {
            return clients.get(this.clientUUID);
        }

        // Search for the client by its client ID
        List<ClientRepresentation> potentialClients = clients.findByClientId(jwtProperties.getClientId());

        // If there is not exactly one client, throw an exception.
        if (potentialClients.size() != 1) {
            throw new UnexpectedResultSizeException(1, potentialClients.size());
        }

        // Store the client UUID and return the client resource.
        String clientId = potentialClients.getFirst().getId();
        return clients.get(clientId);
    }

    /**
     * Initializes the Keycloak client and sets up the necessary groups and roles.
     * This method ensures that the required roles and groups are present in the Keycloak realm.
     */
    protected void init() {
        // Initialize the Keycloak client
        this.keycloak = initKeycloak();

        try {
            // Retrieve and store the UUID of the configured client
            this.clientUUID = this.getClientResource().toRepresentation().getId();
            log.debug("OIDC client resource is available.");
        } catch (UnexpectedResultSizeException e) {
            log.error("OIDC resource not available. Did not find exactly one client.\n\t" + e + " Expected: 1. Actual: " + e.getActualSize() + ".\n");
        }

        try {
            // Create the required operation groups
            this.createOperationGroups();
        } catch (Exception e) {
            log.error("OIDC resource could not be prepared:\n\t" + e.getMessage());
        }
    }

    /**
     * Builds and returns a new Keycloak client instance using the configured properties.
     *
     * @return {@link Keycloak} a new Keycloak client instance for the configured server and credentials.
     */
    private Keycloak initKeycloak() {
        return KeycloakBuilder.builder()
                .grantType(OAuth2Constants.PASSWORD)           // Use password grant type for authentication.
                .realm(jwtProperties.getRealm())               // Set the target realm.
                .clientId(jwtProperties.getClientId())         // Set the client ID.
                .username(jwtProperties.getAdminUsername())    // Set the admin username.
                .password(jwtProperties.getAdminPassword())    // Set the admin password.
                .serverUrl(jwtProperties.getServerUri())       // Set the server URL.
                .clientSecret(jwtProperties.getClientSecret()) // Set the client secret (if applicable).
                .build();
    }

    /**
     * Creates and configures the necessary operation roles and groups in the Keycloak server.
     *
     * @throws OIDCException if any step fails during the creation or assignment of roles and groups.
     */
    private void createOperationGroups() throws OIDCException {
        // Retrieve the list of existing roles for the Keycloak client
        List<String> clientRoles = this.getClientRoles();

        // Loop through each operation role defined in the configuration.
        for (String role : roleConfig.getOperations()) {
            boolean needsToBeAdded = true;

            // Check if the role is already present in the client. If not, add it.
            if (!clientRoles.contains(role)) {

                // Create each new role in the Keycloak server
                if (this.createClientRole(role) == null) {
                    throw new OIDCException("Creating role \"" + role + "\" failed.");
                }
            }
        }

        // Retrieve the main group (container group) for the domain roles
        GroupRepresentation mainGroup = this.getMainGroup();

        // Fail if the main group is not present in Keycloak.
        if (mainGroup == null) {
            throw new OIDCException("Main group \"" + jwtProperties.getDomainRoleGroupContextName() + "\" not found.");
        }

        // Retrieve a flat map of all existing groups in the realm
        Map<String, String> flatGroupPaths = Utility.flattenGroupIDToPathMapping(this.getRealmGroups(), true);

        // List to store group names that need to be created
        List<String> newGroups = new ArrayList<>();

        // Check for each operation role if a corresponding group path exists
        for (String role : roleConfig.getOperations()) {
            Map.Entry<String, String> groupEntry = Utility.findGroupEntryByPath(flatGroupPaths, "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + role);

            if (groupEntry == null) {
                // If the group path does not exist, mark the role as "needing a new group"
                newGroups.add(role);
            } else {
                // If the group path exists, ensure the role is assigned to the group
                RoleRepresentation roleRepresentation = this.getClientRoleByName(role);

                if (roleRepresentation == null) {
                    // The role should have been created earlier or it should have been already available
                    throw new OIDCException("The role \"" + role + "\" is not present in Keycloak while it should be.");
                }

                // Assign the role to the group
                this.assignRoleToGroup(groupEntry.getKey(), roleRepresentation);
            }
        }

        // Create new groups for roles that do not yet have corresponding groups
        for (String newGroup : newGroups) {
            // Create a new subgroup under the main group
            GroupRepresentation groupRepresentation = this.createSubGroup(mainGroup.getId(), newGroup);

            if (groupRepresentation == null) {
                throw new OIDCException("Group \"" + newGroup + "\" could not be created.");
            }

            // Retrieve the role representation for the new group
            RoleRepresentation roleRepresentation = this.getClientRoleByName(newGroup);

            if (roleRepresentation == null) {
                // The role should have been created earlier or it should have been already available
                throw new OIDCException("The role \"" + newGroup + "\" is not present in Keycloak while it should be.");
            }

            // Assign the role to the newly created group
            this.assignRoleToGroup(groupRepresentation.getId(), roleRepresentation);
        }

        // Re-fetch the list of all groups after creation to ensure consistency
        Map<String, String> finalGroupPaths = Utility.flattenGroupIDToPathMapping(this.getRealmGroups(), true);

        // List to store the group IDs for each role
        List<String> groupIds = new ArrayList<>();

        // Match the created roles to their group IDs based on paths
        for (String role : roleConfig.getOperations()) {
            Map.Entry<String, String> groupEntry = Utility.findGroupEntryByPath(finalGroupPaths, "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + role);

            if (groupEntry != null) {
                groupIds.add(groupEntry.getKey());
            }
        }

        // Ensure that the number of groups matches the number of roles
        if (groupIds.size() != roleConfig.getOperations().size()) {
            throw new OIDCException("Could not find all groups and roles that should have been added.");
        }

        // Cache the standard group IDs for future use
        this.operationGroupIDs = groupIds;
    }

    /**
     * Creates a new client role in the Keycloak server.
     * This method creates a role with the specified name and assigns it as a client role for the currently configured
     * Keycloak client. If the role is successfully created, it retrieves and returns the created role representation.
     *
     * @param roleName the name of the role to be created
     * @return {@link RoleRepresentation} the representation of the newly created role, or {@code null} if the role could not be created
     */
    public RoleRepresentation createClientRole(String roleName) {
        try {
            // Create a new RoleRepresentation object to define the role attributes
            RoleRepresentation roleRepresentation = new RoleRepresentation();

            roleRepresentation.setName(roleName);
            roleRepresentation.setClientRole(true); // Mark the role as a client role (specific to a client in contrast to global realm roles)

            // Create the new role in the Keycloak server
            this.getClientResource().roles().create(roleRepresentation);

            // Retrieve and return the created role by its name to confirm its creation
            return this.getClientRoleByName(roleName);
        } catch (Exception e) {
            // Log the exception message and return null if an error occurs
            log.error("Could not create client role: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves a list of all client role names for the currently configured Keycloak client.
     * This method interacts with the Keycloak Admin API to fetch all roles defined for the client specified
     * in the {@link JwtProperties}. It returns a list of the names of these roles. Each role is represented
     * using the {@link RoleRepresentation} class, and only the role names are extracted and returned.
     *
     * @return {@link List} of {@link String} containing the names of all roles associated with the configured client,
     * or {@code null} if the role is not found or an error occurs.
     */
    public List<String> getClientRoles() {
        List<String> clientRoles = null;

        try {
            clientRoles = this.getClientResource().roles().list().stream()
                                .map(RoleRepresentation::getName)
                                .toList();
        } catch (Exception e) {
            log.error("Could not retrieve client roles: " + e.getMessage());
        }

        return clientRoles;
    }

    /**
     * Retrieves the representation of a client role by its name.
     *
     * @param roleName the name of the role to be retrieved
     * @return {@link RoleRepresentation} containing the details of the specified role, or {@code null} if the role is not found or an error occurs.
     */
    public RoleRepresentation getClientRoleByName(String roleName) {
        try {
            // Access the roles resource for the client and retrieve the role its name
            // Convert the role to a RoleRepresentation object
            return this.getClientResource().roles().get(roleName).toRepresentation();
        } catch (Exception e) {
            // Log the exception message if an error occurs
            log.error("Could not retrieve client role by its name: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a specified client role from the Keycloak server.
     *
     * @param roleRepresentations the {@link RoleRepresentation} object representing the role to be deleted
     * @return {@code true} if the role is successfully deleted, or {@code false} if an error occurs.
     */
    public Boolean removeClientRole(RoleRepresentation roleRepresentations) {
        try {
            // Retrieve the roles resource object and delete the role
            RolesResource rolesResource = this.getClientResource().roles();
            rolesResource.deleteRole(roleRepresentations.getName());

            return true;
        } catch (Exception e) {
            // Log the exception message if an error occurs
            log.error("Could not delete client role: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates the name of an existing group in the Keycloak server.
     *
     * @param groupId the ID of the group that needs to be updated
     * @param newName the new name to be assigned to the group
     * @return {@code true} if the group name is successfully updated, or {@code false} if an error occurs.
     */
    public Boolean updateGroupName(String groupId, String newName) {
        try {
            // Retrieve the group representation by its ID from the realm
            GroupRepresentation groupRepresentation = this.getKeycloakRealm().groups().group(groupId).toRepresentation();

            // Set the new name for the group
            groupRepresentation.setName(newName);

            // Retrieve the group resource again by ID and update the group with the new name.
            GroupResource groupResource = this.getKeycloakRealm().groups().group(groupRepresentation.getId());
            groupResource.update(groupRepresentation);

            return true;
        } catch (Exception e) {
            // Log any exception that occurs during the update process
            log.error("Could not update group name: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves a list of all groups within the configured Keycloak realm.
     *
     * @return {@link List} of {@link GroupRepresentation} containing all groups in the current realm.
     */
    public List<GroupRepresentation> getRealmGroups() {
        // search query: "" (empty string) to retrieve all groups
        // briefRepresentation: false, to include detailed group information
        return this.getKeycloakRealm().groups().groups("", false, 0, Integer.MAX_VALUE, false);
    }

    /**
     * Retrieves the main group representation from the Keycloak server.
     *
     * @return {@link GroupRepresentation} of the main group if found, or {@code null} if no such group exists.
     */
    public GroupRepresentation getMainGroup() {
        // Retrieve all groups from the Keycloak realm
        List<GroupRepresentation> groups = this.getRealmGroups();

        // Construct the expected path for the main group using the configured context name
        String path = "/" + jwtProperties.getDomainRoleGroupContextName();

        // Iterate through the list of groups to find the group that matches the expected path
        for (GroupRepresentation group : groups) {
            if (group.getPath().equals(path)) {
                // Return the group representation if the path matches
                return group;
            }
        }

        // Return null if no group with the specified path is found
        return null;
    }

    /**
     * Retrieves a list of groups that the specified user belongs to.
     *
     * @param userId the unique identifier of the user for whom the groups are to be retrieved.
     * @return {@link List} of {@link GroupRepresentation} containing all groups the user is a member of,
     *      or an empty list if the user ID is invalid or the user has no groups.
     */
    public List<GroupRepresentation> getGroupsByUserId(String userId) {
        // Check if the userId is null or empty, and return an empty list if true
        if (userId == null || userId.isBlank()) {
            return new ArrayList<>();
        }

        // Retrieve the list of groups the user is a member of
        List<GroupRepresentation> groupRepresentations = this.getKeycloakRealm().users().get(userId).groups();

        // Return the list of group representations
        return groupRepresentations == null ? new ArrayList<>() : groupRepresentations;
    }

    /**
     * Creates a new subgroup under a specified parent group in the Keycloak server.
     *
     * @param parentGroupId the unique identifier of the parent group under which the new subgroup will be created
     * @param groupName the name of the new subgroup to be created
     * @return {@link GroupRepresentation} of the newly created subgroup, or {@code null} if the creation fails.
     */
    public GroupRepresentation createSubGroup(String parentGroupId, String groupName) {
        // Create a new GroupRepresentation object to represent the new subgroup
        GroupRepresentation subGroup = new GroupRepresentation();
        subGroup.setName(groupName);

        try {
            // Use the Keycloak API to create the new subgroup under the specified parent group
            Response response = this.getKeycloakRealm().groups().group(parentGroupId).subGroup(subGroup);

            // Check if the response status indicates successful creation of the group
            GroupRepresentation returnedGroupRepresentation = null;
            if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                // Read the created group representation from the response
                returnedGroupRepresentation = response.readEntity(GroupRepresentation.class);
            }
            response.close(); // Close the response to release resources

            // Return the created group representation, or null if the creation was not successful.
            return returnedGroupRepresentation;
        } catch (Exception e) {
            // Log the error if any exception occurs during the subgroup creation process
            log.error("Creating the subgroup in Keycloak failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a group from the Keycloak server using its unique identifier.
     *
     * @param groupId the unique identifier of the group to be deleted
     */
    public void removeGroupById(String groupId) {
        try {
            // Use the Keycloak API to delete the group with the specified ID
            this.getKeycloakRealm().groups().group(groupId).remove();
        } catch (Exception e) {
            // Log the exception message if an error occurs during group deletion
            log.error("Deleting the group in Keycloak failed: " + e.getMessage());
        }
    }

    /**
     * Assigns a specified client role to a group in the Keycloak server.
     *
     * @param groupId the unique identifier of the group to which the role will be assigned
     * @param roleRepresentation the {@link RoleRepresentation} object representing the role to be assigned
     */
    public void assignRoleToGroup(String groupId, RoleRepresentation roleRepresentation) {
        try {
            // Assign the specified role to the group using the client-level roles API
            this.getKeycloakRealm().groups().group(groupId).roles().clientLevel(this.clientUUID).add(List.of(roleRepresentation));
        } catch (Exception e) {
            // Catch and ignore any exceptions during role assignment
        }
    }

    /**
     * Adds a user to a specified group in the Keycloak server.
     *
     * @param groupId the unique identifier of the group that the user will join
     * @param userId  the unique identifier of the user to be added to the group
     * @return {@code true} if the user is successfully added to the group, or {@code false} if an error occurs.
     */
    public Boolean addUserToGroup(String groupId, String userId) {
        try {
            this.getKeycloakRealm().users().get(userId).joinGroup(groupId);
            return true;
        } catch (Exception e) {
            log.error("Adding the user (id: " + userId + ") to the group (id: " + groupId + ") failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Adds a user to multiple groups in the Keycloak server.
     *
     * @param groupIds a list of group IDs representing the groups to which the user will be added
     * @param userId the unique identifier of the user to be added to the groups
     * @return {@code true} if the user is successfully added to all groups, or {@code false} if any operation fails.
     */
    public Boolean joinGroups(List<String> groupIds, String userId) {
        for (String groupId : groupIds) {
            // Add the user to each group in the list
            if (!this.addUserToGroup(groupId, userId)) {
                return false;
            }
        }
        return true;
    }
}

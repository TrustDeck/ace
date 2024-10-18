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
import org.trustdeck.ace.exception.UnexpectedResultSizeException;
import org.trustdeck.ace.security.authentication.configuration.JwtProperties;
import org.trustdeck.ace.utils.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The {@code OidcService} class provides services for managing clients, roles, and groups
 * within a Keycloak realm. It uses the Keycloak Admin Client to interact with the server and
 * offers various operations, such as creating roles, groups, and assigning roles to groups.
 *
 * <p>This service is used primarily for ensuring that specific roles and group structures
 * are present in the Keycloak realm during startup, as well as for maintaining and managing
 * roles and groups dynamically.</p>
 *
 * <p>The class implements {@link InitializingBean} to perform custom initialization logic
 * once Spring has injected all dependencies and properties. It also uses the {@code @Service}
 * annotation, making it a managed Spring bean.</p>
 *
 * <p>Authors: Eric Wündisch & Armin Müller</p>
 */
@Slf4j
@Service
public class OidcService implements InitializingBean {

    /**
     * Configuration properties for JWT (JSON Web Token) authentication.
     * <p>
     * This property is injected by Spring and contains information related to the JWT configuration,
     * such as the realm name, client ID, server URL, and administrator credentials. It is used
     * throughout the class to interact with the Keycloak server based on the configured settings.
     * </p>
     */
    @Autowired
    protected JwtProperties jwtProperties;

    /**
     * Configuration for defining the operational roles required by the application.
     * <p>
     * This property is injected by Spring and contains information about the roles and groups
     * that need to be created and managed in the Keycloak realm. It is used during the setup
     * process to ensure that the required roles and groups are present.
     * </p>
     */
    @Autowired
    protected RoleConfig roleConfig;

    /**
     * Keycloak client instance used for interacting with the Keycloak Admin API.
     * <p>
     * This instance is initialized during the startup process and is used to perform various
     * administrative operations on the configured Keycloak realm, such as managing clients,
     * groups, and roles.
     * </p>
     * <p><b>Note:</b> The {@code @Getter} annotation from Lombok is used to generate a getter method for this field.</p>
     */
    @Getter
    private Keycloak keycloak;

    /**
     * Unique identifier (UUID) for the configured Keycloak client.
     * <p>
     * This field stores the UUID of the client that is being managed within the Keycloak realm.
     * It is used to perform operations that are specific to this client, such as creating and
     * assigning roles. The UUID is retrieved during the initialization process.
     * </p>
     * <p><b>Note:</b> The {@code @Getter} annotation from Lombok is used to generate a getter method for this field.</p>
     */
    @Getter
    private String clientUuid;

    /**
     * List of group IDs representing the operational groups in the Keycloak server.
     * <p>
     * This field stores the IDs of groups that are associated with specific operational roles
     * within the configured Keycloak realm. These group IDs are cached to optimize operations
     * that require frequent access to these groups, such as role assignments and group management.
     * </p>
     * <p><b>Note:</b> The {@code @Getter} annotation from Lombok is used to generate a getter method for this field.</p>
     */
    @Getter
    private List<String> operationGroupIds;


    /**
     * Called by the Spring framework after properties have been set.
     * Initializes the Keycloak client and sets up the necessary roles and groups.
     *
     * @throws Exception If any initialization step fails.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }


    /**
     * Retrieves the Keycloak realm resource based on the configured realm name.
     * The realm resource is the root access point for managing all aspects of a Keycloak realm.
     *
     * @return {@link RealmResource} The Keycloak realm resource for the configured realm.
     */
    protected RealmResource getKeycloakRealm() {
        //TODO this currently needed because the test destroys the instance and then uses it. i assume there is some csrf token issue
        this.keycloak = initKeycloak();
        return this.getKeycloak().realm(jwtProperties.getRealm());
    }


    /**
     * Retrieves the client resource from Keycloak based on the configured client ID.
     * If the client UUID is already known, it uses that directly; otherwise, it searches for the client by its ID.
     *
     * @return {@link ClientResource} The client resource for the specified client.
     * @throws UnexpectedResultSizeException If the number of clients with the given ID is not exactly one.
     */
    protected ClientResource getClientResource() throws UnexpectedResultSizeException {
        // Check if the client UUID is already stored.
        if (this.clientUuid != null) {
            return this.getKeycloakRealm().clients().get(this.clientUuid);
        }

        // Search for the client by its client ID.
        List<ClientRepresentation> clients = this.getKeycloakRealm().clients().findByClientId(jwtProperties.getClientId());

        // If there is not exactly one client, throw an exception.
        if (clients.size() != 1) {
            throw new UnexpectedResultSizeException(1, clients.size());
        }

        // Store the client UUID and return the client resource.
        String clientId = clients.get(0).getId();
        return this.getKeycloakRealm().clients().get(clientId);
    }


    /**
     * Initializes the Keycloak client and sets up the necessary groups and roles.
     * This method ensures that the required roles and groups are present in the Keycloak realm.
     */
    protected void init() {
        // Initialize the Keycloak client.
        this.keycloak = initKeycloak();

        try {
            // Retrieve and store the UUID of the configured client.
            ClientResource clientResource = this.getClientResource();
            this.clientUuid = clientResource.toRepresentation().getId();
            log.debug("OIDC client resource available.");
        } catch (UnexpectedResultSizeException e) {
            log.error("OIDC resource not available.\n\t" + e + " Expected: " + e.getExpectedSize()
                    + ". Actual: " + e.getActualSize() + ".\n");
        }

        try {
            // Create the required operation groups.
            this.createOperationGroups();
        } catch (Exception e) {
            log.error("OIDC resource could not be prepared:\n\t" + e.getMessage());
        }
    }


    /**
     * Builds and returns a new Keycloak client instance using the configured properties.
     *
     * @return {@link Keycloak} A new Keycloak client instance for the configured server and credentials.
     */
    private Keycloak initKeycloak() {
        return KeycloakBuilder.builder()
                .grantType(OAuth2Constants.PASSWORD)          // Use password grant type for authentication.
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
     * <p>
     * This method performs the following operations:
     * <ul>
     *     <li>Identifies any missing roles in the Keycloak client and creates them.</li>
     *     <li>Ensures the main group that acts as a container for operation groups is present.</li>
     *     <li>Creates sub-groups under the main group for each role, if not already present.</li>
     *     <li>Assigns the roles to their corresponding groups.</li>
     *     <li>Caches the group IDs for future use and consistency checks.</li>
     * </ul>
     * <p>
     * This method will throw an exception if any of the roles or groups cannot be created,
     * or if there is a mismatch between the expected and actual group structures.
     * </p>
     *
     * @throws Exception If any step fails during the creation or assignment of roles and groups.
     */
    private void createOperationGroups() throws Exception {
        // Retrieve the list of existing roles for the Keycloak client.
        List<String> clientRoles = this.getClientRoles();

        // List to store roles that need to be created.
        List<String> newRoles = new ArrayList<>();

        // Loop through each operation role defined in the configuration.
        for (String role : roleConfig.getOperations()) {
            boolean add = true;

            // Check if the role is already present in the client.
            for (String clientRole : clientRoles) {
                if (clientRole.equals(role)) {
                    add = false;  // Mark as false if role already exists.
                    break;
                }
            }

            // If the role is not found, add it to the list of new roles.
            if (add) {
                newRoles.add(role);
            }
        }

        // Create each new role in the Keycloak server.
        for (String newRole : newRoles) {
            if (this.createClientRole(newRole) == null) {
                throw new NullPointerException("The role already exists, but should not be");
            }
        }

        // Retrieve the main group (container group) for the domain roles.
        GroupRepresentation mainGroup = this.getMainGroup();

        // Fail if the main group is not present in Keycloak.
        if (mainGroup == null) {
            throw new NullPointerException("Main group " + jwtProperties.getDomainRoleGroupContextName() + " not found");
        }

        // Retrieve a flat map of all existing groups in the realm.
        Map<String, String> flatGroupPaths = Utility.flatGroups(this.getRealmGroups(), true);

        // List to store group names that need to be created.
        List<String> newGroups = new ArrayList<>();

        // Check for each operation role if a corresponding group path exists.
        for (String role : roleConfig.getOperations()) {
            Map.Entry<String, String> g = Utility.getMatchByPath(flatGroupPaths, "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + role);

            if (g == null) {
                // If the group path does not exist, mark the role as needing a new group.
                newGroups.add(role);
            } else {
                // If the group path exists, ensure the role is assigned to the group.
                RoleRepresentation roleRepresentation = this.getClientRoleByName(role);

                if (roleRepresentation == null) {
                    throw new NullPointerException("The role " + role + " does not present");
                }

                // Assign the role to the group.
                this.assignRoleToGroup(g.getKey(), roleRepresentation);
            }
        }

        // Create new groups for roles that do not yet have corresponding groups.
        for (String newGroup : newGroups) {
            // Create a new sub-group under the main group.
            GroupRepresentation groupRepresentation = this.createSubGroup(mainGroup.getId(), newGroup);

            if (groupRepresentation == null) {
                throw new NullPointerException("Group " + newGroup + " could not be created");
            }

            // Retrieve the role representation for the new group.
            RoleRepresentation roleRepresentation = this.getClientRoleByName(newGroup);

            if (roleRepresentation == null) {
                throw new NullPointerException("The role " + newGroup + " does not present");
            }

            // Assign the role to the newly created group.
            this.assignRoleToGroup(groupRepresentation.getId(), roleRepresentation);
        }

        // Re-fetch the list of all groups after creation to ensure consistency.
        Map<String, String> finalGroupPaths = Utility.flatGroups(this.getRealmGroups(), true);

        // List to store the group IDs for each role.
        List<String> groupIds = new ArrayList<>();

        // Match the created roles to their group IDs based on paths.
        for (String role : roleConfig.getOperations()) {
            Map.Entry<String, String> g = Utility.getMatchByPath(finalGroupPaths, "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + role);

            if (g != null) {
                groupIds.add(g.getKey());
            }
        }

        // Deduplicate the group IDs to ensure no duplicates exist.
        groupIds = Utility.simpleDeduplicate(groupIds);

        // Ensure that the number of groups matches the number of roles.
        if (groupIds.size() != roleConfig.getOperations().size()) {
            throw new NullPointerException("Could not find all groups and roles");
        }

        // Cache the standard group IDs for future use.
        this.operationGroupIds = groupIds;
    }


    /**
     * Creates a new client role in the Keycloak server.
     * <p>
     * This method creates a role with the specified name and assigns it as a client role for the currently configured
     * Keycloak client. If the role is successfully created, it retrieves and returns the created role representation.
     * </p>
     *
     * <p>If an error occurs during the role creation process, this method logs the error and returns {@code null}.</p>
     *
     * @param roleName The name of the role to be created. This role will be associated with the configured Keycloak client.
     * @return {@link RoleRepresentation} The representation of the newly created role, or {@code null} if the role could not be created.
     */
    public RoleRepresentation createClientRole(String roleName) {
        try {
            // Create a new RoleRepresentation object to define the role attributes.
            RoleRepresentation roleRepresentation = new RoleRepresentation();

            // Set the name of the role.
            roleRepresentation.setName(roleName);

            // Mark the role as a client role. Client roles are specific to a client, rather than global realm roles.
            roleRepresentation.setClientRole(true);

            // Retrieve the RolesResource for the current client using the Keycloak client API.
            RolesResource rolesResource = this.getClientResource().roles();

            // Create the new role in the Keycloak server.
            rolesResource.create(roleRepresentation);

            // Retrieve and return the created role by its name to confirm its creation.
            return this.getClientRoleByName(roleName);
        } catch (Exception e) {
            // Log the exception message and return null if an error occurs.
            log.error(e.getMessage());
            return null;
        }
    }


    /**
     * Retrieves a list of all client role names for the currently configured Keycloak client.
     * <p>
     * This method interacts with the Keycloak Admin API to fetch all roles defined for the client specified
     * in the {@link JwtProperties}. It returns a list of the names of these roles. Each role is represented
     * using the {@link RoleRepresentation} class, and only the role names are extracted and returned.
     * </p>
     *
     * @return {@link List} of {@link String} containing the names of all roles associated with the configured client.
     * @throws Exception If there is an issue accessing the client resource or retrieving the roles.
     */
    public List<String> getClientRoles() throws Exception {
        // Retrieve the list of roles for the current client and convert it into a stream.
        return this.getClientResource().roles().list().stream()

                // Extract the name of each role using a method reference.
                .map(RoleRepresentation::getName)

                // Collect the role names into a List and return.
                .collect(Collectors.toList());
    }


    /**
     * Retrieves the representation of a client role by its name.
     * <p>
     * This method interacts with the Keycloak Admin API to fetch a role associated with the currently configured
     * Keycloak client using the provided role name. If the role is found, it returns a {@link RoleRepresentation}
     * object containing the details of the role. If an error occurs or the role is not found, it logs the error
     * and returns {@code null}.
     * </p>
     *
     * @param roleName The name of the role to be retrieved. This should match the exact name of an existing client role.
     * @return {@link RoleRepresentation} containing the details of the specified role, or {@code null} if the role is not found or an error occurs.
     */
    public RoleRepresentation getClientRoleByName(String roleName) {
        try {
            // Access the roles resource for the client and retrieve the role by name.
            // Convert the role to a RoleRepresentation object.
            return this.getClientResource().roles().get(roleName).toRepresentation();
        } catch (Exception e) {
            // Log the exception message if an error occurs during the role retrieval process.
            log.error(e.getMessage());
            return null;
        }
    }


    /**
     * Deletes a specified client role from the Keycloak server.
     * <p>
     * This method removes a client role identified by its {@link RoleRepresentation} object from the currently
     * configured Keycloak client. If the role is successfully deleted, it returns {@code true}. If an error
     * occurs during the deletion process, it logs the error and returns {@code false}.
     * </p>
     *
     * @param roleRepresentations The {@link RoleRepresentation} object representing the role to be deleted.
     *                            This should contain the exact role name to be removed.
     * @return {@code true} if the role is successfully deleted, or {@code false} if an error occurs.
     */
    public Boolean removeClientRole(RoleRepresentation roleRepresentations) {
        try {
            // Retrieve the roles resource for the current client.
            RolesResource rolesResource = this.getClientResource().roles();

            // Delete the role using its name from the RoleRepresentation object.
            rolesResource.deleteRole(roleRepresentations.getName());

            // Return true indicating successful deletion of the role.
            return true;
        } catch (Exception e) {
            // Log the exception message if an error occurs during the role deletion process.
            log.error(e.getMessage());

            // Return false indicating that the role could not be deleted.
            return false;
        }
    }


    /**
     * Updates the name of an existing group in the Keycloak server.
     * <p>
     * This method fetches a group by its ID, updates its name with the provided new name, and saves the updated
     * group information back to the Keycloak server. If the update is successful, the method returns {@code true}.
     * If an error occurs during the process, it logs the error and returns {@code false}.
     * </p>
     *
     * @param groupId The ID of the group that needs to be updated.
     * @param newName The new name to be assigned to the group.
     * @return {@code true} if the group name is successfully updated, or {@code false} if an error occurs.
     */
    public Boolean updateGroupName(String groupId, String newName) {
        try {
            // Retrieve the group representation by its ID from the Keycloak realm.
            GroupRepresentation groupRepresentation = this.getKeycloakRealm().groups().group(groupId).toRepresentation();

            // Set the new name for the group.
            groupRepresentation.setName(newName);

            // Retrieve the group resource again by ID and update the group with the new name.
            GroupResource groupResource = this.getKeycloakRealm().groups().group(groupRepresentation.getId());
            groupResource.update(groupRepresentation);

            // Return true indicating the group name was successfully updated.
            return true;
        } catch (Exception e) {
            // Log any exception that occurs during the update process.
            log.error(e.getMessage());

            // Return false indicating the group name could not be updated.
            return false;
        }
    }


    /**
     * Retrieves a list of all groups within the configured Keycloak realm.
     * <p>
     * This method uses the Keycloak Admin API to fetch all groups in the current realm. The API call allows for
     * filtering and pagination, but here it retrieves all groups without any filters and returns the entire list.
     * </p>
     *
     * <p><b>Note:</b> This method might need testing in future versions of Keycloak, as indicated by the issue
     * referenced in the TODO comment: <a href="https://github.com/keycloak/keycloak/issues/26227">Keycloak Issue #26227</a>.
     * Changes in the Keycloak API might impact how groups are fetched in future releases.</p>
     *
     * @return {@link List} of {@link GroupRepresentation} containing all groups in the current realm.
     */
    public List<GroupRepresentation> getRealmGroups() {
        // Retrieve all groups in the realm without any filters. The parameters used are:
        // - Search query: "" (empty string) to retrieve all groups.
        // - Exact match: false, to allow for a broader search.
        // - First: 0, to start from the first result.
        // - Max: Integer.MAX_VALUE, to retrieve all possible results without limiting.
        // - BriefRepresentation: false, to include detailed group information.
        return this.getKeycloakRealm().groups().groups("", false, 0, Integer.MAX_VALUE, false);
    }


    /**
     * Retrieves the main group representation from the Keycloak server.
     * <p>
     * This method searches through all available groups in the configured Keycloak realm
     * and looks for a group that matches the main group's path. The path is constructed
     * using the value from {@link JwtProperties#getDomainRoleGroupContextName()}, which
     * specifies the name of the main group. If a group with the specified path is found,
     * it returns the corresponding {@link GroupRepresentation}. If no group matches the
     * path, the method returns {@code null}.
     * </p>
     *
     * @return {@link GroupRepresentation} of the main group if found, or {@code null} if no such group exists.
     */
    public GroupRepresentation getMainGroup() {
        // Retrieve all groups from the Keycloak realm.
        List<GroupRepresentation> groups = this.getRealmGroups();

        // Construct the expected path for the main group using the configured context name.
        String path = "/" + jwtProperties.getDomainRoleGroupContextName();

        // Iterate through the list of groups to find the group that matches the expected path.
        for (GroupRepresentation group : groups) {
            if (group.getPath().equals(path)) {
                // Return the group representation if the path matches.
                return group;
            }
        }

        // Return null if no group with the specified path is found.
        return null;
    }


    /**
     * Retrieves a list of groups that the specified user belongs to.
     * <p>
     * This method fetches all groups associated with a user identified by their unique user ID. If the provided
     * {@code userId} is {@code null} or empty, it returns an empty list. If the user has no associated groups,
     * the method also returns an empty list.
     * </p>
     *
     * @param userId The unique identifier of the user for whom the groups are to be retrieved.
     * @return {@link List} of {@link GroupRepresentation} containing all groups the user is a member of,
     * or an empty list if the user ID is invalid or the user has no groups.
     */
    public List<GroupRepresentation> getGroupsByUserId(String userId) {
        // Check if the userId is null or empty, and return an empty list if true.
        if (userId == null || userId.isEmpty()) {
            return new ArrayList<>(); // Returns an empty list if the user ID is invalid.
        }

        // Retrieve the list of groups the user is a member of.
        List<GroupRepresentation> groupRepresentations = this.getKeycloakRealm().users().get(userId).groups();

        // If the user has no groups, return an empty list instead of null.
        if (groupRepresentations == null) {
            return new ArrayList<>();
        }

        // Return the list of group representations.
        return groupRepresentations;
    }


    /**
     * Creates a new subgroup under a specified parent group in the Keycloak server.
     * <p>
     * This method creates a new subgroup with the specified name under the parent group identified by its ID.
     * If the subgroup is successfully created, it returns the {@link GroupRepresentation} of the newly created group.
     * If an error occurs during the process, the method logs the error and returns {@code null}.
     * </p>
     *
     * @param parentGroupId The unique identifier of the parent group under which the new subgroup will be created.
     * @param groupName     The name of the new subgroup to be created.
     * @return {@link GroupRepresentation} of the newly created subgroup, or {@code null} if the creation fails.
     */
    public GroupRepresentation createSubGroup(String parentGroupId, String groupName) {
        // Create a new GroupRepresentation object to represent the new subgroup.
        GroupRepresentation newGroupRepresentation = new GroupRepresentation();
        newGroupRepresentation.setName(groupName); // Set the name of the new subgroup.

        try {
            // Use the Keycloak API to create the new subgroup under the specified parent group.
            Response response = this.getKeycloakRealm().groups().group(parentGroupId).subGroup(newGroupRepresentation);

            GroupRepresentation returnedGroupRepresentation = null;

            // Check if the response status indicates successful creation of the group.
            if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                // Read the created group representation from the response.
                returnedGroupRepresentation = response.readEntity(GroupRepresentation.class);
            }
            response.close(); // Close the response to release resources.

            // Return the created group representation, or null if the creation was not successful.
            return returnedGroupRepresentation;
        } catch (Exception e) {
            // Log the error if any exception occurs during the subgroup creation process.
            log.error(e.getMessage());
            return null;
        }
    }


    /**
     * Deletes a group from the Keycloak server using its unique identifier.
     * <p>
     * This method removes a group identified by its ID from the Keycloak realm. If the operation fails,
     * the method logs the error but does not throw an exception or return any status.
     * </p>
     *
     * @param groupId The unique identifier of the group to be deleted.
     */
    public void removeGroupById(String groupId) {
        try {
            // Use the Keycloak API to delete the group with the specified ID.
            this.getKeycloakRealm().groups().group(groupId).remove();
        } catch (Exception e) {
            // Log the exception message if an error occurs during group deletion.
            log.error(e.getMessage());
        }
    }


    /**
     * Assigns a specified client role to a group in the Keycloak server.
     * <p>
     * This method assigns the given {@link RoleRepresentation} to a group identified by its ID.
     * The role is assigned at the client level, making it specific to the current client in the realm.
     * If an error occurs during the assignment, the method catches and ignores the exception.
     * </p>
     *
     * @param groupId            The unique identifier of the group to which the role will be assigned.
     * @param roleRepresentation The {@link RoleRepresentation} object representing the role to be assigned.
     */
    public void assignRoleToGroup(String groupId, RoleRepresentation roleRepresentation) {
        try {
            // Assign the specified role to the group using the client-level roles API.
            this.getKeycloakRealm().groups().group(groupId).roles().clientLevel(this.clientUuid).add(List.of(roleRepresentation));
        } catch (Exception e) {
            // Catch and ignore any exceptions during role assignment.
        }
    }


    /**
     * Adds a user to a specified group in the Keycloak server.
     * <p>
     * This method adds a user identified by their unique ID to a group identified by its group ID.
     * If the operation is successful, the method returns {@code true}. If an error occurs, it logs
     * the error and returns {@code false}.
     * </p>
     *
     * @param groupId The unique identifier of the group that the user will join.
     * @param userId  The unique identifier of the user to be added to the group.
     * @return {@code true} if the user is successfully added to the group, or {@code false} if an error occurs.
     */
    public Boolean joinGroup(String groupId, String userId) {
        try {
            // Use the Keycloak API to add the user to the specified group.
            this.getKeycloakRealm().users().get(userId).joinGroup(groupId);
            return true; // Return true indicating the user was successfully added.
        } catch (Exception e) {
            // Log any exception that occurs during the group join process.
            log.error(e.getMessage());
            return false; // Return false indicating the operation failed.
        }
    }


    /**
     * Adds a user to multiple groups in the Keycloak server.
     * <p>
     * This method iterates over a list of group IDs and adds a user (identified by their ID) to each group.
     * If the user is successfully added to all groups, the method returns {@code true}. If the operation
     * fails for any group, it returns {@code false}.
     * </p>
     *
     * @param groupIds A list of group IDs representing the groups to which the user will be added.
     * @param userId   The unique identifier of the user to be added to the groups.
     * @return {@code true} if the user is successfully added to all groups, or {@code false} if any operation fails.
     */
    public Boolean joinGroups(List<String> groupIds, String userId) {
        for (String groupId : groupIds) {
            // Attempt to add the user to each group in the list.
            if (!this.joinGroup(groupId, userId)) {
                return false; // Return false if adding to any group fails.
            }
        }
        return true; // Return true if the user is added to all groups successfully.
    }


}

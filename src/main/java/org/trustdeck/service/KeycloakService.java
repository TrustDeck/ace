/*
 * Trust Deck Services
 * Copyright 2026 Armin Müller
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.trustdeck.configuration.JwtProperties;
import org.trustdeck.dto.UserDTO;
import org.trustdeck.exception.UnexpectedResultSizeException;
import org.trustdeck.utils.Assertion;

import lombok.extern.slf4j.Slf4j;

/**
 * This class encapsulates the communication with Keycloak.
 * 
 * @author Armin Müller
 */
@Slf4j
@Service
public class KeycloakService {

	/** The Keycloak client object. */
	private final Keycloak keycloakAdminClient;
	
	/** The properties object for the JWTs. */
	private final JwtProperties jwtProperties;
    
	/** The name of the realm where the admin client lives in. */
	private final String realmName;

	/**
	 * Constructor that is automatically called by Spring with the correct
	 * attributes from the application context.
	 * 
	 * @param keycloakAdminClient Spring-managed Keycloak admin client (singleton) created in
	 * {@link org.trustdeck.configuration.KeycloakAdminClientConfig} and used to call the 
	 * Keycloak Admin REST API
	 * @param jwtProperties JWT/Keycloak configuration provided by
	 * {@link org.trustdeck.configuration.JwtProperties}, e.g. realm 
	 * and server settings used by this service
	 */
    public KeycloakService(Keycloak keycloakAdminClient, JwtProperties jwtProperties) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.jwtProperties = jwtProperties;
        this.realmName = jwtProperties.getRealm();
    }

    /**
     * Convenience accessor for the configured realm.
     * 
     * @return the realm resource from the keycloak client
     */
    protected RealmResource realm() {
        return keycloakAdminClient.realm(realmName);
    }
	
	/**
     * Looks up the Keycloak subject/user id for a given username.
     *
     * @param username the Keycloak username
     * @return String of Keycloak subject/user id, {@code null} if nothing found
     * @throws UnexpectedResultSizeException if multiple exact matches exist
     */
    public String subjectIdByUsername(String username) {
        if (username == null || username.isBlank()) {
        	log.trace("No username given.");
            return null;
        }

        // Search candidates (can include partial matches), limit to 50 results
        List<UserRepresentation> candidates = realm().users().search(username, 0, 50);

        // Filter exact matches
        List<UserRepresentation> exact = candidates.stream()
                .filter(u -> u.getUsername() != null)
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .toList();

        // Check if any exact matches were found
        if (exact == null || exact.isEmpty()) {
        	log.debug("No exact matches for the given username (" + username + ") were found.");
            return null;
        }

        // Ensure that we found exactly one user
        if (exact.size() > 1) {
            log.warn("Multiple Keycloak users found for username = " + username + ": " + exact.stream().map(UserRepresentation::getId).toList());
            throw new UnexpectedResultSizeException(1, exact.size());
        }

        return exact.get(0).getId();
    }

    /**
     * Looks up the Keycloak subject/user id for a given email (often more user-friendly).
     *
     * @param username the Keycloak username
     * @return String of Keycloak subject/user id, {@code null} if nothing found
     * @throws UnexpectedResultSizeException if multiple exact matches exist
     */
    public String findSubjectIdByEmail(String email) {
        if (email == null || email.isBlank()) {
        	log.trace("No email given.");
            return null;
        }

        // Get a list of candidates, limit to 50 results
        List<UserRepresentation> candidates = realm().users().search(null, null, null, email, 0, 50);

        // Filter exact matches
        List<UserRepresentation> exact = candidates.stream()
                .filter(u -> u.getEmail() != null)
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .toList();

        // Check if any exact matches were found
        if (exact == null || exact.isEmpty()) {
        	log.debug("No exact matches for the given email (" + email + ") were found.");
            return null;
        }

        // Ensure that we found exactly one user
        if (exact.size() > 1) {
            log.warn("Multiple Keycloak users found for email = " + email + ": " + exact.stream().map(UserRepresentation::getId).toList());
            throw new UnexpectedResultSizeException(1, exact.size());
        }

        return exact.get(0).getId();
    }
    
    /**
     * Searches Keycloak users for the given query string and returns a list of UserDTOs.
     *
     * Note: Keycloak search behavior is not guaranteed to be an exact match.
     *
     * @param query the search term, e.g. username, name, email
     * @param maxResults maximum number of results to return (must be > 0)
     * @return a list of users found in Keycloak, or an empty list on error or when nothing was found
     */
    public List<UserDTO> searchUsers(String query, int maxResults) {
        if (!Assertion.isNotNullOrEmpty(query) || maxResults <= 0) {
        	log.debug("Query was empty or maxResults was <= 0.");
            return Collections.emptyList();
        }

        // Search in Keycloak for users, limit to maxResults
        List<UserRepresentation> userReps = keycloakAdminClient.realm(realmName).users().search(query, 0, maxResults, true);
        
        // Also search in the IDs
        userReps.addAll(keycloakAdminClient.realm(realmName).users().search("id:" + query, 0, maxResults, true));
        
        if (userReps == null || userReps.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch federation provider map
        Map<String, String> federationProviderMap = getFederationProviderMap();

        // Transform into DTOs and add user federation info
        List<UserDTO> users = new ArrayList<>(userReps.size());
        for (UserRepresentation u : userReps) {
            if (u == null) {
            	continue;
            }

            UserDTO user = new UserDTO();
            user.assignPojoValues(u);

            // Add federation provider info, if available
            String providerId = user.getFederationProviderId();
            if (providerId != null && federationProviderMap.containsKey(providerId)) {
                user.setFederationProviderName(federationProviderMap.get(providerId));
            }
            
            // Add Keycloak client roles
            user.setKeycloakRoles(getClientRolesForUser(user.getUserId()));

            users.add(user);
        }

        return users;
    }
    
    /**
     * Method to retrieve the client roles of a user from Keycloak.
     * 
     * @param userId the user's Keycloak ID
     * @return a list of the roles' names
     */
	public List<String> getClientRolesForUser(String userId) {
		if (!Assertion.isNotNullOrEmpty(userId)) {
			return Collections.emptyList();
		}

		// Note: this requires that your service account can read role mappings
		UserResource userResource = keycloakAdminClient.realm(realmName).users().get(userId);

		// Get the Keycloak internal, UUID-like ID from the given public clientId
		String clientUuid = keycloakAdminClient.realm(realmName).clients().findByClientId(jwtProperties.getClientId())
				.stream().findFirst().map(c -> c.getId()).orElse(null);

		if (clientUuid == null) {
			return Collections.emptyList();
		}

		// Retrieve the client roles for the given user for the trustdeck client
		List<RoleRepresentation> roles = userResource.roles().clientLevel(clientUuid).listAll();
		if (roles == null || roles.isEmpty()) {
			return Collections.emptyList();
		}

		// Return only the names of the roles as a list
		return roles.stream().map(RoleRepresentation::getName).filter(Assertion::isNotNullOrEmpty).distinct().sorted()
				.collect(Collectors.toList());
	}

	/**
     * Retrieves a map of all configured user storage providers 
     * (e.g. LDAP, Kerberos, etc.) aka federation providers from Keycloak.
     *
     * @return a map that associates the IDs of the federation providers with their names.
     */
    public Map<String, String> getFederationProviderMap() {
        List<ComponentRepresentation> federations = keycloakAdminClient.realm(realmName)
        		.components().query(null, "org.keycloak.storage.UserStorageProvider");

        if (federations == null || federations.isEmpty()) {
        	return Collections.emptyMap();
        }
        
        return federations.stream().collect(Collectors.toMap(ComponentRepresentation::getId, ComponentRepresentation::getName));
    } 
}

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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.trustdeck.ace.exception.UnexpectedResultSizeException;
import org.trustdeck.ace.security.authentication.configuration.JwtProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * This class encapsulates functionalities to work with OIDC information.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Slf4j
@Service
public class OidcService implements InitializingBean {

    @Autowired
    protected JwtProperties jwtProperties;

    @Getter
    private Keycloak keycloak;

    @Getter
    private String clientUuid;

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    protected void init() {
        this.keycloak = initKeycloak();
            
        try {
            // On startup: check if a connection to Keycloak can be established
            ClientResource clientResource = this.getClientResource();
            this.clientUuid = clientResource.toRepresentation().getId();
            
            log.debug("OIDC client resource available.");
        } catch (UnexpectedResultSizeException e) {
            log.error("OIDC resource not available.\n\t" + e + " Expected: " + e.getExpectedSize()
            + ". Actual: " + e.getActualSize() + ".\n");
        }
    }

    protected RealmResource getKeycloakRealm() {
        return this.getKeycloak().realm(jwtProperties.getRealm());
    }

    protected ClientResource getClientResource() throws UnexpectedResultSizeException {
        List<ClientRepresentation> clients = this.getKeycloakRealm().clients().findByClientId(jwtProperties.getClientId());
        
        if (clients.size() != 1) {
            throw new UnexpectedResultSizeException(1, clients.size());
        }

        String clientId = clients.get(0).getId();
        return this.getKeycloakRealm().clients().get(clientId);
    }

    private Keycloak initKeycloak() {
        return KeycloakBuilder.builder()
                .grantType(OAuth2Constants.PASSWORD)
                .realm(jwtProperties.getRealm())
                .clientId(jwtProperties.getClientId())
                .username(jwtProperties.getAdminUsername())
                .password(jwtProperties.getAdminPassword())
                .serverUrl(jwtProperties.getServerUri())
                .clientSecret(jwtProperties.getClientSecret())
                .build();
    }

    public List<String> flatGroupPaths(List<GroupRepresentation> groupRepresentations, Boolean recursive) {
        List<String> groupPaths = new ArrayList<>();
        
        if (groupRepresentations != null) {
            for (GroupRepresentation groupRepresentation : groupRepresentations) {
                groupPaths.add(groupRepresentation.getPath());
                
                // Recursively flatten sub-groups
                if (recursive && groupRepresentation.getSubGroups() != null && !groupRepresentation.getSubGroups().isEmpty()) {
                    List<String> subGroupPaths = this.flatGroupPaths(groupRepresentation.getSubGroups(), true);
                    groupPaths.addAll(subGroupPaths);
                }
            }
        }
        
        return groupPaths;
    }

    public List<GroupRepresentation> getGroupsByUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new ArrayList<>(); // why not return null?
        }
        
        List<GroupRepresentation> groupRepresentations = this.getKeycloakRealm().users().get(userId).groups();
        if (groupRepresentations == null) {
            return new ArrayList<>(); // why not return null?
        }
        
        return groupRepresentations;
    }
}

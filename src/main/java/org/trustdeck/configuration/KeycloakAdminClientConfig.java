/*
 * Trust Deck Services
 * Copyright 2026 Armin Müller and Eric Wündisch
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

package org.trustdeck.configuration;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This configuration class provides a reusable Keycloak admin client.
 * 
 * @author Armin Müller
 */
@Configuration
public class KeycloakAdminClientConfig {

	/**
	 * Creates a singleton Keycloak admin client used to call the Keycloak Admin REST API.
	 *
	 * Spring injects {@link JwtProperties} into this factory method automatically: the method parameter is
	 * resolved from the application context.
	 * The returned {@link org.keycloak.admin.client.Keycloak} instance is thread-safe to reuse across requests
	 * and will obtain/refresh access tokens using the configured client credentials.
	 *
	 * @param jwtProperties configuration values for Keycloak server URL, realm, client id, and client secret
	 * @return a reusable Keycloak admin client instance
	 */
    @Bean(destroyMethod = "close")
    public Keycloak keycloakAdminClient(JwtProperties jwtProperties) {
        return KeycloakBuilder.builder()
                .serverUrl(jwtProperties.getServerUri())
                .realm(jwtProperties.getRealm()) // The realm where the service-account client lives
                .clientId(jwtProperties.getClientId())
                .clientSecret(jwtProperties.getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }
}

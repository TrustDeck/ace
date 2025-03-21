/*
 * Trust Deck Services
 * Copyright 2023-2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.security.authentication.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The configuration for the JWT properties.
 * 
 * @author Eric Wündisch & Armin Müller
 */
@Data
@Configuration
@ConfigurationProperties
public class JwtProperties {

    /** The client ID of the keycloak resource. */
    @Value("${spring.security.oauth2.resourceserver.jwt.client-id:-}")
    private String clientId;

    /** The preferred name for the token, e.g., the username or the ID of the user. */
    @Value("${spring.security.oauth2.resourceserver.jwt.principal-attribute:-}")
    private String principalAttribute;

    /** The claim name to read all group paths. */
    @Value("${spring.security.oauth2.resourceserver.jwt.domain-role-group-claim-name:groups}")
    private String domainRoleGroupClaimName;

    /** The bucket/group to store all groups as relationship between domain and role. */
    @Value("${spring.security.oauth2.resourceserver.jwt.domain-role-group-context-name:Domain}")
    private String domainRoleGroupContextName;

    @Value("${spring.security.oauth2.resourceserver.jwt.admin-username:-}")
    private String adminUsername;

    @Value("${spring.security.oauth2.resourceserver.jwt.admin-password:-}")
    private String adminPassword;

    @Value("${spring.security.oauth2.resourceserver.jwt.client-secret:-}")
    private String clientSecret;

    @Value("${spring.security.oauth2.resourceserver.jwt.realm:-}")
    private String realm;

    @Value("${spring.security.oauth2.resourceserver.jwt.server-uri:-}")
    private String serverUri;

}

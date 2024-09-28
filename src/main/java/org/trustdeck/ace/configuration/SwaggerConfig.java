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

package org.trustdeck.ace.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.Scopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is used to define settings for swagger.
 *
 * @author Armin Müller & Eric Wündisch
 */
@Configuration

public class SwaggerConfig {

	@Value("${app.version}")
	private String appVersion;

	@Value("${spring.security.oauth2.resourceserver.jwt.realm:-}")
	private String realm;

	@Value("${spring.security.oauth2.resourceserver.jwt.server-uri:-}")
	private String serverUri;

    /**
     * This method sets basic information items that are shown in the API documentation.
     *
     * @return an openAPI object containing some meta information
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ACE Pseudonymization API")
                        .version(appVersion)
                        .description("The pseudonymization API of the Advanced Confidentiality Engine (ACE)"))
                .components(new Components().addSecuritySchemes("keycloak",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
                                .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                                        .password(new io.swagger.v3.oas.models.security.OAuthFlow()
                                                .authorizationUrl(serverUri+"realms/"+realm+"/protocol/openid-connect/auth")
                                                .tokenUrl(serverUri+"realms/"+realm+"/protocol/openid-connect/token")
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect scope")
                                                        .addString("profile", "Profile scope")
                                                        .addString("email", "Email scope"))))))
				;
    }
}
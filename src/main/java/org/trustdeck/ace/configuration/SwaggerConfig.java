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
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.Scopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // Define the x-logo extension
        Map<String, String> logoExtension = new HashMap<>();
        logoExtension.put("url", "https://ths-med.de/media/trustdeck.png");
        logoExtension.put("altText", "ACE Logo");

        // Define the x-tagGroups extension
        List<Map<String, Object>> tagGroups = new ArrayList<>();
        Map<String, Object> mainSection = new HashMap<>();
        mainSection.put("name", "API Interfaces");
        mainSection.put("tags", List.of("Domain", "Record"));
        tagGroups.add(mainSection);

        Info info = new Info()
                .title("ACE Pseudonymization API")
                .version(appVersion)
                .description("The pseudonymization API of the Advanced Confidentiality Engine (ACE)")
                .license(new License().name("Apache License 2.0").url("https://github.com/TrustDeck/ace/blob/main/LICENSE"));
        info.addExtension("x-logo", logoExtension);

        OpenAPI openAPI = new OpenAPI()
                .info(info);

        openAPI.addExtension("x-tagGroups", tagGroups);

        return openAPI.components(new Components()
                .addSecuritySchemes("ace",
                        new io.swagger.v3.oas.models.security.SecurityScheme()
                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
                                .description("OAuth 2.0 with an `id_token` that contains the `access_token`. After receiving the `id_token`, extract the `access_token` and include it as a Bearer token in the `Authorization` header of the request.")
                                .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                                        .password(new io.swagger.v3.oas.models.security.OAuthFlow()
                                                .authorizationUrl(serverUri + "/realms/" + realm + "/protocol/openid-connect/auth")
                                                .tokenUrl(serverUri + "/realms/" + realm + "/protocol/openid-connect/token")
                                                .scopes(new Scopes()
                                                        .addString("#domainName", "Name of domain")
                                                        .addString("domain-update-salt", "Updates the salt of a Domain")
                                                        .addString("domain-list-all", "Lists all Domain")
                                                        .addString("link-pseudonyms", "Needs to link records")
                                                        .addString("complete-view", "Needs to see all attributes")
                                                        .addString("domain-create-complete", "Creates Domain (Complete)")
                                                        .addString("record-update-complete", "Updates Domain (Complete)")
                                                        .addString("record-create-batch", "Creates records in batch")
                                                        .addString("record-read-batch", "Reads records in batch")
                                                        .addString("record-update-batch", "Updates records in batch")
                                                        .addString("record-delete-batch", "Delete records in batch")
                                                        .addString("record-create", "Creates record")
                                                        .addString("record-read", "Reads records")
                                                        .addString("record-update", "Updates records")
                                                        .addString("record-delete", "Deletes record")
                                                        .addString("domain-create", "Creates Domain")
                                                        .addString("domain-read", "Reads Domain")
                                                        .addString("domain-update", "Updates Domain")
                                                        .addString("domain-delete", "Deletes Domain")
                                                )))))
                ;
    }
}
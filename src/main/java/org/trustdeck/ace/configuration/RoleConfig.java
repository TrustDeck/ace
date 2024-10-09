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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;


/**
 * Configuration class that holds the operational roles for the application.
 * <p>
 * This class is used to map configuration properties defined under the `app` prefix in the application’s
 * configuration file (e.g., `application.yml` or `application.properties`). It contains a list of role names
 * that define the operations required by the application.
 * </p>
 *
 * <p>For example, a configuration in `application.yml` might look like:</p>
 * <pre>
 * app:
 *   operations:
 *     - domain-create
 *     - domain-create-complete
 *     - domain-delete
 * </pre>
 *
 * <p>The above configuration would populate the {@link RoleConfig#operations} field with a list of these operations.</p>
 *
 * <p>The {@code @Configuration} and {@code @ConfigurationProperties} annotations make this class a Spring-managed
 * configuration bean that will be automatically populated based on the properties defined in the external configuration file.</p>
 * <p>The {@code @Data} annotation from Lombok is used to automatically generate boilerplate code, such as getters and setters.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class RoleConfig {

    /**
     * List of operations defined for the application.
     * <p>
     * This field is populated based on the `app.operations` configuration in the external configuration file.
     * It typically represents the names of various operations (e.g., create, read, update, delete) that the application
     * will use to define roles and permissions in the Keycloak server.
     * </p>
     */
    private List<String> operations;
}
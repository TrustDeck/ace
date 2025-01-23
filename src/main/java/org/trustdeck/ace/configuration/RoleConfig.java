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

package org.trustdeck.ace.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration class that holds the operational roles for the application.
 *
 * This class is used to map configuration properties defined under the `app` prefix in the application’s
 * configuration file (e.g., `application.yml` or `application.properties`). It contains a list of role names
 * that define the operations required by the application.
 *
 * For example, a configuration in `application.yml` might look like:
 * <pre>
 * app:
 *   operations:
 *     - domain-create
 *     - domain-create-complete
 *     - domain-delete
 * </pre>
 *
 * @author Eric Wündisch & Armin Müller
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class RoleConfig {

    /**
     * List of operations defined for the application under `app.operations` in the yml-file.
     * It represents the names of various rights and roles (e.g., create, read, update, delete) that the application
     * will use to define roles and permissions in the Keycloak server.
     */
    private List<String> operations;
}
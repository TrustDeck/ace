/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Class for the configuration of the pseudonymization service's responses.
 * 
 * @author Armin Müller & Eric Wündisch
 */
@Data
@Configuration
@ConfigurationProperties
public class ResponseMediaTypeConfig {

	/** The preferred media type for the responses of the pseudonymization service. */
    @Value("${app.response.preferred-media-type:application/json}")
    private String preferredMediaType;

    /** The encoding used in the pseudonymization service. */
    @Value("${server.servlet.encoding.charset:UTF-8}")
    private String encodingCharset;
}
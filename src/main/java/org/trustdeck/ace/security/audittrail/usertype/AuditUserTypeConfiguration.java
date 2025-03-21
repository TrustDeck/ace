/*
 * Trust Deck Services
 * Copyright 2023 Armin Müller and contributors
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

package org.trustdeck.ace.security.audittrail.usertype;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the audit trail.
 * 
 * @author Armin Müller & Eric Wündisch
 */
@Data
@Configuration
@ConfigurationProperties
public class AuditUserTypeConfiguration {
	/** The name of the item in the token containing the relevant information. */
    @Value("${audittrail.oidc-group-mapper.token-claim-name:-}")
    private String tokenClaimName;
    
    /** The group name that defines the human user group. */
    @Value("${audittrail.oidc-group-mapper.group-names.isHuman:-}")
    private String humanUserGroupName;

    /** The group name that defines the technical user group. */
    @Value("${audittrail.oidc-group-mapper.group-names.isTechnical:-}")
    private String technicalUserGroupName;
    
    /** The group name that defines the no-auditing user group. */
    @Value("${audittrail.oidc-group-mapper.group-names.isNoAuditing:-}")
    private String noAuditingUserGroupName;
    
    /** The group name that defines the audit-everything user group. */
    @Value("${audittrail.oidc-group-mapper.group-names.isAuditEverything:-}")
    private String auditEverythingUserGroupName;
}
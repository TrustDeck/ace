/*
 * Trust Deck Services
 * Copyright 2022-2025 Armin Müller & Eric Wündisch
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

package org.trustdeck.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trustdeck.dto.ProjectDTO;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class offers a REST API for interacting with project entities.
 *
 * @author Armin Müller
 */
@RestController
@EnableMethodSecurity
@Slf4j
@RequestMapping(value = "/api/management")
public class ProjectRESTController {

	@PostMapping("/project")
    @PreAuthorize("hasRole('project-create')")
    @Audit(eventType = AuditEventType.CREATE, auditFor = AuditUserType.ALL)
    public ResponseEntity<?> createProject(@RequestBody ProjectDTO projectDTO,
                                           @RequestHeader(name = "accept", required = false) String responseContentType,
                                           HttpServletRequest request) {
		
		return null;
	}
}

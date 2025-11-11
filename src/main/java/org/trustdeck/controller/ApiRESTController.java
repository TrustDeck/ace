/*
 * Trust Deck Services
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

package org.trustdeck.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trustdeck.security.audittrail.annotation.Audit;
import org.trustdeck.security.audittrail.event.AuditEventType;
import org.trustdeck.security.audittrail.usertype.AuditUserType;

import lombok.extern.slf4j.Slf4j;

/**
 * Controller class to offer an interface to interact with basic API endpoints.
 * 
 * @author Armin Müller
 */
@Slf4j
@RestController
@RequestMapping(value = "/api")
public class ApiRESTController {

    /**
     * This method functions as a ping endpoint.
     *
     * @return <li>a <b>200-OK</b> status</li>
     */
    @GetMapping(value = "/ping")
    @Audit(eventType = AuditEventType.PING, auditFor = AuditUserType.HUMAN)
    public ResponseEntity<?> ping() {
        // The response to the ping is just a 200-OK status code
        log.trace("Ping.");
        return ResponseEntity.ok().build();
    }
}

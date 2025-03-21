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

package org.trustdeck.security.audittrail.event;

/**
 * The type of an audit event.
 * 
 * @author Armin Müller & Eric Wündisch
 */
public enum AuditEventType {

    /** Create audit event. */
    CREATE("CREATE", 1),
    
    /** Read audit event. */
    READ("READ", 2),
    
    /** Update audit event. */
    UPDATE("UPDATE", 3),
    
    /** Delete audit event. */
    DELETE("DELETE", 4),
    
    /** Ping audit event. */
    PING("PING", 5),
    
    /** Unknown audit event. */
    UNKNOWN("UNKNOWN", 0);

    /** The event label. */
    public final String label;
    
    /** The event id. */
    public final short id;

    /**
     * Basic constructor.
     * 
     * @param label the label for the event type.
     */
    AuditEventType(String label, int id) {
        this.label = label;
        this.id = (short) id;
    }
}

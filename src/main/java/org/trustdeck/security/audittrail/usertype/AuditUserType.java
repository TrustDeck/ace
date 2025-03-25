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

package org.trustdeck.security.audittrail.usertype;

/**
 * The user types for which auditing should be enabled/disabled.
 * 
 * @author Armin Müller
 */
public enum AuditUserType {
    
    /** Represents all user types. */
    ALL("ALL", 1),

    /** Human user type. */
    HUMAN("HUMAN", 2),
	
    /** Represents no user type. */
	NONE("NONE", 3),
    
    /** Technical user type. */
    TECHNICAL("TECHNICAL", 4),
    
    /** Unknown user type. */
    UNKNOWN("UNKNOWN", 0);

    /** The user type label. */
    public final String label;
    
    /** The user type id. */
    public final short id;

    /**
     * Basic constructor.
     * 
     * @param label the label of the user type.
     * @param id the id of the user type.
     */
    AuditUserType(String label, int id) {
        this.label = label;
        this.id = (short) id;
    }
}

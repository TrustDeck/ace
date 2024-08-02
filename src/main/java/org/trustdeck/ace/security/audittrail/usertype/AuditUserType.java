/*
 * ACE - Advanced Confidentiality Engine
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

/**
 * The user types for which auditing should be enabled/disabled.
 * 
 * @author Armin Müller
 */
public enum AuditUserType {
    
    /** Represents all user types. */
    ALL("ALL"),

    /** Human user type. */
    HUMAN("HUMAN"),
	
    /** Represents no user type. */
	NONE("NONE"),
    
    /** Technical user type. */
    TECHNICAL("TECHNICAL"),
    
    /** Unknown user type. */
    UNKNOWN("UNKNOWN");

    /** The user type label. */
    public final String label;

    /**
     * Basic constructor.
     * 
     * @param label the label of the user type.
     */
    AuditUserType(String label) {
        this.label = label;
    }
}

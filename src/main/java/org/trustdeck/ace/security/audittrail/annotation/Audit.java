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

package org.trustdeck.ace.security.audittrail.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.trustdeck.ace.security.audittrail.event.AuditEventType;
import org.trustdeck.ace.security.audittrail.usertype.AuditUserType;

/**
 * Annotation that enables an audit trail for the annotated method.
 *
 * @author Armin Müller & Eric Wündisch
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {

    /**
     * The type of the audit event (e.g. CREATE or DELETE).
     *
     * @return the audit event type
     */
    AuditEventType eventType() default AuditEventType.UNKNOWN;
    
    /**
     * The type of users for which the audit information should be recorded.
     * 
     * @return the audit user type
     */
    AuditUserType auditFor() default AuditUserType.ALL;

    /**
     * The message to log in the audit trail.
     *
     * @return the message
     */
    String message() default "";
}

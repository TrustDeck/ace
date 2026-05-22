/*
 * Trust Deck Services
 * Copyright 2022-2026 Armin Müller and Eric Wündisch
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

package org.trustdeck.security.audittrail.context;

import lombok.NoArgsConstructor;

/**
 * Class that stores the audit information object in the thread 
 * so it is available for further access.
 * 
 * @author Armin Müller
 */
@NoArgsConstructor
public final class AuditTriggerContext {

	/** The context object, which is thread-local. */
    private static final ThreadLocal<AuditInformationObject> CONTEXT = new ThreadLocal<>();

    /**
     * Method to insert the audit information object into the context.
     * 
     * @param aio the object containing information needed for auditing
     */
    public static void set(AuditInformationObject aio) {
        CONTEXT.set(aio);
    }

    /**
     * Getter for the thread-local audit object.
     * 
     * @return the audit information object stored in the thread context
     */
    public static AuditInformationObject get() {
        return CONTEXT.get();
    }

    /**
     * Method to clear the stored information object from the thread-local context.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}

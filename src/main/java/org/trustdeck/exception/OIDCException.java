/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller & Eric Wündisch
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

package org.trustdeck.exception;

/**
 * This class represents an exception when handling OIDC rights and roles.
 *
 * @author Armin Müller
 */
public class OIDCException extends RuntimeException {

    /** Exception UID. */
    private static final long serialVersionUID = 639556839881562730L;

    /**
     * Constructor that contains a standard message.
     * @param message an additional message for this exception (e.g. for further information)
     */
    public OIDCException(String message) {
        super("There was an error while handling OIDC rights and roles:\n" + message);
    }
}

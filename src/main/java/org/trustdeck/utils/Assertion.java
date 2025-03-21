/*
 * Trust Deck Services
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

package org.trustdeck.utils;

import org.springframework.stereotype.Component;

/**
 * This class encapsulates utility functionalities.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Component
public class Assertion {

    /**
     * Method that checks whether any one of the given objects is not null.
     *
     * @param assertObjects a list of objects that should be checked
     * @return {@code true} only if none of the passed objects are null, {@code false} otherwise.
     */
    public static Boolean assertNotNullAll(Object... assertObjects) {
        try {
            for (int i = 0; i < assertObjects.length; i++) {
                if (assertObjects[i] == null) {
                    return false;
                } else if (assertObjects[i] instanceof String && ((String) assertObjects[i]).isBlank()) {
                    return false;
                }
            }
        } catch (NullPointerException e) {
            return false;
        }

        return true;
    }

    /**
     * Method that checks whether all of the given objects are null.
     *
     * @param assertObjects a list of objects that should be checked
     * @return {@code true} only if all of the passed objects are null, {@code false} otherwise.
     */
    public static Boolean assertNullAll(Object... assertObjects) {
        for (int i = 0; i < assertObjects.length; i++) {
            if (assertObjects[i] != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method that checks whether the given String is <b>not</b> null nor empty.
     *
     * @param value the String to check
     * @return {@code true} only if the passed object is a string and neither null nor empty, {@code false} otherwise.
     */
    public static Boolean isNotNullOrEmpty(String value) {
        return value != null && !value.isBlank();
    }
}

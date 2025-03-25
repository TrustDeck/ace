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

package org.trustdeck.dto;

import org.springframework.context.annotation.Scope;

/**
 * Interface for a proper representation object.
 *
 * @author Armin Müller & Eric Wündisch
 */
@Scope("prototype") // Ensures that an instance is deleted after a request
public interface IObjectDTO<F, T> {
	
    /** Assigns values from the given POJO to the desired DTO. */
    T assignPojoValues(F pojo);

    /** Checks if only the fields for the standard view are filled. */
    Boolean isValidStandardView();

    /** Reduces an Object to its minimal view. */
    T toReducedStandardView();

    /** Represents an object as a string. */
    String toRepresentationString();

    /** Checks if mandatory fields are filled. */
    Boolean validate();
}

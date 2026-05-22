/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller
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

package org.trustdeck.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IdentifierItem object encapsulating the actual identifier and its type.
 * 
 * @author Armin Müller
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdentifierItem {
	
	/** The actual identifying information. */
	private String identifier;
	
	/** The type of the information stored. */
	private String idType;
	
	/**
     * Creates a readable string representation.
     * 
     * @return a string representation of the item's contents.
     */
    @JsonIgnore
    public String toRepresentationString() {
        String out = "";
        out += (this.getIdentifier() != null) ? "identifier: " + this.getIdentifier() + ", " : "";
        out += (this.getIdType() != null) ? "idType: " + this.getIdType() + ", " : "";

        return (out.endsWith(", ") ? out.substring(0, out.length() - 2) : out);
    }
    
    @JsonIgnore
    public Boolean validate() {
        // Unused
    	return null;
    }
    
    /**
     * Ensure that the item's values are null or empty.
     * 
     * @return {@code true} if any of the item's attributes are null or empty, {@code false} otherwise
     */
    @JsonIgnore
    public boolean isNullOrEmpty() {
    	if (this.getIdentifier() == null || this.getIdentifier().isBlank() || this.getIdType() == null || this.getIdType().isBlank()) {
            return true;
        }

        return false;
    }
    
    /**
     * Ensure that the item's values are not null nor empty.
     * 
     * @return {@code true} if none of the item's attributes are null or empty, {@code false} otherwise
     */
    @JsonIgnore
    public boolean isNotNullNorEmpty() {
    	if (this.getIdentifier() != null && !this.getIdentifier().isBlank() && this.getIdType() != null && !this.getIdType().isBlank()) {
            return true;
        }

        return false;
    }
}

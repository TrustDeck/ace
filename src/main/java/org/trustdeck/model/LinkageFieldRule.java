/*
 * Trust Deck Services
 * Copyright 2026 Armin Müller and Eric Wündisch
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

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class represents the effective record linkage rule for a single 
 * field from an EntityInstance. It contains the resolved linkage configuration 
 * that should be applied to one specific attribute path during token 
 * generation and candidate scoring.
 * 
 * @author Armin Müller
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkageFieldRule {

	/** The path of the field inside the entity instance's JSON structure. */
    private String path;

    /** The name of the field this rule belongs to. */
    private String name;

    /** The semantic tag used to classify the field for record linkage. */
    private String tag;

    /** The data type of the field this rule belongs to. */
    private String type;

    /** Determines whether or not this field should be used for record linkage. */
    private boolean linkage;

    /** The normalization steps that should be applied to the field's value. */
    private List<String> normalizers;

    /** The encoding steps that should be applied after normalization. */
    private List<String> encoders;

    /** The blocking strategies that should be used for candidate generation. */
    private List<String> blocking;

    /** The comparator that should be used during record linkage scoring. (Not yet used / implemented.) */
    private String comparator;

    /** The weight of this field during record linkage scoring. */
    private double weight;
}

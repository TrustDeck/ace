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

package org.trustdeck.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;

import org.springframework.context.annotation.Scope;
import org.trustdeck.utils.Assertion;

/**
 * DTO for updating a permission grant.
 *
 * Identifying information (OLD): subjectId + resourceType + resourceId + action
 * Updated data (NEW): decision, validity, resource mapping fields, etc.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Scope("prototype")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class PermissionUpdateDTO {

    /** The subject ID (from Keycloak) associated with the permission to update. */
    private String oldSubjectId;

    /** The type of the resource, e.g. Domain, Project. */
    private String oldResourceType;

    /** The internal database ID of the resource. */
    @JsonIgnore
    private Integer oldResourceId;

    /** The action that this permission represents on the resource, e.g. domain:read. */
    private String oldAction;

    // Optional: these are not part of the unique key, but can be useful for UI / debugging
    /** The domain this permission applies to (optional helper). */
    private String oldDomainName;

    /** The project this permission applies to (optional helper). */
    private String oldProjectName;

    /** New subjectId (only if changing ownership / migrating). */
    private String newSubjectId;

    /** New resourceType (rare; usually not changed). */
    private String newResourceType;

    /** New resourceId (rare; usually not changed). */
    @JsonIgnore
    private Integer newResourceId;

    /** New action (rare; usually not changed). */
    private String newAction;

    /** The new effective decision for the permission, e.g. ALLOW, DENY. */
    private String decision;

    /** The start date from which this permission is valid. */
    private OffsetDateTime validFrom;

    /** The end date up until this permission is valid. */
    private OffsetDateTime validTo;

    /** Optional: if you allow updating the display mapping as well. */
    private String domainName;

    /** Optional: if you allow updating the display mapping as well. */
    private String projectName;

    /** The user/account that performed the update (Keycloak subject). */
    private String updatedBy;

    /** If you set update timestamps in service layer, you can omit this. */
    private OffsetDateTime updatedAt;

	/**
	 * Checks if there is information to identify the record that should be updated.
	 * Unique key: oldSubjectId + oldResourceType + oldResourceId + oldAction.
	 * 
	 * @return true, iff we have enough data to uniquely identify the permission record.
	 */
	public boolean hasIdentifyingInformation() {
		return Assertion.isNotNullOrEmpty(this.getOldSubjectId(), this.getOldResourceType(), this.getOldAction())
				&& this.getOldResourceId() != null;
	}

	/**
	 * Checks whether any update fields are set at all. Useful to reject empty update payloads.
	 * 
	 * @return true, when not all the update fields are empty
	 */
	public boolean hasUpdateData() {
		return Assertion.isNotNullOrEmpty(this.getNewSubjectId(), this.getNewResourceType(), this.getNewAction(),
				this.getDecision(), this.getDomainName(), this.getProjectName(), this.getUpdatedBy())
				|| this.getNewResourceId() != null || this.getValidFrom() != null || this.getValidTo() != null
				|| this.getUpdatedAt() != null;
	}
}
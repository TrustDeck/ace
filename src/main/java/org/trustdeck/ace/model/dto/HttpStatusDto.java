/*
 * ACE - Advanced Confidentiality Engine
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

package org.trustdeck.ace.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;

/**
 * The HTTP status DTO.
 * 
 * @author Eric Wündisch & Armin Müller
 */
@Data
@NoArgsConstructor
@Scope("prototype") // Ensures that an instance is deleted after a request.
public class HttpStatusDto implements IRepresentation<HttpStatus, HttpStatusDto> {

    /** The HTTP status code number. */
    private int statusCode;

    /** The HTTP status message. */
    private String statusMessage;

    @JsonIgnore
    @Override
    public HttpStatusDto assignPojoValues(HttpStatus pojo) {
        this.setStatusMessage(pojo.getReasonPhrase());
        this.setStatusCode(pojo.value());
        return this;
    }

    @Override
    public Boolean isValidStandardView() {
        return true;
    }

    @Override
    public HttpStatusDto toReducedStandardView() {
        // Is already reduced
        return this;
    }

    @JsonIgnore
    @Override
    public String toRepresentationString() {
        return this.getStatusCode() + " " + this.getStatusMessage();
    }

    @Override
    public Boolean validate() {
        return true;
    }
}

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

package org.trustdeck.ace.exception;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.trustdeck.ace.service.ResponseService;

/**
 * Class for intercepting all exceptions triggered by the REST controllers.
 * (Extends to cover all different kinds of exceptions.)
 * 
 * @author Armin Müller & Eric Wündisch
 */
@ControllerAdvice(annotations = RestController.class)
public class ControllerAdviceException extends ResponseEntityExceptionHandler {

	/** Provides a response handler. */
    @Autowired
    ResponseService responseService;

    // Override the internal exception response to manipulate it based on the accept header.
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        // This is copied from the original method in the ResponseEntityExceptionHandler
        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(statusCode)) {
            request.setAttribute("jakarta.servlet.error.exception", ex, 0);
        }

        // Create the new body.
        Object overrideBody = responseService.createHttpStatusDtoFromRequest(HttpStatus.valueOf(statusCode.value()), request);

        // Create a valid value for the response's content type header.
        String contentTypeHeaderValue = responseService.buildContentTypeHeaderStringForResponse(this.responseService.getMediaTypeFromRequest(request));

        // Add the value to the response header.
        headers.setContentType(MediaType.parseMediaType(contentTypeHeaderValue));

        // Return as a simple response entity.
        return new ResponseEntity(overrideBody, headers, statusCode);
    }
}

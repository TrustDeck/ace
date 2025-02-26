/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2022-2025 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.security.authentication.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * A resource exception handler which re-throws an AccessDeniedException.
 * 
 * @author Eric Wündisch & Armin Müller
 */
@Slf4j
@ControllerAdvice
public class ResourceExceptionHandler {

    /**
     * Re-throw the exception from an access denied event from spring security. 
     * This is needed to receive the final exception in a spring context object.
     *
     * @param e the AccessDeniedException object
     * @return a ResponseEntity but this will never happen, since the exception is just re-thrown
     * @throws AccessDeniedException always thrown
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> accessDeniedException(AccessDeniedException e) throws AccessDeniedException {
        log.debug(e.toString());
        throw e;
    }
}
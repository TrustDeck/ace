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

package org.trustdeck.ace.security.authentication.handler;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Handles the output if the request contains an invalid token.
 * 
 * @author Eric Wündisch & Armin Müller
 */
@Slf4j
@NoArgsConstructor
public class CustomKeycloakAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /**
     * Triggers if the authentication with the given token failed.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @param authenticationException the AuthenticationException object
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException) {
        log.debug("Request with the given access token failed.");
        
        if (!response.isCommitted() ) {
	        // Set a 401-UNAUTHORIZED status.
	        response.setStatus(HttpStatus.UNAUTHORIZED.value());
	        
	        // Try to add an accompanying text.
            PrintWriter writer;
            try {
				writer = response.getWriter();
	            writer.print(HttpStatus.UNAUTHORIZED.name().toUpperCase());
			} catch (IOException e) {
				// Not really critical since a HTTP status was successfully set already.
				log.debug("Couldn't add a text to the 401-UNAUTHORIZED state: " + e.getMessage());
			}
        } else if (200 <= response.getStatus() && response.getStatus() < 300) {
            throw new RuntimeException("Success response was committed while authentication or verification failed!", authenticationException);
        }   
    }
}
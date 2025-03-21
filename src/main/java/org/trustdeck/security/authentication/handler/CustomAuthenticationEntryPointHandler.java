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

package org.trustdeck.security.authentication.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Used to control incoming requests without an access token.
 * 
 * @author Eric Wündisch & Armin Müller
 */
@Slf4j
public class CustomAuthenticationEntryPointHandler implements AuthenticationEntryPoint {

    /**
     * Listen for incoming request and resolve them if no access token is given.
     *
     * @param httpServletRequest the HttpServletRequest object
     * @param httpServletResponse the HttpServletResponse object
     * @param authenticationException the AuthenticationException object
     */
    @Override
    public void commence(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException authenticationException) {
        log.debug("Request without an access token encountered.");
        
        if (!httpServletResponse.isCommitted() ) {
	        // Set a 400-BAD_REQUEST status.
	        httpServletResponse.setStatus(HttpStatus.BAD_REQUEST.value());
	        
	        // Try to add an accompanying text.
            PrintWriter writer;
            try {
				writer = httpServletResponse.getWriter();
	            writer.print(HttpStatus.BAD_REQUEST.name().toUpperCase());
			} catch (IOException e) {
				// Not really critical since a HTTP status was successfully set already.
				log.debug("Couldn't add a text to the 400-BAD_REQUEST state: " + e.getMessage());
			}
        }
    }
}

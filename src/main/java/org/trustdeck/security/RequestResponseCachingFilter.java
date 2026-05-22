/*
 * Trust Deck Services
 * Copyright 2023-2026 Armin Müller and Eric Wündisch
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

package org.trustdeck.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.trustdeck.utils.Utility;

import lombok.NoArgsConstructor;

/**
 * Filter that wraps HTTP requests and responses so their bodies can be read
 * multiple times later in the request lifecycle.
 *
 * For multipart requests, the request is intentionally not wrapped because
 * multipart handling and eager request-body consumption are incompatible.
 *
 * @author Armin Müller
 */
@NoArgsConstructor
public class RequestResponseCachingFilter extends OncePerRequestFilter {
	/* NOTE: This filter needs to be added to the Keycloak configuration in the SecurityConfig class. */
	
	/**
     * Applies request/response wrappers where safe and necessary.
     *
     * @param request the incoming HTTP request
     * @param response the outgoing HTTP response
     * @param filterChain the filter chain
     * @throws ServletException when filter processing fails
     * @throws IOException when I/O fails
     */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		// If the content is a (multipart) file, don't wrap the request as this consumes the 
        // file sent with the request, because for multipart handling, getParts(), which is 
        // called later by Spring, and getInputStream(), which is called in the 
        // CachedBodyHttpServletRequest-constructor, are mutually exclusive
        HttpServletRequest wrappedRequest = !Utility.isMultipartRequest(request) ? new CachedBodyHttpServletRequest(request) : request;

        // Must be wrapped, otherwise we would consume the response objects which would lead to their disappearance
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        
        try {
	        // Passes the actual request down the filter chain
	        filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
        	// If you read the response body once, it is consumed (since it is internally a stream) and it disappears 
        	// from the response. This is fixed by copying the body back if a body exists.
	        wrappedResponse.copyBodyToResponse();
        }
	}
}

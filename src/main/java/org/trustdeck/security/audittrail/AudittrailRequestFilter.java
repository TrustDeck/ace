/*
 * Trust Deck Services
 * Copyright 2023 Armin Müller and contributors
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

package org.trustdeck.security.audittrail;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.trustdeck.security.audittrail.request.CachedBodyHttpServletRequest;

import lombok.NoArgsConstructor;

/**
 * The audittrail request filter that injects a request object that 
 * enables non-consuming readability of the request-body.
 * 
 * @author Armin Müller
 */
@NoArgsConstructor
public class AudittrailRequestFilter extends OncePerRequestFilter {
	/* NOTE: This filter needs to be added to the Keycloak configuration in the KeycloakSecurityConfig class. */
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

		// Must be wrapped. Otherwise we would consume the request/response objects which would lead to their disappearance.
        CachedBodyHttpServletRequest req = new CachedBodyHttpServletRequest(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        
        // Performs the actual request
        filterChain.doFilter(req, res);

        // This is important !!!
        // If you read the response body once, it is consumed (since it is internally a stream) and it disappears from the response.
        // You can easily fix this by simply copying the body back if a body exists.
        res.copyBodyToResponse();
	}
}

/*
 * ACE - Advanced Confidentiality Engine
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

package org.trustdeck.ace.security.audittrail.annotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * This interceptor is used to pass some audit information through
 * to the request-object to make it available for the filters where
 * the actual logging is triggered.
 * 
 * @author Armin Müller & Eric Wündisch
 */
@Component
public class AudittrailAnnotationInterceptor implements HandlerInterceptor {

	/** The identifying attribute name under which the audit annotation is stored in the request object. */
    public static final String AUDIT_ANNOTATION_IDENTIFIER = AudittrailAnnotationInterceptor.class.getName() + ".AUDITTRAIL_ANNOTATION_OBJECT";
    
    /** The identifying attribute name under which the keycloak authentication token is stored in the request object. */
    public static final String AUDIT_KEYCLOAK_AUTHENTICATION_TOKEN = AudittrailAnnotationInterceptor.class.getName() + ".KEYCLOAK_AUTHENTICATION_TOKEN";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    	// Since the audit annotation is only available via handlers and these are not available in the database access 
    	// services (where we extract the information from the request), we pass the annotation through to the 
    	// request-object.

    	// Check if the handler that called this method is a HandlerMethod. If so, it contains the annotation we need.
        if (handler instanceof HandlerMethod) {
            // Retrieve the annotation
            Audit auditAnnotation = ((HandlerMethod) handler).getMethodAnnotation(Audit.class);
            
            if (auditAnnotation != null) {
            	// Pass the annotation through to the request-object
            	request.setAttribute(AUDIT_ANNOTATION_IDENTIFIER, auditAnnotation);
            }
        }
        
        // Retrieve the Keycloak token
        JwtAuthenticationToken token = (JwtAuthenticationToken) request.getUserPrincipal();
    	
    	if (token != null) {
    		// Pass the authentication token through to the request-object
    		request.setAttribute(AUDIT_KEYCLOAK_AUTHENTICATION_TOKEN, token);
    	}
    	
    	return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // Nothing to do
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) throws Exception {
        // Nothing to do
    }
}
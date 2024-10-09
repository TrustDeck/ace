/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.trustdeck.ace.security.authentication.configuration.JwtProperties;
import org.trustdeck.ace.utils.Assertion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class encapsulates utility functionalities to check roles and relationships within a given OIDC token.
 *
 * @author Eric Wündisch & Armin Müller
 */
@Component("auth")
public class AuthorizationService {

    /** JWT properties. */
    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    CachingService cachingService;

    /**
     * Returns the authentication object from the SecurityContextHolder as a shortcut.
     *
     * @return the Authentication Object
     */
    private static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Returns the roles assigned to the OIDC token as a set of strings 
     * when no authentication object is provided by the user.
     *
     * @return the roles as a set of strings
     */
    public static Set<String> getRolesFromAuthentication() {
        Authentication authentication = getAuthentication();

        return (authentication == null) ? Collections.emptySet() : getRolesFromAuthentication(authentication);
    }

    /**
     * Returns the roles assigned to the OIDC token as a set of strings.
     *
     * @param authentication the Authentication object
     * @return the roles as a set of strings
     */
    public static Set<String> getRolesFromAuthentication(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .flatMap(x -> Stream.of(x.getAuthority()))
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the list of group paths assigned to the current user from the cache.
     *
     * <p>This method obtains the authentication context, extracts the JWT token, and
     * uses the subject (typically the user's ID) to retrieve cached group paths
     * from the caching service.</p>
     *
     * @return a list of group paths associated with the current user, or an empty list if
     *         the authentication is not available or the user is not authenticated.
     */
    public List<String> getGroupPathsCachedFromContext(){
        Authentication authentication = getAuthentication();
        
        if (authentication == null) {
        	return new ArrayList<>();
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();

        return cachingService.getGroupPaths(jwt.getSubject());
    }

    /**
     * Method that checks if a token contains the given role names.
     * @apiNote Currently unused. Nonetheless, do not remove.
     *
     * @param roles the role names that should be searched for
     * @return {@code true} only if <b>all</b> given roles are contained in the token, {@code false} if at least one role is missing
     */
    public boolean currentRequestHasRoles(String... roles) {
        // Extract a list of all roles from the authentication token
    	Set<String> grantedRoles = getRolesFromAuthentication();
        
    	// Iterate over the required roles and try to find them in the list of granted roles
        for (String role : roles) {
            if (!grantedRoles.contains("ROLE_" + role)) {
                // A role is missing
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the request has the role given from within the security context object.
     *
     * @param role the role as a string
     * @return {@code true} if the role is present, {@code false} if not
     */
    public boolean currentRequestHasRole(String role) {
        return getRolesFromAuthentication().contains("ROLE_" + role);
    }

    /**
     * Method that checks whether the user has the specified domain and role 
     * as a role in the OIDC token and a relationship between them.
     * For security reasons, this method is only used for new requests and 
     * always contains the token of the new request.
     *
     * @param root method-level security control context object that includes information about the authenticated user
     * @param domain the domain name as a string
     * @param role the role as a string
     * @return {@code true} only if the given role and domain have a relationship, {@code false} if not
     */
    public boolean hasDomainRoleRelationship(MethodSecurityExpressionOperations root, String domain, String role) {
        List<String> groupPaths = null;
        Authentication authentication = root.getAuthentication();
        
        if (authentication == null) {
        	return false;
        }
        
        try {
	        Jwt jwt = (Jwt) authentication.getPrincipal();
            if(jwt == null){
                return false;
            }
	        groupPaths = cachingService.getGroupPaths(jwt.getSubject());
        } catch (ClassCastException e) {
         	return false;
        }

        if (!Assertion.isNotNullOrEmpty(domain) || !Assertion.isNotNullOrEmpty(role) || groupPaths == null || groupPaths.isEmpty()) {
            // Domain name or role were not provided
        	return false;
        }

        return hasAssignedGroupPaths(groupPaths, domain, role);
    }

    /**
     * Method that checks whether the user has the specified domain and role 
     * as a role in the OIDC token and a relationship between them
     * when no method-level security control context object is given by the user.
     *
     * @param domain the domain name as a string
     * @param role the role as a string
     * @return {@code true} only if given role and domain have a relationship, {@code false} if not
     */
    public boolean hasDomainRoleRelationship(String domain, String role) {
        List<String> groupPaths = this.getGroupPathsCachedFromContext();

        if (!Assertion.isNotNullOrEmpty(domain) || !Assertion.isNotNullOrEmpty(role) || groupPaths == null || groupPaths.isEmpty()) {
            // Domain name or role were not provided
            return false;
        }
        
        return hasAssignedGroupPaths(groupPaths, domain, role);
    }

    /**
     * Helper method to have the check in one place.
     *
     * @param groupPaths the domains as a list
     * @param domain the given domain's name as a string
     * @param role the given role as a string
     * @return {@code true} only if the given role and domain have a relationship and are set as role, {@code false} if not
     */
    private Boolean hasAssignedGroupPaths(List<String> groupPaths, String domain, String role){
        String path = "/" + jwtProperties.getDomainRoleGroupContextName() + "/" + role + "/" + domain;
        return groupPaths.contains(path);
    }
}

/*
 * Trust Deck Services
 * Copyright 2023-2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.security.authentication.configuration;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is used to convert a JWT token into readable information.
 * It extracts role names and the preferred username.
 * 
 * @author Eric Wündisch & Armin Müller
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	/** An instance of the converter. */
    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    
    /** The properties object. */
    private JwtProperties jwtProperties;

    /**
     * Instantiates a new converter with the given properties.
     *
     * @param jwtProperties the JWT properties
     */
    public JwtAuthConverter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Method to convert from JSON Web Token (JWT) to an AbstractAuthenticationToken.
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                jwtGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractResourceRoles(jwt).stream()).collect(Collectors.toSet());
        
        return new JwtAuthenticationToken(jwt, authorities, getPrincipalClaimName(jwt));
    }

    /**
     * Custom method to extract the preferred username.
     * If nothing is found, the default user id is returned.
     *
     * @param jwt the raw JWT token
     * @return the user token's name as a string
     */
    private String getPrincipalClaimName(Jwt jwt) {
        String claimName = JwtClaimNames.SUB;
        
        if (this.jwtProperties.getPrincipalAttribute() != null) {
            claimName = this.jwtProperties.getPrincipalAttribute();
        }
        String name = jwt.getClaim(claimName);

        return name == null ? jwt.getClaim(JwtClaimNames.SUB) : name;
    }

    /**
     * Extracts roles from the given JWT token.
     *
     * @param jwt the raw JWT token
     * @return a list of granted roles
     */
    @SuppressWarnings("unchecked")
	private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        Map<String, Object> resource;
        Collection<String> resourceRoles;
        
        if (resourceAccess == null
                || (resource = (Map<String, Object>) resourceAccess.get(this.jwtProperties.getClientId())) == null
                || (resourceRoles = (Collection<String>) resource.get("roles")) == null) {
            return Set.of();
        }
        
        return resourceRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }
}
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

package org.trustdeck.ace.security.audittrail.event;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.trustdeck.ace.jooq.generated.tables.pojos.Auditevent;
import org.trustdeck.ace.security.audittrail.annotation.Audit;
import org.trustdeck.ace.security.audittrail.annotation.AudittrailAnnotationInterceptor;
import org.trustdeck.ace.security.audittrail.usertype.AuditUserTypeConfiguration;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

/**
 * A class encapsulating the functionality needed to create jOOQ AuditEvent POJOs.
 *
 * @author Armin Müller
 */
@Service
@NoArgsConstructor
@Slf4j
public class AuditEventBuilder {

    /** The configuration for the user type naming. */
    @Autowired
    private AuditUserTypeConfiguration auditUserTypeConfig;

    /**
     * Builds an Auditevent object which can then be inserted into the database.
     *
     * @param request the request object containing necessary information
     * @return an {@code Auditevent} object containing all necessary information and that can be written into the database
     */
    public Auditevent build(HttpServletRequest request) {
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);

        // Retrieve audit annotation from request
        Audit auditAnnotation = (Audit) request.getAttribute(AudittrailAnnotationInterceptor.AUDIT_ANNOTATION_IDENTIFIER);

        // Retrieve keycloak information
        JwtAuthenticationToken token = (JwtAuthenticationToken) req.getAttribute(AudittrailAnnotationInterceptor.AUDIT_KEYCLOAK_AUTHENTICATION_TOKEN);

        // Decide if auditing is even required
        if (!isAuditingRequired(auditAnnotation, token)) {
            return null;
        }

        // Extract request body
        String body = null;
        try {
            body = readInputStreamInStringFormat(request.getInputStream(), Charset.forName(request.getCharacterEncoding()));
        } catch (IllegalStateException | IOException e) {
            // Nothing to do.
        }
        body = (body == null) ? "" : body;

        // Build Auditevent POJO
        Auditevent auditEvent = new Auditevent();
        auditEvent.setRequesttime(LocalDateTime.now());
        auditEvent.setUsername((token != null) ? token.getName() : "UNKNOWN");
        auditEvent.setRequesterip(getRequesterIP(req));
        auditEvent.setRequesturl("[" + req.getMethod() + "] " + getRequestURL(req));
        auditEvent.setRequestbody(body.isEmpty() ? null : body);

        return auditEvent;
    }

    /**
     * Method to read the contents of the request body which is an InputStream.
     *
     * @param stream  the stream object containing the request body
     * @param charset the charset of the input stream; needed to decode the contents
     * @return the stream content as a String
     * @apiNote This method tries to "unread" the Stream but will destructively
     * read the Stream if "unreading" isn't supported (i.e. mark() and reset()
     * isn't supported).
     */
    private String readInputStreamInStringFormat(InputStream stream, Charset charset) {
        final int MAX_BODY_SIZE = 1024;
        final StringBuilder bodyStringBuilder = new StringBuilder();

        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }

        stream.mark(MAX_BODY_SIZE + 1);
        final byte[] entity = new byte[MAX_BODY_SIZE + 1];
        int bytesRead = -1;
        try {
            bytesRead = stream.read(entity);
        } catch (IOException e) {
            return null;
        }

        if (bytesRead != -1) {
            bodyStringBuilder.append(new String(entity, 0, Math.min(bytesRead, MAX_BODY_SIZE), charset));
            if (bytesRead > MAX_BODY_SIZE) {
                bodyStringBuilder.append("...");
            }
        }

        try {
            stream.reset();
        } catch (IOException e) {
            log.error("Un-reading the stream of the request was unsuccessful. The stream might be unrecoverably consumed.\n\t" + e.getMessage());
            return null;
        }

        return bodyStringBuilder.toString();
    }

    /**
     * Returns the requester's IP address from the request.
     *
     * @param request the request object containing the IP
     * @return the client IP address as a String
     */
    private String getRequesterIP(ContentCachingRequestWrapper request) {
        String[] headerCandidates = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR", "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"};

        // Iterate over possible headers that could contain the requester's IP. Return the first one found.
        for (String header : headerCandidates) {
            String headerText = request.getHeader(header);
            if (headerText != null && headerText.length() != 0 && !headerText.equalsIgnoreCase("unknown")) {
                return headerText.substring(0, headerText.indexOf(",") == -1 ? headerText.length() : headerText.indexOf(","));
            }
        }

        // No header with the IP information was found.
        String fallbackIP = request.getRemoteAddr();
        return fallbackIP.equals("0:0:0:0:0:0:0:1") ? "127.0.0.1" : fallbackIP;
    }

    /**
     * Returns the request URL including the query parameters without the base URL.
     * Example: /sub/site/a?id=1111&foo=bar
     *
     * @param request the request object containing the the request URL
     * @return the request URL as a string
     */
    private String getRequestURL(ContentCachingRequestWrapper request) {
        StringBuilder parameter = new StringBuilder();

        // Encode query parameter into query string if there are any
        if (!request.getParameterMap().isEmpty()) {
            parameter.append("?");

            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                parameter.append(entry.getKey()).append("=").append(String.join(",", entry.getValue())).append("&");
            }
        }

        String url = request.getRequestURI() + parameter.toString();

        // Removes the last '&' if necessary and remove base-URL
        url = url.endsWith("&") ? url.substring(0, url.length() - 1) : url;

        // Remove common sub-sites and return
        return url.replace("/api/pseudonymization", "");
    }

    /**
     * Returns a list of groups the user-account is assigned to.
     *
     * @param token the JWT access token of the requester
     * @return the list of group names
     */
    @SuppressWarnings("unchecked")
    private ArrayList<String> getGroupNamesFromToken(JwtAuthenticationToken token) {
        ArrayList<String> groups = new ArrayList<>();

        if (token != null && token.getToken().hasClaim(auditUserTypeConfig.getTokenClaimName())) {
            ArrayList<String> otherClaims = (ArrayList<String>) token.getToken().getClaim(auditUserTypeConfig.getTokenClaimName());
            
            return otherClaims == null ? groups : otherClaims;
        }

        // If the token is null, return an empty list
        return groups;
    }

    /**
     * Helper method to decide if auditing should be performed for the current user.
     * (Depends on the group the user is in.)
     *
     * @param auditAnnotation the audit annotation containing the information for which user type the requests should be audited
     * @param token the access token containing the requester's user group name
     * @return {@code true} when the request for the current user and endpoint should be audited, {@code false} otherwise.
     * @apiNote User-assigned groups (no-auditing, audit-everything) have priority over method-annotated auditing targets
     * (e.g., requests from a user with an assigned no-auditing group will not be audited even when the method's
     * AuditAnnotation targets all requests).
     */
    private boolean isAuditingRequired(Audit auditAnnotation, JwtAuthenticationToken token) {
        ArrayList<String> groupNames = getGroupNamesFromToken(token);

        // Check if the user is assigned to the no-auditing or audit-everything group
        if (groupNames.contains("/" + auditUserTypeConfig.getNoAuditingUserGroupName())) {
            return false;
        } else if (groupNames.contains("/" + auditUserTypeConfig.getAuditEverythingUserGroupName())) {
            return true;
        }

        // Check if the auditing group assigned to the user and the target group in the Audit annotation match
        switch (auditAnnotation.auditFor()) {
            case HUMAN:
                if (groupNames.contains("/" + auditUserTypeConfig.getHumanUserGroupName())) {
                    return true;
                } else {
                    return false;
                }
            case TECHNICAL:
                if (groupNames.contains("/" + auditUserTypeConfig.getTechnicalUserGroupName())) {
                    return true;
                } else {
                    return false;
                }
            case NONE:
                return false;
            case ALL:
            case UNKNOWN:
            default:
                return true;
        }
    }
}

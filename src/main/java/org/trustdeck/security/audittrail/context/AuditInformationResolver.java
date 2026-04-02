/*
 * Trust Deck Services
 * Copyright 2026 Armin Müller and Eric Wündisch
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

package org.trustdeck.security.audittrail.context;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.trustdeck.configuration.AuditUserTypeConfiguration;
import org.trustdeck.dto.KafkaPseudonymMessageDTO;
import org.trustdeck.kafka.KafkaPseudonymMessageParser;
import org.trustdeck.security.audittrail.usertype.AuditUserType;
import org.trustdeck.utils.Assertion;
import org.trustdeck.utils.Utility;

import com.fasterxml.jackson.core.JsonProcessingException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the currently active audit source from the intercepted method context.
 *
 * @author Armin Müller
 */
@Component
@Slf4j
public class AuditInformationResolver {

	/** The configuration for audit user types. */
    @Autowired
    private AuditUserTypeConfiguration auditUserTypeConfig;

	/** Gives access to the message parser so we can transform a Kafka message into a processable DTO. */
	@Autowired
	private KafkaPseudonymMessageParser kafkaParser;
    
    /**
     * Resolves the source of the audit trigger from the current invocation context.
     *
     * @param joinPoint the intercepted method invocation point
     * @return the resolved audit information object or {@code null}
     */
    public AuditInformationObject resolve(ProceedingJoinPoint joinPoint) {
        AuditInformationObject restSource = resolveFromCurrentRESTRequest();
        if (restSource != null) {
        	log.trace("Auditing triggered by a REST request.");
            return restSource;
        }

        AuditInformationObject kafkaSource = resolveFromKafkaArguments(joinPoint.getArgs());
        if (kafkaSource != null) {
        	log.trace("Auditing triggered by a Kafka message.");
            return kafkaSource;
        }

    	log.trace("No auditing source found.");
        return null;
    }

    /**
     * Resolves the source of the audit trigger from the current REST request.
     *
     * @return the resolved audit information object or {@code null}
     */
    private AuditInformationObject resolveFromCurrentRESTRequest() {
    	// Retrieve the current request
        HttpServletRequest request = Utility.getCurrentRequest();
        if (request == null) {
        	// No request found
            return null;
        }
        
        // Wrap request, so we can safely perform the destructive read on the request's input stream
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);

        // Extract user information from the request
        JwtAuthenticationToken token = getJwtAuthenticationToken();
        String username = (token != null && Assertion.isNotNullOrEmpty(token.getName())) ? token.getName() : "UNKNOWN";

        // Fill body, but only when it's not a file (i.e. when it's not a multipart request
        String requestBody = null;
        if (Utility.isMultipartRequest(request)) {
            requestBody = null;
        } else {
            try {
	            Charset charset = request.getCharacterEncoding() != null ? Charset.forName(request.getCharacterEncoding()) : Charset.defaultCharset();
	            requestBody = readInputStreamInStringFormat(request.getInputStream(), charset);
	        } catch (IllegalStateException | IOException e) {
	            // Nothing to do.
	        }
        }

        // Build and return audit information object
        return AuditInformationObject.builder()
        		.auditSourceSystem(AuditSourceSystem.REST)
                .username(username)
                .userType(resolveUserType(getGroupNamesFromToken(token)))
                .requesterIp(getRequesterIP(req))
                .target("[" + req.getMethod() + "] " + getRequestURL(req))
                .payload(normalizePayload(requestBody))
                .build();
    }

    /**
     * Resolves the source of the audit trigger from the current Kafka-related method arguments.
     * Supports ConsumerRecord as well as Spring Message payload wrappers.
     *
     * @param args the intercepted method arguments
     * @return the resolved audit information object or {@code null}
     */
    private AuditInformationObject resolveFromKafkaArguments(Object[] args) {
        // Check that we actually have arguments
    	if (args == null || args.length == 0) {
            // No arguments found
    		return null;
        }

    	// Check if any of the arguments can be used to create the audit information object
        for (Object arg : args) {
            // Check if the argument is a consumer record
        	if (arg instanceof ConsumerRecord consumerRecord) {
                return buildFromConsumerRecord(consumerRecord);
            }

        	// Check if the argument is a consumer record wrapped in a message
            if (arg instanceof Message<?> message) {
                if (message.getPayload() instanceof ConsumerRecord consumerRecord) {
                    return buildFromConsumerRecord(consumerRecord);
                }
            }
        }

        // No usable arguments were found
        return null;
    }

    /**
     * Builds the audit information object from a Kafka ConsumerRecord.
     *
     * @param record the Kafka ConsumerRecord
     * @return the audit information object
     */
    private AuditInformationObject buildFromConsumerRecord(ConsumerRecord<?, ?> record) {
    	// Parse payload (the value() in the record object) into DTO, so we can access the username in the message
		KafkaPseudonymMessageDTO kafkaDto = null;
		String payload = null;
		try {
			payload = (String) record.value();
			kafkaDto = kafkaParser.parse(payload);
		} catch (ClassCastException | JsonProcessingException e) {
			log.warn("Parsing the Kafka message failed, so it was skipped.", e);

		}
    	
    	// Retrieve username
    	String username = kafkaDto != null ? kafkaDto.getUsername() : null;
    	if (Assertion.isNullOrEmpty(username)) {
    		// Username was not found in the Kafka message, maybe it's in the header (if not, fall back to "KAFKA")
    		username = readKafkaHeader(record, "username", "KAFKA");
    	}

    	// Build string that contains the message target
        String target = "[KAFKA] topic=" + record.topic() + ", partition=" + record.partition() + ", offset=" + record.offset();

        // Build and return the information object
        return AuditInformationObject.builder()
        		.auditSourceSystem(AuditSourceSystem.KAFKA)
                .username(username)
                .userType(AuditUserType.TECHNICAL)
                .requesterIp("KAFKA")
                .target(target)
                .payload(normalizePayload(payload))
                .topic(record.topic())
                .partition(record.partition())
                .recordOffset(record.offset())
                .build();
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

        // Removes the last '&' if necessary
        url = url.endsWith("&") ? url.substring(0, url.length() - 1) : url;

        // Remove "/api"-part to save some space
        return url.replace("/api", "");
    }

    /**
     * Reads a Kafka header.
     *
     * @param record the Kafka record
     * @param headerName the name of the header that should be read
     * @param fallback the fallback value
     * @return the header value or the fallback
     */
    private String readKafkaHeader(ConsumerRecord<?, ?> record, String headerName, String fallback) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null) {
            return fallback;
        }

        return new String(header.value(), StandardCharsets.UTF_8);
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
        final int MAX_BODY_SIZE = 4096;
        final StringBuilder bodyStringBuilder = new StringBuilder();
        
        if (stream == null) {
        	return null;
        }
        
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
     * Normalizes the payload for storage.
     *
     * @param payload the payload
     * @return the normalized payload or {@code null}
     */
    private String normalizePayload(String payload) {
        if (Assertion.isNullOrEmpty(payload)) {
            return null;
        }

        // Remove all whitespaces
        return payload.replaceAll("\\s+", "");
    }

    /**
     * Retrieves the current JWT token from Spring's security context if present.
     *
     * @return the JWT authentication token or {@code null}
     */
    private JwtAuthenticationToken getJwtAuthenticationToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken;
        }

        return null;
    }

    /**
     * Returns a list of groups the user-account is assigned to.
     *
     * @param token the JWT access token of the requester
     * @return the list of group names
     */
    private ArrayList<String> getGroupNamesFromToken(JwtAuthenticationToken token) {
        ArrayList<String> groups = new ArrayList<>();

        if (token != null && token.getToken().hasClaim(auditUserTypeConfig.getTokenClaimName())) {
            @SuppressWarnings("unchecked")
			ArrayList<String> otherClaims = (ArrayList<String>) token.getToken().getClaim(auditUserTypeConfig.getTokenClaimName());
            
            return otherClaims == null ? groups : otherClaims;
        }

        // If the token is null, return an empty list
        return groups;
    }

    /**
     * Resolves the user type from group names.
     *
     * @param groupNames the group names from the authentication token
     * @return the resolved user type
     */
    private AuditUserType resolveUserType(List<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return AuditUserType.UNKNOWN;
        }

        if (groupNames.contains("/" + auditUserTypeConfig.getHumanUserGroupName())) {
            return AuditUserType.HUMAN;
        }

        if (groupNames.contains("/" + auditUserTypeConfig.getTechnicalUserGroupName())) {
            return AuditUserType.TECHNICAL;
        }

        if (groupNames.contains("/" + auditUserTypeConfig.getAuditEverythingUserGroupName())) {
            return AuditUserType.ALL;
        }

        if (groupNames.contains("/" + auditUserTypeConfig.getNoAuditingUserGroupName())) {
            return AuditUserType.NONE;
        }

        return AuditUserType.UNKNOWN;
    }
}

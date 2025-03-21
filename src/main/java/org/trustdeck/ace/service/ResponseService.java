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

package org.trustdeck.ace.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.WebRequest;
import org.trustdeck.ace.configuration.ResponseMediaTypeConfig;
import org.trustdeck.ace.model.dto.HttpStatusDto;
import org.trustdeck.ace.model.dto.IRepresentation;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Handles the responses depending on the response type requested by the user.
 * 
 * @author Eric Wündisch & Armin Müller
 */
@Service
@Slf4j
public class ResponseService {

    /** The configuration for the pseudonymization service's response media type. */
    @Autowired
    ResponseMediaTypeConfig responseMediaTypeConfig;

    /** Defines a list of media types that the pseudonymization service is able to respond with. */
    public static final Set<String> supportedMediaTypes = Set.of(MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE);

    /**
     * Checks whether or not the provided media type is in the list of supported media types.
     *
     * @param mediaType the media type
     * @return whether the media type is supported or not
     */
    public boolean isMediaTypeSupportedByService(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        
        return supportedMediaTypes.contains(mediaType);
    }

    /**
     * Checks whether the given media type is valid. If not the 
     * default preferred media type is returned.
     *
     * @param mediaType the media type to validate
     * @return the given media type if it's valid, the preferred media type if not
     */
    public String getValidMediaType(String mediaType) {
        if (this.isMediaTypeSupportedByService(mediaType)) {
            return mediaType;
        } else {
            return this.responseMediaTypeConfig.getPreferredMediaType();
        }
    }

    /**
     * Retrieves the media type value from the request.
     *
     * @param request the request
     * @return the media type value from the request
     */
    public String getMediaTypeFromRequest(WebRequest request) {
        try {
            return this.getValidMediaType(request.getHeader(HttpHeaders.ACCEPT));
        } catch (Exception e) {
            // In case something went wrong, we return the preferred accepted media type.
        	log.debug("Couldn't retrieve the media type from the given request and will use the standard one: " + e.getMessage());
            return this.responseMediaTypeConfig.getPreferredMediaType();
        }
    }

    /**
     * Create a HTTP status DTO taking the request object into account.
     *
     * @param status the HTTP status
     * @param request the request
     * @return the HTTP status response DTO or an empty string
     */
    public Object createHttpStatusDtoFromRequest(HttpStatus status, WebRequest request) {
        return this.createHttpStatusDtoFromMediaType(status, this.getMediaTypeFromRequest(request));
    }

    /**
     * Create a HTTP status DTO taking into account which media type the response should have.
     *
     * @param status the HTTP status
     * @param mediaType the media type the response should have
     * @return the HTTP status response DTO or an (empty) string
     */
    public Object createHttpStatusDtoFromMediaType(HttpStatus status, String mediaType) {
        HttpStatusDto httpStatusDto = new HttpStatusDto().assignPojoValues(status);
        if (httpStatusDto == null) {
        	return "";
        }

        switch (mediaType) {
            case MediaType.APPLICATION_JSON_VALUE:
                return httpStatusDto;
            case MediaType.TEXT_PLAIN_VALUE:
                return httpStatusDto.toRepresentationString();
            default:
                // Due to several precautions, this case shouldn't be 
            	// reached, but this must return at least an empty string.
                return "";
        }
    }

    /**
     * Builds the content type header string for the response.
     *
     * @param mediaType the media type
     * @return the content type header string for the response
     */
    public String buildContentTypeHeaderStringForResponse(String mediaType) {
        return mediaType + ";charset=" + responseMediaTypeConfig.getEncodingCharset();
    }
    
    /**
     * Creates a response entity object that can be returned 
     * to the user in the requested media type.
     * 
     * @param <T> the type parameter
     * @param mediaType the requested media type for the response
     * @param status the HTTP status
     * @param location the location parameter for when needed (e.g. for 201-CREATED)
     * @return the response entity object matching the requested response media type
     */
    @SuppressWarnings("unchecked")
	private <T> ResponseEntity<T> createResponseEntityFromHttpStatus(String mediaType, HttpStatus status, URI location) {
        String validMediaTypeValueFromString = this.getValidMediaType(mediaType);
        Object responseObject = this.createHttpStatusDtoFromMediaType(status, validMediaTypeValueFromString);
        String responseContentTypeHeader = this.buildContentTypeHeaderStringForResponse(validMediaTypeValueFromString);

        return (ResponseEntity<T>) ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, responseContentTypeHeader).location(location).body(responseObject);
    }

    /**
     * Not found (404) response entity.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> notFound(String mediaType) {
        return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.NOT_FOUND, null);
    }

    /**
     * Bad request (400) response entity.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> badRequest(String mediaType) {
        return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.BAD_REQUEST, null);
    }

    /**
     * Not acceptable (406) response entity.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> notAcceptable(String mediaType) {
        return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.NOT_ACCEPTABLE, null);
    }


    /**
     * Unprocessable entity (422) response entity.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> unprocessableEntity(String mediaType) {
        return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.UNPROCESSABLE_ENTITY, null);
    }

    /**
     * Internal server error (500) response entity.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> internalServerError(String mediaType) {
        return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    /**
     * No content (204) response entity.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> noContent(String mediaType) {
        return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.NO_CONTENT, null);
    }

    /**
     * Insufficient storage (507) response entity.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> insufficientStorage(String mediaType) {
        return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.INSUFFICIENT_STORAGE, null);
    }

    /**
     * Creates a response entity object that can be returned 
     * to the user with the requested media type. Adds a body 
     * to the response if required.
     * 
     * @param <T> the type parameter
     * @param status the HTTP status
     * @param mediaType the requested media type for the response
     * @param body the response's body
     * @param location the location parameter for when needed (e.g. for 201-CREATED)
     * @return the response entity object matching the requested response media type
     */
    @SuppressWarnings("unchecked")
	private <T> ResponseEntity<T> createResponseEntityFromBody(HttpStatus status, String mediaType, @Nullable T body, URI location) {
        String validResponseMediaType = this.getValidMediaType(mediaType);

        if (body == null) {
            return this.createResponseEntityFromHttpStatus(validResponseMediaType, status, location);
        } else {
            String contentTypeHeader = this.buildContentTypeHeaderStringForResponse(validResponseMediaType);

            // If the object is one of the usual DTOs
            if (body instanceof IRepresentation) {
                switch (validResponseMediaType) {
                    case MediaType.APPLICATION_JSON_VALUE:
                        return ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, contentTypeHeader).location(location).body(body);
                    case MediaType.TEXT_PLAIN_VALUE:
                        return (ResponseEntity<T>) ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, contentTypeHeader).location(location).body(((IRepresentation<?, ?>) body).toRepresentationString());
                    default:
                        return this.createResponseEntityFromHttpStatus(mediaType, status, location);
                }
            } else if (body instanceof List<?> && ((List<?>) body).size() == 0) {
            	// Empty list; return empty JSON
            	return (ResponseEntity<T>) ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, contentTypeHeader).location(location).body("{}");
            } else if (body instanceof List<?> && ((List<?>) body).get(0) instanceof String) {
            	// Usually this is reached while using any batch endpoint
            	return (ResponseEntity<T>) ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, contentTypeHeader).location(location).body(String.join(";", (Iterable<? extends CharSequence>) body));
            } else {
                return ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, contentTypeHeader).location(location).body(body);
            }
        }
    }

    /**
     * Ok (200) response entity <b>without</b> a body.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> ok(String mediaType) {
        return this.createResponseEntityFromBody(HttpStatus.OK, mediaType, null, null);
    }

    /**
     * Ok (200) response entity <b>with</b> a body.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @param body the body
     * @return the response entity
     */
    public <T> ResponseEntity<T> ok(String mediaType, T body) {
        return this.createResponseEntityFromBody(HttpStatus.OK, mediaType, body, null);
    }

    /**
     * Created (201) response entity <b>without</b> a body and <b>without</b> a location.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> created(String mediaType) {
        return this.createResponseEntityFromBody(HttpStatus.CREATED, mediaType, null, null);
    }

    /**
     * Created (201) response entity <b>with</b> a body and <b>without</b> a location.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @param body the body
     * @return the response entity
     */
    public <T> ResponseEntity<T> created(String mediaType, T body) {
        return this.createResponseEntityFromBody(HttpStatus.CREATED, mediaType, body, null);
    }

    /**
     * Created (201) response entity <b>without</b> a body and <b>with</b> a location.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @param location the location
     * @return the response entity
     */
    public <T> ResponseEntity<T> created(String mediaType, URI location) {
        return this.createResponseEntityFromBody(HttpStatus.CREATED, mediaType, null, location);
    }

    /**
     * Created (201) response entity <b>with</b> a body and <b>with</b> a location.
     *
     * @param <T> the type parameter
     * @param mediaType the media type
     * @param location the location
     * @param body the body
     * @return the response entity
     */
    public <T> ResponseEntity<T> created(String mediaType, URI location, T body) {
        return this.createResponseEntityFromBody(HttpStatus.CREATED, mediaType, body, location);
    }
    
    /**
     * Forbidden (403) response entity.
     * 
     * @param <T> the type parameter
     * @param mediaType the media type
     * @return the response entity
     */
    public <T> ResponseEntity<T> forbidden(String mediaType) {
    	return this.createResponseEntityFromHttpStatus(mediaType, HttpStatus.FORBIDDEN, null);
    }
}

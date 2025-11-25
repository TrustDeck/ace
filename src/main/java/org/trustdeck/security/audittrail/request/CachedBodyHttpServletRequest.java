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

package org.trustdeck.security.audittrail.request;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.util.StreamUtils;

/**
 * Wrapper class that allows for multiple reads on the request body.
 * Adapted from <a href="https://www.baeldung.com/spring-reading-httpservletrequest-multiple-times">Baeldung: Reading HttpServletRequest Multiple Times</a>.
 * 
 * @author Baeldung, Armin Müller
 *
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
	
	/** Stores the body. */
	private byte[] cachedBody;

	/**
	 * Constructor.
	 * 
	 * @param request the request that should be wrapped.
	 * @throws IOException when copying the body fails
	 */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        
        // Get the type of content that was sent with the request --> check if it was a file (represented as a multipart)
        String contentType = request.getContentType();
        boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
        
        // Only copy the request body when it's not a multipart request as calling getInputStream() 
        // is mutually exclusive to calling getParts, which happens later by Spring
		if (!isMultipart) {
			this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
		} else {
			this.cachedBody = new byte[0];
		}
    }
    
    @Override
    public ServletInputStream getInputStream() {
    	try {
			if (cachedBody == null || cachedBody.length == 0) {
				// Don’t interfere with multipart requests
				return super.getInputStream();
			}
			
			return new CachedBodyServletInputStream(this.cachedBody);
		} catch (IOException e) {
			return null;
		}
    }

    @Override
    public BufferedReader getReader() throws IOException {
		// Create a reader from the cached content and return it
    	if (cachedBody == null || cachedBody.length == 0) {
    		// Don’t interfere with multipart requests
			return super.getReader();
		}
		
		// Request has body data, return the cached content
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream));
    }
}

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

package org.trustdeck.ace.security.audittrail.request;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.util.StreamUtils;

/**
 * Wrapper class that allows for multiple reads on the request body.
 * Adapted from {@link https://www.baeldung.com/spring-reading-httpservletrequest-multiple-times}.
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
	 * @throws IOException
	 */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        InputStream requestInputStream = request.getInputStream();
        this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
    }
    
    @Override
    public ServletInputStream getInputStream() {
    	try {
			return new CachedBodyServletInputStream(this.cachedBody);
		} catch (Exception e) {
			return null;
		}
    }

    @Override
    public BufferedReader getReader() throws IOException {
        // Create a reader from the cached content and return it
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream));
    }
}

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

package org.trustdeck.ace.security.audittrail.request;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

/**
 * Enables the servlet input stream to be read multiple times without being destroyed.
 * Adapted from {@link https://www.baeldung.com/spring-reading-httpservletrequest-multiple-times}.
 * 
 * @author Baeldung, Armin Müller
 *
 */
public class CachedBodyServletInputStream extends ServletInputStream {

	/** The cached input stream. */
    private InputStream cachedBodyInputStream;

    /**
     * Constructor.
     * 
     * @param cachedBody the byte-array of the cached body.
     */
    public CachedBodyServletInputStream(byte[] cachedBody) {
        this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
    }

    @Override
    public boolean isFinished() {
        try {
            return cachedBodyInputStream.available() == 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return false;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    /**
     * This re-implementation will always throw an UnsupportedOperationException.
     */
    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read() throws IOException {
        return cachedBodyInputStream.read();
    }
}

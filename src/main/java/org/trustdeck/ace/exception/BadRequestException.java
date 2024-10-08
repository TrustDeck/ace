/*
 * ACE - Advanced Confidentiality Engine
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

package org.trustdeck.ace.exception;

/**
 * This class represents an exception when a bad HTTP request was encountered.
 * 
 * @author Eric Wündisch & Armin Müller
 *
 */
public class BadRequestException extends NullPointerException {
	
	/** Exception UID. */
    private static final long serialVersionUID = -1214596448189436879L;

    /**
     * Constructor that only specifies the exception message.
     * @param msg the exception message
     */
	public BadRequestException(String msg) {
        super(msg);
    }
}

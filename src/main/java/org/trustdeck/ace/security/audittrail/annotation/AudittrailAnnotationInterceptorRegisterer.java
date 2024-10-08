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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * This class is used to add the audittrail annotation interceptor in the interceptor registry.
 * 
 * @author Armin Müller & Eric Wündisch
 */
@Configuration
public class AudittrailAnnotationInterceptorRegisterer implements WebMvcConfigurer {

    /** The audit trail annotation interceptor. */
    @Autowired
    AudittrailAnnotationInterceptor audittrailAnnotationInterceptor;

    /**
     * Registers the audit trail interceptor.
     *
     * @param registry the registry to use
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(audittrailAnnotationInterceptor);
    }
}
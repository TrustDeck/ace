/*
 * Trust Deck Services
 * Copyright 2024-2025 Armin Müller and contributors
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

package org.trustdeck.security.audittrail.annotation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.trustdeck.exception.AuditTrailException;
import org.trustdeck.jooq.generated.tables.daos.AuditeventDao;
import org.trustdeck.jooq.generated.tables.pojos.Auditevent;
import org.trustdeck.security.audittrail.event.AuditEventBuilder;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Class that automatically intercepts the audit annotation and stores the collected information 
 * using the same transaction as the database method.
 * 
 * @author Armin Müller
 */
@Aspect
@Component
@Slf4j
public class AuditAnnotationAspect {

	/** The request object containing information needed for the audit trail. */
    @Autowired
    private HttpServletRequest request;

	/** The builder that creates a coherent audit object out of the collected information. */
    @Autowired
    private AuditEventBuilder auditEventBuilder;

	/** The database context. */
    @Autowired
    private DSLContext dsl;
    
    /** The data access object for the audit trail table. */
    private AuditeventDao auditDao;
    
    /**
     * Method to retrieve the audit event data access object (or create it if it's {@code null}.)
     *
     * @return the audit event DAO
     */
    private AuditeventDao getAuditeventDao() {
        if (this.auditDao == null) {
            this.auditDao = new AuditeventDao(this.dsl.configuration());
        }

        return this.auditDao;
    }

    /**
     * The method that intercepts the audit annotation, collects all necessary information
     * and writes the audit trail to the database.
     * 
     * @param joinPoint the point where the process execution should proceed from
     * @param auditAnnotation the audit annotation object that includes some audit information
     * @return the result of proceeding
     * @throws AuditTrailException if anything goes wrong while the audit trail is written, this exception
     * 			is thrown in order to abort the current transaction and roll back to a consistent database state.
     */
    @Around("@annotation(auditAnnotation)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audit auditAnnotation) throws AuditTrailException {
        Object result = null;

        try {
            // Proceed with the original method
            result = joinPoint.proceed();

            // Create audit event object, if request-object is available
            if (request != null) {
                Auditevent auditEvent = auditEventBuilder.build(request);
                if (auditEvent != null) {
                	// Write audit information into database
                	this.getAuditeventDao().insert(auditEvent);
                }
            }
        } catch (Throwable e) {
        	log.debug("Audit trail information could not be stored: " + e.getClass().getSimpleName());
            log.trace("\t" + e.getMessage());
            
            // Throw an exception to terminate the transaction that surrounds the audit annotation processing.
            throw new AuditTrailException();
        }

        return result;
    }
}

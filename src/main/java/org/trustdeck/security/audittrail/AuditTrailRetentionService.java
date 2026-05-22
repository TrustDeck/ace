/*
 * Trust Deck Services
 * Copyright 2025 Armin Müller and contributors
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
package org.trustdeck.security.audittrail;

import java.time.DateTimeException;
import java.time.LocalDateTime;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.trustdeck.configuration.AuditTrailRetentionConfiguration;

import static org.trustdeck.jooq.generated.Tables.AUDIT_EVENT;

import lombok.extern.slf4j.Slf4j;

/**
 * This service is responsible for periodically cleaning up old entries in the
 * audit trail database table.
 *
 * @author Armin Müller
 */
@Service
@Slf4j
public class AuditTrailRetentionService {

	/** The jOOQ DSL context for database interaction. */
	@Autowired
	private DSLContext dslCtx;

	/** The configuration for the audit trail cleanup. */
	@Autowired
	private AuditTrailRetentionConfiguration retentionConfig;

	/**
	 * Periodically cleans up old audit trail entries based on the configured
	 * retention period. The schedule is defined by a cron expression in the
	 * application configuration.
	 */
	@Scheduled(cron = "${audittrail.cleanup.cron}")
	public void cleanupOldAuditEntries() {
		// Check if periodic cleaning is enabled
		if (!retentionConfig.isEnabled()) {
			log.debug("AuditTrail cleanup is disabled. Skipping task.");
			return;
		}

		log.info("Starting scheduled cleanup of old audit trail entries...");

		try {
			LocalDateTime cutoffDateTime = LocalDateTime.now().minusDays(retentionConfig.getRetentionDays());
			log.trace("Deleting audit trail entries older than " + cutoffDateTime + ".");

			int deletedRows = dslCtx.transactionResult(configuration ->
				DSL.using(configuration)
				.deleteFrom(AUDIT_EVENT)
				.where(AUDIT_EVENT.REQUEST_TIME.lt(cutoffDateTime))
				.execute());

			log.info("Successfully deleted " + deletedRows + " old audit trail entries.");
		} catch (DateTimeException e) {
			log.error("Could not determine cutoff date.", e);
		} catch (DataAccessException f) {
			log.error("Deleting old audit trail entries failed due to a database issue: ", f);
		} catch (RuntimeException g) {
			log.error("Error occurred during audit trail cleanup task.", g);
		}
	}
}

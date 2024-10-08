/*
 * This file is generated by jOOQ.
 */
package org.trustdeck.ace.jooq.generated.tables.daos;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jooq.Configuration;
import org.jooq.impl.DAOImpl;
import org.trustdeck.ace.jooq.generated.tables.Pseudonym;
import org.trustdeck.ace.jooq.generated.tables.records.PseudonymRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class PseudonymDao extends DAOImpl<PseudonymRecord, org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym, Long> {

    /**
     * Create a new PseudonymDao without any configuration
     */
    public PseudonymDao() {
        super(Pseudonym.PSEUDONYM, org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym.class);
    }

    /**
     * Create a new PseudonymDao with an attached configuration
     */
    public PseudonymDao(Configuration configuration) {
        super(Pseudonym.PSEUDONYM, org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym.class, configuration);
    }

    @Override
    public Long getId(org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym object) {
        return object.getId();
    }

    /**
     * Fetch records that have <code>id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfId(Long lowerInclusive, Long upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.ID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>id IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchById(Long... values) {
        return fetch(Pseudonym.PSEUDONYM.ID, values);
    }

    /**
     * Fetch a unique record that has <code>id = value</code>
     */
    public org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym fetchOneById(Long value) {
        return fetchOne(Pseudonym.PSEUDONYM.ID, value);
    }

    /**
     * Fetch a unique record that has <code>id = value</code>
     */
    public Optional<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchOptionalById(Long value) {
        return fetchOptional(Pseudonym.PSEUDONYM.ID, value);
    }

    /**
     * Fetch records that have <code>identifier BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfIdentifier(String lowerInclusive, String upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.IDENTIFIER, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>identifier IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByIdentifier(String... values) {
        return fetch(Pseudonym.PSEUDONYM.IDENTIFIER, values);
    }

    /**
     * Fetch records that have <code>idtype BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfIdtype(String lowerInclusive, String upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.IDTYPE, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>idtype IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByIdtype(String... values) {
        return fetch(Pseudonym.PSEUDONYM.IDTYPE, values);
    }

    /**
     * Fetch records that have <code>pseudonym BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfPseudonym(String lowerInclusive, String upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.PSEUDONYM_, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>pseudonym IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByPseudonym(String... values) {
        return fetch(Pseudonym.PSEUDONYM.PSEUDONYM_, values);
    }

    /**
     * Fetch records that have <code>validfrom BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfValidfrom(LocalDateTime lowerInclusive, LocalDateTime upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.VALIDFROM, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validfrom IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByValidfrom(LocalDateTime... values) {
        return fetch(Pseudonym.PSEUDONYM.VALIDFROM, values);
    }

    /**
     * Fetch records that have <code>validfrominherited BETWEEN lowerInclusive
     * AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfValidfrominherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.VALIDFROMINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validfrominherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByValidfrominherited(Boolean... values) {
        return fetch(Pseudonym.PSEUDONYM.VALIDFROMINHERITED, values);
    }

    /**
     * Fetch records that have <code>validto BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfValidto(LocalDateTime lowerInclusive, LocalDateTime upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.VALIDTO, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validto IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByValidto(LocalDateTime... values) {
        return fetch(Pseudonym.PSEUDONYM.VALIDTO, values);
    }

    /**
     * Fetch records that have <code>validtoinherited BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfValidtoinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.VALIDTOINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validtoinherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByValidtoinherited(Boolean... values) {
        return fetch(Pseudonym.PSEUDONYM.VALIDTOINHERITED, values);
    }

    /**
     * Fetch records that have <code>domainid BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchRangeOfDomainid(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(Pseudonym.PSEUDONYM.DOMAINID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>domainid IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Pseudonym> fetchByDomainid(Integer... values) {
        return fetch(Pseudonym.PSEUDONYM.DOMAINID, values);
    }
}

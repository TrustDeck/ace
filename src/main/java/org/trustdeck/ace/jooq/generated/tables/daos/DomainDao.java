/*
 * This file is generated by jOOQ.
 */
package org.trustdeck.ace.jooq.generated.tables.daos;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jooq.Configuration;
import org.jooq.impl.DAOImpl;
import org.trustdeck.ace.jooq.generated.tables.Domain;
import org.trustdeck.ace.jooq.generated.tables.records.DomainRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class DomainDao extends DAOImpl<DomainRecord, org.trustdeck.ace.jooq.generated.tables.pojos.Domain, Integer> {

    /**
     * Create a new DomainDao without any configuration
     */
    public DomainDao() {
        super(Domain.DOMAIN, org.trustdeck.ace.jooq.generated.tables.pojos.Domain.class);
    }

    /**
     * Create a new DomainDao with an attached configuration
     */
    public DomainDao(Configuration configuration) {
        super(Domain.DOMAIN, org.trustdeck.ace.jooq.generated.tables.pojos.Domain.class, configuration);
    }

    @Override
    public Integer getId(org.trustdeck.ace.jooq.generated.tables.pojos.Domain object) {
        return object.getId();
    }

    /**
     * Fetch records that have <code>id BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfId(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(Domain.DOMAIN.ID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>id IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchById(Integer... values) {
        return fetch(Domain.DOMAIN.ID, values);
    }

    /**
     * Fetch a unique record that has <code>id = value</code>
     */
    public org.trustdeck.ace.jooq.generated.tables.pojos.Domain fetchOneById(Integer value) {
        return fetchOne(Domain.DOMAIN.ID, value);
    }

    /**
     * Fetch a unique record that has <code>id = value</code>
     */
    public Optional<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchOptionalById(Integer value) {
        return fetchOptional(Domain.DOMAIN.ID, value);
    }

    /**
     * Fetch records that have <code>name BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfName(String lowerInclusive, String upperInclusive) {
        return fetchRange(Domain.DOMAIN.NAME, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>name IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByName(String... values) {
        return fetch(Domain.DOMAIN.NAME, values);
    }

    /**
     * Fetch a unique record that has <code>name = value</code>
     */
    public org.trustdeck.ace.jooq.generated.tables.pojos.Domain fetchOneByName(String value) {
        return fetchOne(Domain.DOMAIN.NAME, value);
    }

    /**
     * Fetch a unique record that has <code>name = value</code>
     */
    public Optional<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchOptionalByName(String value) {
        return fetchOptional(Domain.DOMAIN.NAME, value);
    }

    /**
     * Fetch records that have <code>prefix BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfPrefix(String lowerInclusive, String upperInclusive) {
        return fetchRange(Domain.DOMAIN.PREFIX, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>prefix IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByPrefix(String... values) {
        return fetch(Domain.DOMAIN.PREFIX, values);
    }

    /**
     * Fetch records that have <code>validfrom BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfValidfrom(LocalDateTime lowerInclusive, LocalDateTime upperInclusive) {
        return fetchRange(Domain.DOMAIN.VALIDFROM, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validfrom IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByValidfrom(LocalDateTime... values) {
        return fetch(Domain.DOMAIN.VALIDFROM, values);
    }

    /**
     * Fetch records that have <code>validfrominherited BETWEEN lowerInclusive
     * AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfValidfrominherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.VALIDFROMINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validfrominherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByValidfrominherited(Boolean... values) {
        return fetch(Domain.DOMAIN.VALIDFROMINHERITED, values);
    }

    /**
     * Fetch records that have <code>validto BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfValidto(LocalDateTime lowerInclusive, LocalDateTime upperInclusive) {
        return fetchRange(Domain.DOMAIN.VALIDTO, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validto IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByValidto(LocalDateTime... values) {
        return fetch(Domain.DOMAIN.VALIDTO, values);
    }

    /**
     * Fetch records that have <code>validtoinherited BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfValidtoinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.VALIDTOINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>validtoinherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByValidtoinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.VALIDTOINHERITED, values);
    }

    /**
     * Fetch records that have <code>enforcestartdatevalidity BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfEnforcestartdatevalidity(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ENFORCESTARTDATEVALIDITY, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>enforcestartdatevalidity IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByEnforcestartdatevalidity(Boolean... values) {
        return fetch(Domain.DOMAIN.ENFORCESTARTDATEVALIDITY, values);
    }

    /**
     * Fetch records that have <code>enforcestartdatevalidityinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfEnforcestartdatevalidityinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ENFORCESTARTDATEVALIDITYINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>enforcestartdatevalidityinherited IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByEnforcestartdatevalidityinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.ENFORCESTARTDATEVALIDITYINHERITED, values);
    }

    /**
     * Fetch records that have <code>enforceenddatevalidity BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfEnforceenddatevalidity(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ENFORCEENDDATEVALIDITY, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>enforceenddatevalidity IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByEnforceenddatevalidity(Boolean... values) {
        return fetch(Domain.DOMAIN.ENFORCEENDDATEVALIDITY, values);
    }

    /**
     * Fetch records that have <code>enforceenddatevalidityinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfEnforceenddatevalidityinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ENFORCEENDDATEVALIDITYINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>enforceenddatevalidityinherited IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByEnforceenddatevalidityinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.ENFORCEENDDATEVALIDITYINHERITED, values);
    }

    /**
     * Fetch records that have <code>algorithm BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfAlgorithm(String lowerInclusive, String upperInclusive) {
        return fetchRange(Domain.DOMAIN.ALGORITHM, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>algorithm IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByAlgorithm(String... values) {
        return fetch(Domain.DOMAIN.ALGORITHM, values);
    }

    /**
     * Fetch records that have <code>algorithminherited BETWEEN lowerInclusive
     * AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfAlgorithminherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ALGORITHMINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>algorithminherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByAlgorithminherited(Boolean... values) {
        return fetch(Domain.DOMAIN.ALGORITHMINHERITED, values);
    }

    /**
     * Fetch records that have <code>alphabet BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfAlphabet(String lowerInclusive, String upperInclusive) {
        return fetchRange(Domain.DOMAIN.ALPHABET, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>alphabet IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByAlphabet(String... values) {
        return fetch(Domain.DOMAIN.ALPHABET, values);
    }

    /**
     * Fetch records that have <code>alphabetinherited BETWEEN lowerInclusive
     * AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfAlphabetinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ALPHABETINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>alphabetinherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByAlphabetinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.ALPHABETINHERITED, values);
    }

    /**
     * Fetch records that have <code>randomalgorithmdesiredsize BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfRandomalgorithmdesiredsize(Long lowerInclusive, Long upperInclusive) {
        return fetchRange(Domain.DOMAIN.RANDOMALGORITHMDESIREDSIZE, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>randomalgorithmdesiredsize IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByRandomalgorithmdesiredsize(Long... values) {
        return fetch(Domain.DOMAIN.RANDOMALGORITHMDESIREDSIZE, values);
    }

    /**
     * Fetch records that have <code>randomalgorithmdesiredsizeinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfRandomalgorithmdesiredsizeinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.RANDOMALGORITHMDESIREDSIZEINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>randomalgorithmdesiredsizeinherited IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByRandomalgorithmdesiredsizeinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.RANDOMALGORITHMDESIREDSIZEINHERITED, values);
    }

    /**
     * Fetch records that have <code>randomalgorithmdesiredsuccessprobability
     * BETWEEN lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfRandomalgorithmdesiredsuccessprobability(Double lowerInclusive, Double upperInclusive) {
        return fetchRange(Domain.DOMAIN.RANDOMALGORITHMDESIREDSUCCESSPROBABILITY, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>randomalgorithmdesiredsuccessprobability IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByRandomalgorithmdesiredsuccessprobability(Double... values) {
        return fetch(Domain.DOMAIN.RANDOMALGORITHMDESIREDSUCCESSPROBABILITY, values);
    }

    /**
     * Fetch records that have
     * <code>randomalgorithmdesiredsuccessprobabilityinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfRandomalgorithmdesiredsuccessprobabilityinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.RANDOMALGORITHMDESIREDSUCCESSPROBABILITYINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have
     * <code>randomalgorithmdesiredsuccessprobabilityinherited IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByRandomalgorithmdesiredsuccessprobabilityinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.RANDOMALGORITHMDESIREDSUCCESSPROBABILITYINHERITED, values);
    }

    /**
     * Fetch records that have <code>multiplepsnallowed BETWEEN lowerInclusive
     * AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfMultiplepsnallowed(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.MULTIPLEPSNALLOWED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>multiplepsnallowed IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByMultiplepsnallowed(Boolean... values) {
        return fetch(Domain.DOMAIN.MULTIPLEPSNALLOWED, values);
    }

    /**
     * Fetch records that have <code>multiplepsnallowedinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfMultiplepsnallowedinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.MULTIPLEPSNALLOWEDINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>multiplepsnallowedinherited IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByMultiplepsnallowedinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.MULTIPLEPSNALLOWEDINHERITED, values);
    }

    /**
     * Fetch records that have <code>consecutivevaluecounter BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfConsecutivevaluecounter(Long lowerInclusive, Long upperInclusive) {
        return fetchRange(Domain.DOMAIN.CONSECUTIVEVALUECOUNTER, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>consecutivevaluecounter IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByConsecutivevaluecounter(Long... values) {
        return fetch(Domain.DOMAIN.CONSECUTIVEVALUECOUNTER, values);
    }

    /**
     * Fetch records that have <code>pseudonymlength BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfPseudonymlength(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(Domain.DOMAIN.PSEUDONYMLENGTH, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>pseudonymlength IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByPseudonymlength(Integer... values) {
        return fetch(Domain.DOMAIN.PSEUDONYMLENGTH, values);
    }

    /**
     * Fetch records that have <code>pseudonymlengthinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfPseudonymlengthinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.PSEUDONYMLENGTHINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>pseudonymlengthinherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByPseudonymlengthinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.PSEUDONYMLENGTHINHERITED, values);
    }

    /**
     * Fetch records that have <code>paddingcharacter BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfPaddingcharacter(String lowerInclusive, String upperInclusive) {
        return fetchRange(Domain.DOMAIN.PADDINGCHARACTER, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>paddingcharacter IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByPaddingcharacter(String... values) {
        return fetch(Domain.DOMAIN.PADDINGCHARACTER, values);
    }

    /**
     * Fetch records that have <code>paddingcharacterinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfPaddingcharacterinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.PADDINGCHARACTERINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>paddingcharacterinherited IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByPaddingcharacterinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.PADDINGCHARACTERINHERITED, values);
    }

    /**
     * Fetch records that have <code>addcheckdigit BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfAddcheckdigit(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ADDCHECKDIGIT, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>addcheckdigit IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByAddcheckdigit(Boolean... values) {
        return fetch(Domain.DOMAIN.ADDCHECKDIGIT, values);
    }

    /**
     * Fetch records that have <code>addcheckdigitinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfAddcheckdigitinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.ADDCHECKDIGITINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>addcheckdigitinherited IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByAddcheckdigitinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.ADDCHECKDIGITINHERITED, values);
    }

    /**
     * Fetch records that have <code>lengthincludescheckdigit BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfLengthincludescheckdigit(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.LENGTHINCLUDESCHECKDIGIT, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>lengthincludescheckdigit IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByLengthincludescheckdigit(Boolean... values) {
        return fetch(Domain.DOMAIN.LENGTHINCLUDESCHECKDIGIT, values);
    }

    /**
     * Fetch records that have <code>lengthincludescheckdigitinherited BETWEEN
     * lowerInclusive AND upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfLengthincludescheckdigitinherited(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Domain.DOMAIN.LENGTHINCLUDESCHECKDIGITINHERITED, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>lengthincludescheckdigitinherited IN
     * (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByLengthincludescheckdigitinherited(Boolean... values) {
        return fetch(Domain.DOMAIN.LENGTHINCLUDESCHECKDIGITINHERITED, values);
    }

    /**
     * Fetch records that have <code>salt BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfSalt(String lowerInclusive, String upperInclusive) {
        return fetchRange(Domain.DOMAIN.SALT, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>salt IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchBySalt(String... values) {
        return fetch(Domain.DOMAIN.SALT, values);
    }

    /**
     * Fetch records that have <code>saltlength BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfSaltlength(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(Domain.DOMAIN.SALTLENGTH, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>saltlength IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchBySaltlength(Integer... values) {
        return fetch(Domain.DOMAIN.SALTLENGTH, values);
    }

    /**
     * Fetch records that have <code>description BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfDescription(String lowerInclusive, String upperInclusive) {
        return fetchRange(Domain.DOMAIN.DESCRIPTION, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>description IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchByDescription(String... values) {
        return fetch(Domain.DOMAIN.DESCRIPTION, values);
    }

    /**
     * Fetch records that have <code>superdomainid BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchRangeOfSuperdomainid(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(Domain.DOMAIN.SUPERDOMAINID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>superdomainid IN (values)</code>
     */
    public List<org.trustdeck.ace.jooq.generated.tables.pojos.Domain> fetchBySuperdomainid(Integer... values) {
        return fetch(Domain.DOMAIN.SUPERDOMAINID, values);
    }
}

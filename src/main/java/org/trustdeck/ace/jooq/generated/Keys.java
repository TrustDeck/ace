/*
 * This file is generated by jOOQ.
 */
package org.trustdeck.ace.jooq.generated;


import org.jooq.ForeignKey;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import org.trustdeck.ace.jooq.generated.tables.Auditevent;
import org.trustdeck.ace.jooq.generated.tables.Domain;
import org.trustdeck.ace.jooq.generated.tables.Pseudonym;
import org.trustdeck.ace.jooq.generated.tables.records.AuditeventRecord;
import org.trustdeck.ace.jooq.generated.tables.records.DomainRecord;
import org.trustdeck.ace.jooq.generated.tables.records.PseudonymRecord;


/**
 * A class modelling foreign key relationships and constraints of tables in
 * public.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<AuditeventRecord> AUDITEVENT_PKEY = Internal.createUniqueKey(Auditevent.AUDITEVENT, DSL.name("auditevent_pkey"), new TableField[] { Auditevent.AUDITEVENT.ID }, true);
    public static final UniqueKey<DomainRecord> DOMAIN_NAME_KEY = Internal.createUniqueKey(Domain.DOMAIN, DSL.name("domain_name_key"), new TableField[] { Domain.DOMAIN.NAME }, true);
    public static final UniqueKey<DomainRecord> DOMAIN_PKEY = Internal.createUniqueKey(Domain.DOMAIN, DSL.name("domain_pkey"), new TableField[] { Domain.DOMAIN.ID }, true);
    public static final UniqueKey<PseudonymRecord> PSEUDONYM_IDENTIFIER_IDTYPE_DOMAINID_PSEUDONYM_KEY = Internal.createUniqueKey(Pseudonym.PSEUDONYM, DSL.name("pseudonym_identifier_idtype_domainid_pseudonym_key"), new TableField[] { Pseudonym.PSEUDONYM.IDENTIFIER, Pseudonym.PSEUDONYM.IDTYPE, Pseudonym.PSEUDONYM.DOMAINID, Pseudonym.PSEUDONYM.PSEUDONYM_ }, true);
    public static final UniqueKey<PseudonymRecord> PSEUDONYM_PKEY = Internal.createUniqueKey(Pseudonym.PSEUDONYM, DSL.name("pseudonym_pkey"), new TableField[] { Pseudonym.PSEUDONYM.ID }, true);
    public static final UniqueKey<PseudonymRecord> PSEUDONYM_PSN_DOMAINID_KEY = Internal.createUniqueKey(Pseudonym.PSEUDONYM, DSL.name("pseudonym_psn_domainid_key"), new TableField[] { Pseudonym.PSEUDONYM.DOMAINID, Pseudonym.PSEUDONYM.PSEUDONYM_ }, true);

    // -------------------------------------------------------------------------
    // FOREIGN KEY definitions
    // -------------------------------------------------------------------------

    public static final ForeignKey<DomainRecord, DomainRecord> DOMAIN__DOMAIN_SUPERDOMAINID_FKEY = Internal.createForeignKey(Domain.DOMAIN, DSL.name("domain_superdomainid_fkey"), new TableField[] { Domain.DOMAIN.SUPERDOMAINID }, Keys.DOMAIN_PKEY, new TableField[] { Domain.DOMAIN.ID }, true);
    public static final ForeignKey<PseudonymRecord, DomainRecord> PSEUDONYM__PSEUDONYM_DOMAINID_FKEY = Internal.createForeignKey(Pseudonym.PSEUDONYM, DSL.name("pseudonym_domainid_fkey"), new TableField[] { Pseudonym.PSEUDONYM.DOMAINID }, Keys.DOMAIN_PKEY, new TableField[] { Domain.DOMAIN.ID }, true);
}

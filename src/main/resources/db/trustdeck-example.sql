--
-- PostgreSQL database dump
--

-- Dumped from database version 17.4 (Debian 17.4-1.pgdg120+2)
-- Dumped by pg_dump version 17.4 (Debian 17.4-1.pgdg120+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

\connect postgres

DROP DATABASE IF EXISTS trustdeck WITH (FORCE);

--
-- Name: trustdeck; Type: DATABASE; Schema: -; Owner: trustdeck-manager
--

CREATE DATABASE trustdeck WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE trustdeck OWNER TO "trustdeck-manager";

\connect trustdeck

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: algorithm; Type: TABLE; Schema: public; Owner: trustdeck-manager
--

CREATE TABLE public.algorithm (
    id integer NOT NULL,
    name text NOT NULL,
    alphabet text NOT NULL,
    randomalgorithmdesiredsize bigint NOT NULL,
    randomalgorithmdesiredsuccessprobability double precision NOT NULL,
    consecutivevaluecounter bigint NOT NULL,
    pseudonymlength integer NOT NULL,
    paddingcharacter character(1) NOT NULL,
    addcheckdigit boolean NOT NULL,
    lengthincludescheckdigit boolean NOT NULL,
    salt text NOT NULL,
    saltlength integer NOT NULL
);


ALTER TABLE public.algorithm OWNER TO "trustdeck-manager";

--
-- Name: algorithm_id_seq; Type: SEQUENCE; Schema: public; Owner: trustdeck-manager
--

CREATE SEQUENCE public.algorithm_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.algorithm_id_seq OWNER TO "trustdeck-manager";

--
-- Name: algorithm_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: trustdeck-manager
--

ALTER SEQUENCE public.algorithm_id_seq OWNED BY public.algorithm.id;


--
-- Name: auditevent; Type: TABLE; Schema: public; Owner: trustdeck-manager
--

CREATE TABLE public.auditevent (
    id bigint NOT NULL,
    requesttime timestamp without time zone NOT NULL,
    username text NOT NULL,
    requesterip text NOT NULL,
    requesturl text NOT NULL,
    requestbody text
);


ALTER TABLE public.auditevent OWNER TO "trustdeck-manager";

--
-- Name: auditevent_id_seq; Type: SEQUENCE; Schema: public; Owner: trustdeck-manager
--

CREATE SEQUENCE public.auditevent_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.auditevent_id_seq OWNER TO "trustdeck-manager";

--
-- Name: auditevent_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: trustdeck-manager
--

ALTER SEQUENCE public.auditevent_id_seq OWNED BY public.auditevent.id;


--
-- Name: domain; Type: TABLE; Schema: public; Owner: trustdeck-manager
--

CREATE TABLE public.domain (
    id integer NOT NULL,
    name text NOT NULL,
    prefix text NOT NULL,
    validfrom timestamp without time zone NOT NULL,
    validfrominherited boolean NOT NULL,
    validto timestamp without time zone NOT NULL,
    validtoinherited boolean NOT NULL,
    enforcestartdatevalidity boolean NOT NULL,
    enforcestartdatevalidityinherited boolean NOT NULL,
    enforceenddatevalidity boolean NOT NULL,
    enforceenddatevalidityinherited boolean NOT NULL,
    algorithm text NOT NULL,
    algorithminherited boolean NOT NULL,
    alphabet text NOT NULL,
    alphabetinherited boolean NOT NULL,
    randomalgorithmdesiredsize bigint NOT NULL,
    randomalgorithmdesiredsizeinherited boolean NOT NULL,
    randomalgorithmdesiredsuccessprobability double precision NOT NULL,
    randomalgorithmdesiredsuccessprobabilityinherited boolean NOT NULL,
    multiplepsnallowed boolean NOT NULL,
    multiplepsnallowedinherited boolean NOT NULL,
    consecutivevaluecounter bigint NOT NULL,
    pseudonymlength integer NOT NULL,
    pseudonymlengthinherited boolean NOT NULL,
    paddingcharacter character(1) NOT NULL,
    paddingcharacterinherited boolean NOT NULL,
    addcheckdigit boolean NOT NULL,
    addcheckdigitinherited boolean NOT NULL,
    lengthincludescheckdigit boolean NOT NULL,
    lengthincludescheckdigitinherited boolean NOT NULL,
    salt text NOT NULL,
    saltlength integer NOT NULL,
    description text,
    superdomainid integer
);


ALTER TABLE public.domain OWNER TO "trustdeck-manager";

--
-- Name: domain_id_seq; Type: SEQUENCE; Schema: public; Owner: trustdeck-manager
--

CREATE SEQUENCE public.domain_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.domain_id_seq OWNER TO "trustdeck-manager";

--
-- Name: domain_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: trustdeck-manager
--

ALTER SEQUENCE public.domain_id_seq OWNED BY public.domain.id;


--
-- Name: person; Type: TABLE; Schema: public; Owner: trustdeck-manager
--

CREATE TABLE public.person (
    id integer NOT NULL,
    firstname character varying(255) NOT NULL,
    lastname character varying(255) NOT NULL,
    birthname character varying(255),
    administrativegender character(1) NOT NULL,
    dateofbirth date,
    street character varying(255),
    postalcode character varying(20),
    city character varying(255),
    country character varying(100),
    identifier text NOT NULL,
    idtype text NOT NULL,
    identifieralgorithm integer NOT NULL
);


ALTER TABLE public.person OWNER TO "trustdeck-manager";

--
-- Name: person_id_seq; Type: SEQUENCE; Schema: public; Owner: trustdeck-manager
--

CREATE SEQUENCE public.person_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.person_id_seq OWNER TO "trustdeck-manager";

--
-- Name: person_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: trustdeck-manager
--

ALTER SEQUENCE public.person_id_seq OWNED BY public.person.id;


--
-- Name: pseudonym; Type: TABLE; Schema: public; Owner: trustdeck-manager
--

CREATE TABLE public.pseudonym (
    id bigint NOT NULL,
    identifier text NOT NULL,
    idtype text NOT NULL,
    pseudonym text NOT NULL,
    validfrom timestamp without time zone NOT NULL,
    validfrominherited boolean NOT NULL,
    validto timestamp without time zone NOT NULL,
    validtoinherited boolean NOT NULL,
    domainid integer
);


ALTER TABLE public.pseudonym OWNER TO "trustdeck-manager";

--
-- Name: pseudonym_id_seq; Type: SEQUENCE; Schema: public; Owner: trustdeck-manager
--

CREATE SEQUENCE public.pseudonym_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.pseudonym_id_seq OWNER TO "trustdeck-manager";

--
-- Name: pseudonym_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: trustdeck-manager
--

ALTER SEQUENCE public.pseudonym_id_seq OWNED BY public.pseudonym.id;


--
-- Name: algorithm id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.algorithm ALTER COLUMN id SET DEFAULT nextval('public.algorithm_id_seq'::regclass);


--
-- Name: auditevent id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.auditevent ALTER COLUMN id SET DEFAULT nextval('public.auditevent_id_seq'::regclass);


--
-- Name: domain id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.domain ALTER COLUMN id SET DEFAULT nextval('public.domain_id_seq'::regclass);


--
-- Name: person id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.person ALTER COLUMN id SET DEFAULT nextval('public.person_id_seq'::regclass);


--
-- Name: pseudonym id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.pseudonym ALTER COLUMN id SET DEFAULT nextval('public.pseudonym_id_seq'::regclass);


--
-- Name: algorithm algorithm_name_alphabet_randomalgorithmdesiredsize_randomal_key; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.algorithm
    ADD CONSTRAINT algorithm_name_alphabet_randomalgorithmdesiredsize_randomal_key UNIQUE (name, alphabet, randomalgorithmdesiredsize, randomalgorithmdesiredsuccessprobability, pseudonymlength, paddingcharacter, addcheckdigit, lengthincludescheckdigit);


--
-- Name: algorithm algorithm_pkey; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.algorithm
    ADD CONSTRAINT algorithm_pkey PRIMARY KEY (id);


--
-- Name: auditevent auditevent_pkey; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.auditevent
    ADD CONSTRAINT auditevent_pkey PRIMARY KEY (id);


--
-- Name: domain domain_name_key; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.domain
    ADD CONSTRAINT domain_name_key UNIQUE (name);


--
-- Name: domain domain_pkey; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.domain
    ADD CONSTRAINT domain_pkey PRIMARY KEY (id);


--
-- Name: person person_firstname_lastname_administrativegender_dateofbirth__key; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT person_firstname_lastname_administrativegender_dateofbirth__key UNIQUE (firstname, lastname, administrativegender, dateofbirth, street, postalcode, city, country);


--
-- Name: person person_identifier_idtype_key; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT person_identifier_idtype_key UNIQUE (identifier, idtype);


--
-- Name: person person_pkey; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT person_pkey PRIMARY KEY (id);


--
-- Name: pseudonym pseudonym_identifier_idtype_domainid_pseudonym_key; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.pseudonym
    ADD CONSTRAINT pseudonym_identifier_idtype_domainid_pseudonym_key UNIQUE (identifier, idtype, domainid, pseudonym);


--
-- Name: pseudonym pseudonym_pkey; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.pseudonym
    ADD CONSTRAINT pseudonym_pkey PRIMARY KEY (id);


--
-- Name: pseudonym pseudonym_psn_domainid_key; Type: CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.pseudonym
    ADD CONSTRAINT pseudonym_psn_domainid_key UNIQUE (domainid, pseudonym);


--
-- Name: algorithm_name_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX algorithm_name_uindex ON public.algorithm USING btree (name);


--
-- Name: auditusernameidx; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX auditusernameidx ON public.auditevent USING btree (username);


--
-- Name: ididtypeidx; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX ididtypeidx ON public.pseudonym USING btree (identifier, idtype);


--
-- Name: idpsnidx; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE UNIQUE INDEX idpsnidx ON public.pseudonym USING btree (identifier, pseudonym);


--
-- Name: metadataidx; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE UNIQUE INDEX metadataidx ON public.domain USING btree (name);


--
-- Name: person_firstname_lastname_admgender_dob_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_firstname_lastname_admgender_dob_uindex ON public.person USING btree (firstname, lastname, administrativegender, dateofbirth);


--
-- Name: person_firstname_lastname_admgender_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_firstname_lastname_admgender_uindex ON public.person USING btree (firstname, lastname, administrativegender);


--
-- Name: person_firstname_lastname_dob_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_firstname_lastname_dob_uindex ON public.person USING btree (firstname, lastname, dateofbirth);


--
-- Name: person_firstname_lastname_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_firstname_lastname_uindex ON public.person USING btree (firstname, lastname);


--
-- Name: person_firstname_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_firstname_uindex ON public.person USING btree (firstname);


--
-- Name: person_identifier_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_identifier_uindex ON public.person USING btree (identifier);


--
-- Name: person_lastname_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_lastname_uindex ON public.person USING btree (lastname);


--
-- Name: person_street_postalcode_city_country_uindex; Type: INDEX; Schema: public; Owner: trustdeck-manager
--

CREATE INDEX person_street_postalcode_city_country_uindex ON public.person USING btree (street, postalcode, city, country);


--
-- Name: domain domain_superdomainid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.domain
    ADD CONSTRAINT domain_superdomainid_fkey FOREIGN KEY (superdomainid) REFERENCES public.domain(id);


--
-- Name: person person_identifieralgorithm_fkey; Type: FK CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.person
    ADD CONSTRAINT person_identifieralgorithm_fkey FOREIGN KEY (identifieralgorithm) REFERENCES public.algorithm(id);


--
-- Name: pseudonym pseudonym_domainid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.pseudonym
    ADD CONSTRAINT pseudonym_domainid_fkey FOREIGN KEY (domainid) REFERENCES public.domain(id);


--
-- PostgreSQL database dump complete
--


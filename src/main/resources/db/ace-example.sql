--
-- PostgreSQL database dump
--

-- Dumped from database version 17.3 (Debian 17.3-3.pgdg120+1)
-- Dumped by pg_dump version 17.3 (Debian 17.3-3.pgdg120+1)

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
-- Name: auditevent id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.auditevent ALTER COLUMN id SET DEFAULT nextval('public.auditevent_id_seq'::regclass);


--
-- Name: domain id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.domain ALTER COLUMN id SET DEFAULT nextval('public.domain_id_seq'::regclass);


--
-- Name: pseudonym id; Type: DEFAULT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.pseudonym ALTER COLUMN id SET DEFAULT nextval('public.pseudonym_id_seq'::regclass);


--
-- Data for Name: auditevent; Type: TABLE DATA; Schema: public; Owner: trustdeck-manager
--



--
-- Data for Name: domain; Type: TABLE DATA; Schema: public; Owner: trustdeck-manager
--



--
-- Data for Name: pseudonym; Type: TABLE DATA; Schema: public; Owner: trustdeck-manager
--



--
-- Name: auditevent_id_seq; Type: SEQUENCE SET; Schema: public; Owner: trustdeck-manager
--

SELECT pg_catalog.setval('public.auditevent_id_seq', 1, false);


--
-- Name: domain_id_seq; Type: SEQUENCE SET; Schema: public; Owner: trustdeck-manager
--

SELECT pg_catalog.setval('public.domain_id_seq', 1, false);


--
-- Name: pseudonym_id_seq; Type: SEQUENCE SET; Schema: public; Owner: trustdeck-manager
--

SELECT pg_catalog.setval('public.pseudonym_id_seq', 1, false);


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
-- Name: domain domain_superdomainid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.domain
    ADD CONSTRAINT domain_superdomainid_fkey FOREIGN KEY (superdomainid) REFERENCES public.domain(id);


--
-- Name: pseudonym pseudonym_domainid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: trustdeck-manager
--

ALTER TABLE ONLY public.pseudonym
    ADD CONSTRAINT pseudonym_domainid_fkey FOREIGN KEY (domainid) REFERENCES public.domain(id);


--
-- PostgreSQL database dump complete
--


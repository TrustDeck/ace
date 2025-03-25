/*
 * Trust Deck Services
 * Copyright 2021-2025 Armin Müller & Eric Wündisch
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

package org.trustdeck.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.trustdeck.dto.DomainDTO;
import org.trustdeck.service.AssertWebRequestService;
import org.trustdeck.service.DomainOIDCService;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;

/**
 * This class offers tests to test only the domain endpoints.
 *
 * @author Armin Müller & Eric Wündisch
 */
@Slf4j
public class TestsDomainServiceIT extends AssertWebRequestService {

	/** OIDC service for managing OpenID Connect operations such as token retrieval and validation. */
    @Autowired
    private DomainOIDCService domainOidcService;
    
    /**
     * Test that tries to create a new domain with different given inputs
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("commonCreateDomainTest")
    public void commonCreateDomainTest() throws Exception {
        // A create request without the necessary domainName permission should work, the rest shouldn't
        String domainName = "WeitereStudie";

        // Check if common request works
        DomainDTO reducedDomainDto = new DomainDTO();
        reducedDomainDto.setName(domainName);
        reducedDomainDto.setPrefix("WS-");

        // Unauthorized tests for creating a domain
        this.assertBadRequestRequest("createDomainBadRequest", post("/api/pseudonymization/domain"), null, reducedDomainDto, "");
        this.assertUnauthorizedRequest("createDomainUnauth", post("/api/pseudonymization/domain"), null, reducedDomainDto, "SomeToken");

        MockHttpServletResponse response = this.assertCreatedRequest("createDomain", post("/api/pseudonymization/domain"), null, reducedDomainDto, this.getAccessToken());
        String content = response.getContentAsString();
        assertTrue(content.contains("201"));

        String domainNameComplete = "WeitereStudieComplete";
        DomainDTO completeDomainDto = new DomainDTO();
        completeDomainDto.setName(domainNameComplete);
        completeDomainDto.setPrefix("WS-");

        // Unauthorized tests for creating a domain
        this.assertBadRequestRequest("createDomainCompleteBadRequest", post("/api/pseudonymization/domain/complete"), null, completeDomainDto, "");
        this.assertUnauthorizedRequest("createDomainCompleteUnauth", post("/api/pseudonymization/domain/complete"), null, completeDomainDto, "SomeToken");

        response = this.assertCreatedRequest("createDomainComplete", post("/api/pseudonymization/domain/complete"), null, completeDomainDto, this.getAccessToken());
        content = response.getContentAsString();
        assertTrue(content.contains("201"));

        // These commands must fail because we don't have the correct permission to do anything on the newly created domain
        // TODO: create a domain in sql to test this
        /*
        Map<String, String> getParameterForbidden = new HashMap<>() {
        	private static final long serialVersionUID = -6132269345886096019L;
		{
            put("name", domainName);
        }};
        this.assertForbiddenRequest("commonGetDomainForbidden", get("/api/pseudonymization/domain"), getParameterForbidden, null, this.getAccessToken());
        this.assertForbiddenRequest("commonGetSaltForbidden", get("/api/pseudonymization/domains/" + domainName + "/salt"), getParameterForbidden, null, this.getAccessToken());

        Map<String, String> getParameterUpdateDomainForbidden = getParameterForbidden;
        getParameterUpdateDomainForbidden.put("recursive", "false");

        DomainDTO forbiddenDomainDto = new DomainDTO();

        this.assertForbiddenRequest("commonUpdateDomainForbidden", put("/api/pseudonymization/domain/complete"), getParameterUpdateDomainForbidden, forbiddenDomainDto, this.getAccessToken());

        Map<String, String> getParameterUpdateSaltForbidden = getParameterForbidden;
        getParameterUpdateSaltForbidden.put("salt", "something");
        this.assertForbiddenRequest("commonUpdateSaltForbidden", put("/api/pseudonymization/domains/" + domainName + "/salt"), getParameterUpdateSaltForbidden, null, this.getAccessToken());
        */
    }

    /**
     * Test that tries to trigger errors on domain endpoints for different status codes.
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("triggerFailuresOnDomainsTest")
    public void triggerFailuresOnDomainsTest() throws Exception {
        String domainName = "TestStudie-Labor";

        // Get domain without creating it first
        Map<String, String> getParameter = new HashMap<>() {
        	private static final long serialVersionUID = -8173402790582999935L;
		{
            put("name", domainName);
        }};
        
        domainOidcService.createDomainGroupsAndRolesAndJoin(domainName, "3dfb6717-3def-493b-a237-b7345fc42718");
        this.assertNotFoundRequest("getDomainNotFoundDomainName", get("/api/pseudonymization/domain"), getParameter, null, this.getAccessToken());

        // Get salt without creating a domain first
        this.assertNotFoundRequest("getSaltDomainNotFoundDomainName", get("/api/pseudonymization/domains/" + domainName + "/salt"), null, null, this.getAccessToken());

        // Update domain while domain is not yet created
        Map<String, String> updateParameter = new HashMap<>() {
        	private static final long serialVersionUID = 1994787103897759608L;
		{
            put("name", domainName);
            put("recursive", "false");
        }};
        // Needs an non empty object to trigger not found
        DomainDTO domainDTO = new DomainDTO();
        domainDTO.setDescription("Just Something");
        this.assertNotFoundRequest("updateDomainNotFoundDomainName", put("/api/pseudonymization/domain/complete"), updateParameter, domainDTO, this.getAccessToken());

        // Try updating the salt on you know ... a not yet created domain again :) (But needs a salt of length >= 32 to test it)
        Map<String, String> firstUpdateSaltParameter = new HashMap<>() {
        	private static final long serialVersionUID = -39113940687042576L;
		{
            put("salt", "somethingSensibleMightStandHere.OrNot.WhoKnows?");
        }};
        this.assertNotFoundRequest("firstUpdateSaltDomainNotFoundDomainName", put("/api/pseudonymization/domains/" + domainName + "/salt"), firstUpdateSaltParameter, null, this.getAccessToken());

        Map<String, String> secondUpdateSaltParameter = new HashMap<>() {
        	private static final long serialVersionUID = -4738784430090242117L;
		{
            put("salt", " ");
        }};
        this.assertBadRequestRequest("secondUpdateSaltDomainNotFoundDomainName", put("/api/pseudonymization/domains/" + domainName + "/salt"), secondUpdateSaltParameter, null, this.getAccessToken());

        Map<String, String> thirdUpdateSaltParameter = new HashMap<>() {
        	private static final long serialVersionUID = -7380059142268432102L;
		{
            put("salt", "");
        }};
        this.assertBadRequestRequest("thirdUpdateSaltDomainNotFoundDomainName", put("/api/pseudonymization/domains/" + domainName + "/salt"), thirdUpdateSaltParameter, null, this.getAccessToken());
        thirdUpdateSaltParameter.put("foo", "bar");
        this.assertBadRequestRequest("fourthUpdateSaltDomainNotFoundDomainName", put("/api/pseudonymization/domains/" + domainName + "/salt"), thirdUpdateSaltParameter, null, this.getAccessToken());

        // Create domain with unknown parentName
        Map<String, String> createParameter = new HashMap<>() {
        	private static final long serialVersionUID = 7067526794461193299L;
		{
            put("parentName", "Unknown-ParentName");
            put("prefix", "TS-L");
            put("name", domainName);
        }};
        this.assertBadRequestRequest("createDomainNotFoundParentName", post("/api/pseudonymization/domain"), createParameter, null, this.getAccessToken());

        // Delete domain with unknown parentName
        Map<String, String> deleteParameter = new HashMap<>() {
        	private static final long serialVersionUID = -6870663419604238488L;
		{
            put("name", domainName);
        }};
        this.assertNotFoundRequest("deleteDomainNotFoundParentName", delete("/api/pseudonymization/domain"), deleteParameter, null, this.getAccessToken());

    }

    /**
     * Test that simulates common change-actions by a user on domains
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("commonChangesOnDomainTest")
    public void commonChangesOnDomainTest() throws Exception {
        String domainName = "TestStudie";

        Map<String, String> getParameter = new HashMap<>() {private static final long serialVersionUID = 4462487992226896288L;
		{
            put("name", domainName);
        }};

        // Unauthorized tests for getting a domain
        this.assertBadRequestRequest("getDomainBadRequest", get("/api/pseudonymization/domain"), getParameter, null, "");
        this.assertUnauthorizedRequest("getDomainUnauth", get("/api/pseudonymization/domain"), getParameter, null, "SomeToken");

        MockHttpServletResponse response = this.assertOkRequest("getDomain", get("/api/pseudonymization/domain"), getParameter, null, this.getAccessToken());
        String content = response.getContentAsString();
        DomainDTO d = this.applySingleJsonContentToClass(content, DomainDTO.class);

        // Content must be a valid JSON and mappable
        assertNotNull(d);
        
        assertNull(d.getDescription());

        String newDescription = "das ist ein test2";
        d.setDescription(newDescription);
        this.domainUpdateHelperReduced(d, domainName, d);

        // All fields must be filled with something (at least an empty string) and available
        assertEquals("TestStudie", d.getName());
        assertEquals("TS-", d.getPrefix());
        assertEquals("2022-02-26T19:15:20.885853", d.getValidFrom().toString());
        assertFalse(d.getValidFromInherited());
        assertEquals("2052-02-19T19:15:20.885853", d.getValidTo().toString());
        assertFalse(d.getValidToInherited());
        assertTrue(d.getEnforceStartDateValidity());
        assertFalse(d.getEnforceStartDateValidityInherited());
        assertTrue(d.getEnforceEndDateValidity());
        assertFalse(d.getEnforceEndDateValidityInherited());
        assertEquals("MD5", d.getAlgorithm());
        assertFalse(d.getAlgorithmInherited());
        assertEquals(1, d.getConsecutiveValueCounter());
        assertFalse(d.getMultiplePsnAllowed());
        assertEquals(32, d.getPseudonymLength());
        assertFalse(d.getPseudonymLengthInherited());
        assertEquals("0", d.getPaddingCharacter().toString());
        assertFalse(d.getPaddingCharacterInherited());
        assertTrue(d.getAddCheckDigit());
        assertFalse(d.getAddCheckDigitInherited());
        assertFalse(d.getLengthIncludesCheckDigit());
        assertFalse(d.getLengthIncludesCheckDigitInherited());
        
        assertEquals("azMPTIQXJsept_4nDj5B1BXN83Bj_8VJ", d.getSalt());
        assertEquals(32, d.getSaltLength());

        // Change some of the domain's attributes

        //salt must be at least 8 chars long
        String newSalt = "foobar78";
        d.setSalt(newSalt);
        this.domainUpdateHelperComplete(d, domainName, d);

        newDescription = "das ist ein test";
        d.setDescription(newDescription);
        this.domainUpdateHelperComplete(d, domainName, d);

        String newPaddingChar = "1";
        d.setPaddingCharacter(newPaddingChar.charAt(0));
        this.domainUpdateHelperComplete(d, domainName, d);

        Integer newPsnLength = 16;
        d.setPseudonymLength(newPsnLength);
        this.domainUpdateHelperComplete(d, domainName, d);

        d.setValidFrom(d.getValidFrom().withNano(0).plusDays(10));
        this.domainUpdateHelperComplete(d, domainName, d);

        d.setValidTo(d.getValidTo().withNano(0).plusDays(10));
        this.domainUpdateHelperComplete(d, domainName, d);

        d.setEnforceStartDateValidity(false);
        this.domainUpdateHelperComplete(d, domainName, d);

        d.setEnforceStartDateValidity(true);
        this.domainUpdateHelperComplete(d, domainName, d);

        d.setEnforceEndDateValidity(false);
        this.domainUpdateHelperComplete(d, domainName, d);

        d.setEnforceEndDateValidity(true);
        this.domainUpdateHelperComplete(d, domainName, d);

        String newAlgo = "BLAKE3";
        d.setAlgorithm(newAlgo);
        this.domainUpdateHelperComplete(d, domainName, d);

        long newConsecVal = 10L;
        d.setConsecutiveValueCounter(newConsecVal);
        this.domainUpdateHelperComplete(d, domainName, d);

        this.assertIsDeletedDomain(domainName);
    }

    /**
     * Test that simulates common actions on the domain's salt endpoint
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("saltTest")
    public void saltTest() throws Exception {
        String domainName = "TestStudie";

        // Unauthorized tests for requesting the salt
        this.assertBadRequestRequest("getSaltBadRequest", get("/api/pseudonymization/domains/" + domainName + "/salt"), null, null, "");
        this.assertUnauthorizedRequest("getSaltUnauth", get("/api/pseudonymization/domains/" + domainName + "/salt"), null, null, "SomeToken");

        // Common get salt request
        MockHttpServletResponse response = this.assertOkRequest("getSalt", get("/api/pseudonymization/domains/" + domainName + "/salt"), null, null, this.getAccessToken());
        String content = response.getContentAsString();
        assertEquals(content, "{\"salt\":\"azMPTIQXJsept_4nDj5B1BXN83Bj_8VJ\"}");

        // Common update salt (salt needs to be at least 32 chars long)
        String newSalt = "foobarFoobarFoobarFoobarFoobarFoobar";
        Map<String, String> updateParameter = new HashMap<>() {private static final long serialVersionUID = 3657160170840501742L;

		{
            put("name", domainName);
            put("salt", newSalt);
        }};

        // Unauthorized tests for updating salt
        this.assertBadRequestRequest("updateSaltBadRequest", put("/api/pseudonymization/domains/" + domainName + "/salt"), updateParameter, null, "");
        this.assertUnauthorizedRequest("updateSaltUnauth", put("/api/pseudonymization/domains/" + domainName + "/salt"), updateParameter, null, "SomeToken");

        this.assertOkRequest("updateSalt", put("/api/pseudonymization/domains/" + domainName + "/salt"), updateParameter, null, this.getAccessToken());

        // Check if the salt was saved after the update
        response = this.assertOkRequest("getSalt", get("/api/pseudonymization/domains/" + domainName + "/salt"), null, null, this.getAccessToken());
        content = response.getContentAsString();
        assertEquals(content, "{\"salt\":\"foobarFoobarFoobarFoobarFoobarFoobar\"}");

        // Now delete the domain
        Map<String, String> getParameter = new HashMap<>() {private static final long serialVersionUID = -6076235129760176348L;

		{
            put("name", domainName);
        }};
        this.assertNoContent("deleteDomainForSaltTest", delete("/api/pseudonymization/domain"), getParameter, null, this.getAccessToken());

        // Getting the salt again must now lead to a "not found" status because the domain was deleted
        this.assertNotFoundRequest("notFoundSaltWhileDelete", get("/api/pseudonymization/domains" + domainName + "/salt"), null, null, this.getAccessToken());

    }

    /**
     * Test the endpoint for listing all domains.
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("listDomainHierarchyTest")
    public void listDomainHierarchyTest() throws Exception {
        this.assertEqualsListDomainHierarchyLength(1);

        String parentDomainName = "TestStudie";

        // Add domains as children
        DomainDTO firstDomainDto = new DomainDTO();
        firstDomainDto.setName("TestStudie-Labor-Analyse");
        firstDomainDto.setPrefix("TS-L");
        firstDomainDto.setSuperDomainName(parentDomainName);

        // Should have the permission on the domain
        this.assertCreatedRequest("addFirstDomainForListHierarchy", post("/api/pseudonymization/domain"), null, firstDomainDto, this.getAccessToken());

        // Check the length again
        this.assertEqualsListDomainHierarchyLength(2);

        DomainDTO secondDomainDto = new DomainDTO();
        secondDomainDto.setName("TestStudie-Paper");
        secondDomainDto.setPrefix("TS-P");
        secondDomainDto.setSuperDomainName(parentDomainName);

        // Should have the permission on the domain
        this.assertCreatedRequest("addSecondDomainForListHierarchy", post("/api/pseudonymization/domain"), null, secondDomainDto, this.getAccessToken());

        // Check the length again
        this.assertEqualsListDomainHierarchyLength(3);

        // Should NOT have the permission on the domain
        DomainDTO thirdDomainDto = new DomainDTO();
        thirdDomainDto.setName("No-Permission-Domain");
        thirdDomainDto.setPrefix("NoPe");
        thirdDomainDto.setSuperDomainName("TestStudie-Labor-Analyse");

        // Should NOT have the permission on the domain
        this.assertCreatedRequest("addThirdDomainForListHierarchy", post("/api/pseudonymization/domain"), null, thirdDomainDto, this.getAccessToken());

        // Check the length again
        List<DomainDTO> domains = this.assertEqualsListDomainHierarchyLength(4);

        // Check if the minimal domain-net makes sense here
        for (DomainDTO domain : domains) {

            switch (domain.getId()) {
                case 1:
                    assertEquals("TestStudie", domain.getName());
                    assertNull(domain.getSuperDomainID());
                    break;
                case 2:
                    assertEquals("TestStudie-Labor-Analyse", domain.getName());
                    assertEquals(1, domain.getSuperDomainID());
                    break;
                case 3:
                    assertEquals("TestStudie-Paper", domain.getName());
                    assertEquals(1, domain.getSuperDomainID());
                    break;
                case 4:
                    assertEquals("No-Permission-Domain", domain.getName());
                    assertEquals(2, domain.getSuperDomainID());
                    break;
                default:
                    throw new AssertionFailedError("Domain ID " + String.valueOf(domain.getId()) + " not found");
            }
        }
    }
    
    /**
     * Test that simulates common actions on the domain's getAttribute endpoint
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("getDomainAttributesTest")
    public void getDomainAttributesTest() throws Exception {
        String domainName = "TestStudie";

        // Unauthorized tests for requesting an attribute
        this.assertBadRequestRequest("getDomainAttributeBadRequest", get("/api/pseudonymization/domains/" + domainName + "/name"), null, null, "");
        this.assertUnauthorizedRequest("getDomainAttributeUnauth", get("/api/pseudonymization/domains/" + domainName + "/name"), null, null, "SomeToken");

        // Common get attribute requests
        MockHttpServletResponse response = this.assertOkRequest("getDomainID", get("/api/pseudonymization/domains/" + domainName + "/id"), null, null, this.getAccessToken());
        String content = response.getContentAsString();
        assertEquals(content, "{\"id\":1}");
        response = this.assertOkRequest("getDomainName", get("/api/pseudonymization/domains/" + domainName + "/name"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"name\":\"TestStudie\"}");
        response = this.assertOkRequest("getDomainPrefix", get("/api/pseudonymization/domains/" + domainName + "/prefix"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"prefix\":\"TS-\"}");
        response = this.assertOkRequest("getDomainValidfrom", get("/api/pseudonymization/domains/" + domainName + "/validfrom"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"validFrom\":\"2022-02-26T19:15:20.885853\"}");
        response = this.assertOkRequest("getDomainValidfrominherited", get("/api/pseudonymization/domains/" + domainName + "/validfrominherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"validFromInherited\":false}");
        response = this.assertOkRequest("getDomainValidto", get("/api/pseudonymization/domains/" + domainName + "/validto"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"validTo\":\"2052-02-19T19:15:20.885853\"}");
        response = this.assertOkRequest("getDomainValidtoinherited", get("/api/pseudonymization/domains/" + domainName + "/validtoinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"validToInherited\":false}");
        response = this.assertOkRequest("getDomainEnforcestartdatevalidity", get("/api/pseudonymization/domains/" + domainName + "/enforcestartdatevalidity"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"enforceStartDateValidity\":true}");
        response = this.assertOkRequest("getDomainEnforcestartdatevalidityinherited", get("/api/pseudonymization/domains/" + domainName + "/enforcestartdatevalidityinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"enforceStartDateValidityInherited\":false}");
        response = this.assertOkRequest("getDomainEnforceenddatevalidity", get("/api/pseudonymization/domains/" + domainName + "/enforceenddatevalidity"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"enforceEndDateValidity\":true}");
        response = this.assertOkRequest("getDomainEnforceenddatevalidityinherited", get("/api/pseudonymization/domains/" + domainName + "/enforceenddatevalidityinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"enforceEndDateValidityInherited\":false}");
        response = this.assertOkRequest("getDomainAlgorithm", get("/api/pseudonymization/domains/" + domainName + "/algorithm"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"algorithm\":\"MD5\"}");
        response = this.assertOkRequest("getDomainAlgorithminherited", get("/api/pseudonymization/domains/" + domainName + "/algorithminherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"algorithmInherited\":false}");
        response = this.assertOkRequest("getDomainAlphabet", get("/api/pseudonymization/domains/" + domainName + "/alphabet"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"alphabet\":\"ABCDEF0123456789\"}");
        response = this.assertOkRequest("getDomainAlphabetinherited", get("/api/pseudonymization/domains/" + domainName + "/alphabetinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"alphabetInherited\":false}");
        response = this.assertOkRequest("getDomainRandomalgorithmdesiredsize", get("/api/pseudonymization/domains/" + domainName + "/randomalgorithmdesiredsize"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"randomAlgorithmDesiredSize\":100000000}");
        response = this.assertOkRequest("getDomainRandomalgorithmdesiredsizeinherited", get("/api/pseudonymization/domains/" + domainName + "/randomalgorithmdesiredsizeinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"randomAlgorithmDesiredSizeInherited\":false}");
        response = this.assertOkRequest("getDomainRandomalgorithmdesiredsuccessprobability", get("/api/pseudonymization/domains/" + domainName + "/randomalgorithmdesiredsuccessprobability"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"randomAlgorithmDesiredSuccessProbability\":0.99999998}");
        response = this.assertOkRequest("getDomainRandomalgorithmdesiredsuccessprobabilityinherited", get("/api/pseudonymization/domains/" + domainName + "/randomalgorithmdesiredsuccessprobabilityinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"randomAlgorithmDesiredSuccessProbabilityInherited\":false}");
        response = this.assertOkRequest("getDomainMultiplepsnallowed", get("/api/pseudonymization/domains/" + domainName + "/multiplepsnallowed"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"multiplePsnAllowed\":false}");
        response = this.assertOkRequest("getDomainMultiplepsnallowedinherited", get("/api/pseudonymization/domains/" + domainName + "/multiplepsnallowedinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"multiplePsnAllowedInherited\":false}");
        response = this.assertOkRequest("getDomainConsecutivevaluecounter", get("/api/pseudonymization/domains/" + domainName + "/consecutivevaluecounter"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"consecutiveValueCounter\":1}");
        response = this.assertOkRequest("getDomainPseudonymlength", get("/api/pseudonymization/domains/" + domainName + "/pseudonymlength"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"pseudonymLength\":32}");
        response = this.assertOkRequest("getDomainPseudonymlengthinherited", get("/api/pseudonymization/domains/" + domainName + "/pseudonymlengthinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"pseudonymLengthInherited\":false}");
        response = this.assertOkRequest("getDomainPaddingcharacter", get("/api/pseudonymization/domains/" + domainName + "/paddingcharacter"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"paddingCharacter\":\"0\"}");
        response = this.assertOkRequest("getDomainPaddingcharacterinherited", get("/api/pseudonymization/domains/" + domainName + "/paddingcharacterinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"paddingCharacterInherited\":false}");
        response = this.assertOkRequest("getAddCheckDigit", get("/api/pseudonymization/domains/" + domainName + "/addcheckdigit"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"addCheckDigit\":true}");
        response = this.assertOkRequest("getAddCheckDigitInherited", get("/api/pseudonymization/domains/" + domainName + "/addcheckdigitinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"addCheckDigitInherited\":false}");
        response = this.assertOkRequest("getLengthIncludesCheckDigit", get("/api/pseudonymization/domains/" + domainName + "/lengthincludescheckdigit"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"lengthIncludesCheckDigit\":false}");
        response = this.assertOkRequest("getLengthIncludesCheckDigitInherited", get("/api/pseudonymization/domains/" + domainName + "/lengthincludescheckdigitinherited"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"lengthIncludesCheckDigitInherited\":false}");
        response = this.assertOkRequest("getDomainSalt", get("/api/pseudonymization/domains/" + domainName + "/salt"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"salt\":\"azMPTIQXJsept_4nDj5B1BXN83Bj_8VJ\"}");
        response = this.assertOkRequest("getDomainSaltlength", get("/api/pseudonymization/domains/" + domainName + "/saltlength"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{\"saltLength\":32}");
        response = this.assertOkRequest("getDomainDescription", get("/api/pseudonymization/domains/" + domainName + "/description"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{}");
        response = this.assertOkRequest("getDomainSuperdomainid", get("/api/pseudonymization/domains/" + domainName + "/superdomainid"), null, null, this.getAccessToken());
        assertEquals(response.getContentAsString(), "{}");

        // Now delete the domain
        Map<String, String> getParameter = new HashMap<>() {
        	private static final long serialVersionUID = -6076235129760176348L;
		{
            put("name", domainName);
        }};
        this.assertNoContent("deleteDomainForSaltTest", delete("/api/pseudonymization/domain"), getParameter, null, this.getAccessToken());

        // Getting a attribute again should now lead to a "not found" status since the domain was deleted
        this.assertNotFoundRequest("notFoundAttributeAfterDeletion", get("/api/pseudonymization/domains" + domainName + "/name"), null, null, this.getAccessToken());
    }
}
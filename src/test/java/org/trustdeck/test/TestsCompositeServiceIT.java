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
import org.springframework.mock.web.MockHttpServletResponse;
import org.trustdeck.dto.DomainDTO;
import org.trustdeck.dto.RecordDTO;
import org.trustdeck.jooq.generated.tables.pojos.Pseudonym;
import org.trustdeck.service.AssertWebRequestService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;

/**
 * This class offers tests that only work in a combination of updating or deleting different objects first.
 *
 * @author Armin Müller & Eric Wündisch
 */
public class TestsCompositeServiceIT extends AssertWebRequestService {

    /**
     * This method is a helper and creates a simple domain network.
     *
     * @param parentDomainName the parent's domain name as string for its child
     * @param childName        the name of the new child as string
     * @param childNameOfChild the name of the child for the first created child
     * @throws Exception forwards any internally thrown exceptions
     */
    public void createNet(String parentDomainName, String childName, String childNameOfChild) throws Exception {
        this.assertEqualsListDomainHierarchyLength(1);

        // Should have the permission on domain
        this.createDomainHelper(childName, "TS-L", parentDomainName);
        
        // Check length
        this.assertEqualsListDomainHierarchyLength(2);

        // Should have the permission on domain
        this.createDomainHelper(childNameOfChild, "TS-P", childName);
        
        // Check length again
        this.assertEqualsListDomainHierarchyLength(3);
    }

    /**
     * Small helper to quickly create a domain
     *
     * @param name       the name of the domain
     * @param prefix     the prefix of the domain
     * @param parentName the parent domain's name if given, otherwise set it to null
     * @throws Exception
     */
    public void createDomainHelper(String name, String prefix, String parentName) throws Exception {
        DomainDTO d = new DomainDTO();
        d.setName(name);
        d.setPrefix(prefix);
        d.setSuperDomainName(parentName);
        this.assertCreatedRequest("addDomain", post("/api/pseudonymization/domain"), null, d, this.getAccessToken());
    }

    /**
     * Helper method to create a record.
     *
     * @param id the identifier of the record
     * @param idType the identifier's type of the record
     * @param domainName the name of the domain where the record should be in
     * @throws Exception forwards all thrown exceptions
     */
    public RecordDTO createRecordHelper(String id, String idType, String domainName) throws Exception {
        RecordDTO createRecord = new RecordDTO();
        createRecord.setId(id);
        createRecord.setIdType(idType);

        MockHttpServletResponse response = this.assertCreatedRequest("createRecord", post("/api/pseudonymization/domains/" + domainName + "/pseudonym"), null, createRecord, this.getAccessToken());
        String content = response.getContentAsString();

        List<RecordDTO> r1l = this.mapJsonObjectsInStringToList(content, RecordDTO.class);
        RecordDTO r = r1l.get(0);

        RecordDTO nextRecord = new RecordDTO();
        nextRecord.setId(r.getPsn());
        nextRecord.setIdType(r.getDomainName());

        return nextRecord;
    }

    @SuppressWarnings("unused")
	@Test
    @DisplayName("linkPathTest")
    public void linkPathTest() throws Exception {

        // Create the domain network
        String rootDomainName = "TestStudie";
        String rootDomainPrefix = "TS-";

        String childOneName = "TestStudie-MRT";
        String childOnePrefix = "TS-MRT-";

        String childTwoName = "TestStudie-Paper";
        String childTwoPrefix = "TS-P-";

        String childThreeName = "TestStudie-Labor-Analyse";
        String childThreePrefix = "TS-L-";

        //this.createDomainHelper(rootDomainName, rootDomainPrefix, null);
        this.createDomainHelper(childOneName, childOnePrefix, rootDomainName);
        this.createDomainHelper(childTwoName, childTwoPrefix, rootDomainName);
        this.createDomainHelper(childThreeName, childThreePrefix, childOneName);

        // Create pseudonyms inside the network

        // Regular creation without issues
        RecordDTO a1 = this.createRecordHelper("1234356", "MP-ID", rootDomainName);
        RecordDTO a2 = this.createRecordHelper(a1.getId(), a1.getIdType(), childOneName);
        RecordDTO a3 = this.createRecordHelper(a1.getId(), a1.getIdType(), childTwoName);
        RecordDTO a4 = this.createRecordHelper(a2.getId(), a2.getIdType(), childThreeName);

        // Regular creation without issues
        RecordDTO b1 = this.createRecordHelper("1234357", "MP-ID", rootDomainName);
        RecordDTO b2 = this.createRecordHelper(b1.getId(), b1.getIdType(), childOneName);
        RecordDTO b3 = this.createRecordHelper(b1.getId(), b1.getIdType(), childTwoName);
        RecordDTO b4 = this.createRecordHelper(b2.getId(), b2.getIdType(), childThreeName);

        // Regular creation without issues
        RecordDTO c1 = this.createRecordHelper("1234357", "ANY-ID", rootDomainName);
        RecordDTO c2 = this.createRecordHelper(c1.getId(), c1.getIdType(), childOneName);
        RecordDTO c3 = this.createRecordHelper(c1.getId(), c1.getIdType(), childTwoName);
        RecordDTO c4 = this.createRecordHelper(c2.getId(), c2.getIdType(), childThreeName);

        //Regular creation but without childThreeName
        RecordDTO d1 = this.createRecordHelper("1234358", "MP-ID", rootDomainName);
        RecordDTO d2 = this.createRecordHelper(d1.getId(), d1.getIdType(), childOneName);
        RecordDTO d3 = this.createRecordHelper(d1.getId(), d1.getIdType(), childTwoName);

        // Set query parameter
        Map<String, String> getParameter = new HashMap<>() {
            private static final long serialVersionUID = 3844434370077016386L;
            {
                put("sourceDomain", childTwoName);
                put("targetDomain", childThreeName);
                //id is the next psn from helper method...
                put("sourcePsn", b3.getId());
            }
        };

        this.assertOkRequest("getSomething", get("/api/pseudonymization/experimental/domains/hierarchy"), getParameter, null, this.getAccessToken());
    }

    /**
     * Tests that building and recursive deleting on a domain-net works.
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("domainNetRecursionTest")
    public void domainNetRecursionTest() throws Exception {
        String parentDomainName = "TestStudie";
        String child = "TestStudie-Labor-Analyse";
        String childOfChild = "TestStudie-Paper";

        this.assertEqualsListDomainHierarchyLength(1);

        // Create domain network for "TestStudie", "TestStudie-Labor-Analyse" and "TestStudie-Paper"
        this.createNet(parentDomainName, child, childOfChild);

        // Recursive deletion of "child" must also lead to the deletion of "childOfChild"
        Map<String, String> deleteParameter = new HashMap<>() {
            private static final long serialVersionUID = -8572862716962724488L;

            {
                put("name", child);
                put("recursive", "true");
            }
        };

        this.obtainNewAccessToken("test", "test");

        // Note: Recursive deletion of "child" must also lead to the deletion of the respective domain groups in the OIDC service
        this.assertNoContent("deleteDomain", delete("/api/pseudonymization/domain"), deleteParameter, null, this.getAccessToken());

        // Check that recursive deleting worked. Length must be 1.
        this.assertEqualsListDomainHierarchyLength(1);

        // Creating the net again must lead to a different domainID.
        this.createNet(parentDomainName, child, childOfChild);

        MockHttpServletResponse response = this.assertOkRequest("listDomainHierarchy", get("/api/pseudonymization/experimental/domains/hierarchy"), null, null, this.getAccessToken());
        String content = response.getContentAsString();
        List<DomainDTO> domains = this.mapJsonObjectsInStringToList(content, DomainDTO.class);

        // Then check net: i.e. the ID must be increased
        for (DomainDTO domain : domains) {
            switch (domain.getId()) {
                case 1:
                    assertEquals("TestStudie", domain.getName());
                    assertNull(domain.getSuperDomainID());
                    break;
                case 4:
                    assertEquals("TestStudie-Labor-Analyse", domain.getName());
                    assertEquals(1, domain.getSuperDomainID());
                    break;
                case 5:
                    assertEquals("TestStudie-Paper", domain.getName());
                    assertEquals(4, domain.getSuperDomainID());
                    break;
                default:
                    throw new AssertionFailedError("Domain ID " + domain.getId() + " not found");
            }
        }

        String assertIdType = "EINE-ID";
        String firstId = "1234356";
        String secondId = "1234357";

        // Create a record
        RecordDTO recordCreate = new RecordDTO();
        recordCreate.setId(firstId);
        recordCreate.setIdType(assertIdType);

        // Inserting the same record in two different domains (that have a relation between each other) must work.
        this.assertCreatedRequest("createNewRecord", post("/api/pseudonymization/domains/" + child + "/pseudonym"), null, recordCreate, this.getAccessToken());
        this.assertCreatedRequest("createNewRecord", post("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), null, recordCreate, this.getAccessToken());

        RecordDTO anotherCreateRecord = new RecordDTO();
        anotherCreateRecord.setId(secondId);
        anotherCreateRecord.setIdType(assertIdType);

        this.assertCreatedRequest("createNewRecord", post("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), null, anotherCreateRecord, this.getAccessToken());

        // Check if the records are available
        Map<String, String> getParameter = new HashMap<>() {
            private static final long serialVersionUID = -6352351748269439647L;

            {
                put("id", firstId);
                put("idType", assertIdType);
            }
        };

        response = this.assertOkRequest("readRecord", get("/api/pseudonymization/domains/" + child + "/pseudonym"), getParameter, null, this.getAccessToken());
        content = response.getContentAsString();

        List<RecordDTO> r1l = this.mapJsonObjectsInStringToList(content, RecordDTO.class);
        RecordDTO r1 = r1l.get(0);

        response = this.assertOkRequest("readRecord", get("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), getParameter, null, this.getAccessToken());
        content = response.getContentAsString();
        List<RecordDTO> r2l = this.mapJsonObjectsInStringToList(content, RecordDTO.class);
        RecordDTO r2 = r2l.get(0);

        assertNotNull(r1.getPsn());
        assertNotNull(r2.getPsn());

        assertEquals(firstId, r1.getId());
        assertEquals(firstId, r2.getId());

        assertEquals(assertIdType, r1.getIdType());
        assertEquals(assertIdType, r2.getIdType());

        // Because of a different salt this musn't be equal.
        assertNotEquals(r1.getPsn(), r2.getPsn());

        Map<String, String> anotherGetParameter = new HashMap<>() {
            private static final long serialVersionUID = 2912185610891383926L;

            {
                put("id", secondId);
                put("idType", assertIdType);
            }
        };

        response = this.assertOkRequest("readRecord", get("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), anotherGetParameter, null, this.getAccessToken());
        content = response.getContentAsString();

        List<RecordDTO> r3l = this.mapJsonObjectsInStringToList(content, RecordDTO.class);
        RecordDTO r3 = r3l.get(0);

        assertNotNull(r3.getPsn());
        assertEquals(secondId, r3.getId());
        assertEquals(assertIdType, r3.getIdType());
        // At this point all pseudonyms are saved correctly

        // Deleting the child recursively again must lead to the deletion of the records.
        this.assertNoContent("deleteDomain", delete("/api/pseudonymization/domain"), deleteParameter, null, this.getAccessToken());

        // Check if recursive deleting worked. Length must be 1.
        this.assertEqualsListDomainHierarchyLength(1);
        this.assertForbiddenRequest("readRecord", get("/api/pseudonymization/domains/" + child + "/pseudonym"), getParameter, null, this.getAccessToken());
        this.assertForbiddenRequest("readRecord", get("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), getParameter, null, this.getAccessToken());
        this.assertForbiddenRequest("readRecord", get("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), anotherGetParameter, null, this.getAccessToken());

        // Create the domain-net again
        this.createNet(parentDomainName, child, childOfChild);

        // Check that re-creating the domain doesn't restore the records formerly stored in it. Must lead to a not found.
        this.assertNotFoundRequest("readRecord", get("/api/pseudonymization/domains/" + child + "/pseudonym"), getParameter, null, this.getAccessToken());
        this.assertNotFoundRequest("readRecord", get("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), getParameter, null, this.getAccessToken());
        this.assertNotFoundRequest("readRecord", get("/api/pseudonymization/domains/" + childOfChild + "/pseudonym"), anotherGetParameter, null, this.getAccessToken());
    }

    /**
     * Tests if a domain properly handles records in regard to its valid to/from dates.
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("dateValidationOnDomainsAndRecordsTest")
    public void dateValidationOnDomainsAndRecordsTest() throws Exception {
        String domainName = "TestStudie";
        String assertId = "1234356";
        String assertIdType = "EINE-ID";

        // Ensure that the "TestStudie" is available
        Map<String, String> getParameter = new HashMap<>() {
            private static final long serialVersionUID = 4335696761958453390L;

            {
                put("name", domainName);
            }
        };

        MockHttpServletResponse response = this.assertOkRequest("getDomain", get("/api/pseudonymization/domain"), getParameter, null, this.getAccessToken());
        String content = response.getContentAsString();
        DomainDTO d = this.applySingleJsonContentToClass(content, DomainDTO.class);

        // Change some dates on the current domain which makes absolutely no sense but is needed for the test
        d.setValidFrom(LocalDateTime.now().withNano(0).minusDays(20));
        this.domainUpdateHelperComplete(d, domainName, d);

        d.setValidTo(LocalDateTime.now().withNano(0).minusDays(10));
        this.domainUpdateHelperComplete(d, domainName, d);

        // Create a record
        RecordDTO recordCreate = new RecordDTO();
        recordCreate.setId(assertId);
        recordCreate.setIdType(assertIdType);

        this.assertUnprocessableEntity("createNewRecord", post("/api/pseudonymization/domains/" + domainName + "/pseudonym"), null, recordCreate, this.getAccessToken());

        Pseudonym pseudonym1 = new Pseudonym();
        pseudonym1.setIdentifier("10000008913");
        pseudonym1.setIdtype("ANY-ID");
        pseudonym1.setValidfrominherited(true);
        pseudonym1.setValidtoinherited(true);

        Pseudonym pseudonym2 = new Pseudonym();
        pseudonym2.setIdentifier("10000008914");
        pseudonym2.setIdtype("ANY-ID");
        pseudonym2.setValidfrominherited(true);
        pseudonym2.setValidtoinherited(true);

        List<RecordDTO> recordDtoList = new ArrayList<>();
        recordDtoList.add(new RecordDTO().assignPojoValues(pseudonym1));
        recordDtoList.add(new RecordDTO().assignPojoValues(pseudonym2));
        this.assertUnprocessableEntity("createNewRecordBatch", post("/api/pseudonymization/domains/" + domainName + "/pseudonyms"), null, recordDtoList, this.getAccessToken());
    }

    /**
     * Tests the user-friendly validity time feature.
     *
     * @throws Exception forwards any internally thrown exceptions
     */
    @Test
    @DisplayName("userFriendlyValidityTimeTest")
    public void userFriendlyValidityTimeTest() throws Exception {
        // Prepare
        String parentDomainName = "TestStudie";
        String child = "TestStudie-Labor-Analyse";
        String childOfChild = "TestStudie-Paper";

        // Create domain network for "TestStudie", "TestStudie-Labor" and "TestStudie-Paper"
        this.createNet(parentDomainName, child, childOfChild);

        // Tests
        // Create a domain with a user-friendly validity time string
        DomainDTO domainDTO = new DomainDTO();
        domainDTO.setName("valTimeTest");
        domainDTO.setPrefix("VTT-");
        domainDTO.setValidFrom(LocalDateTime.parse("2023-01-01T00:00:00"));
        domainDTO.setValidityTime("1 year");

        this.assertCreatedRequest("createDomain", post("/api/pseudonymization/domain"), null, domainDTO, this.getAccessToken());
        /*Map<String, String> getP = new HashMap<>() {
        	private static final long serialVersionUID = -2537459720418280492L;
		{
            put("name", "valTimeTest");
        }};
        MockHttpServletResponse response = this.assertOkRequest("getDomain", get("/api/pseudonymization/domain/"), getP, null, this.getAccessToken());
        DomainDTO d = this.applySingleJsonContentToClass(response.getContentAsString(), DomainDTO.class);
        assertEquals(domainDto.getValidFrom().plusSeconds(365*86400L), d.getValidTo());*/

        // Create a record with a user-friendly validity time string
        RecordDTO record = new RecordDTO();
        record.setId("testIdentifier");
        record.setIdType("testType");
        record.setValidFrom(LocalDateTime.parse("2023-01-01T12:00:00"));
        record.setValidityTime("1d");

        MockHttpServletResponse response = this.assertCreatedRequest("createRecord", post("/api/pseudonymization/domains/" + parentDomainName + "/pseudonym"), null, record, this.getAccessToken());

        List<RecordDTO> r1l = this.mapJsonObjectsInStringToList(response.getContentAsString(), RecordDTO.class);
        RecordDTO r = r1l.get(0);

        assertEquals(record.getValidFrom().plusSeconds(86400L), r.getValidTo());

        // Update a record with a user-friendly validity time string via identifier
        Map<String, String> updateViaIdentifierParams = new HashMap<>() {
            private static final long serialVersionUID = -2537459720418280492L;

            {
                put("id", "testIdentifier");
                put("idType", "testType");
            }
        };
        RecordDTO updateViaIdentifier = new RecordDTO();
        updateViaIdentifier.setValidityTime("8 w");

        response = this.assertOkRequest("updateRecordViaIdentifier", put("/api/pseudonymization/domains/" + parentDomainName + "/pseudonym"), updateViaIdentifierParams, updateViaIdentifier, this.getAccessToken());
        RecordDTO updatedViaIdentifier = this.applySingleJsonContentToClass(response.getContentAsString(), RecordDTO.class);
        assertEquals(record.getValidFrom().plusSeconds(8 * 7 * 86400L), updatedViaIdentifier.getValidTo());

        // Update a record with a user-friendly validity time string via psn
        Map<String, String> updateViaPsnParams = new HashMap<>() {
            private static final long serialVersionUID = 3409656673301886926L;

            {
                put("psn", r.getPsn());
            }
        };
        RecordDTO updateViaPsn = new RecordDTO();
        updateViaPsn.setValidityTime("10hours");

        response = this.assertOkRequest("updateRecordViaPsnHours", put("/api/pseudonymization/domains/" + parentDomainName + "/pseudonym"), updateViaPsnParams, updateViaPsn, this.getAccessToken());
        RecordDTO updatedViaPsn = this.applySingleJsonContentToClass(response.getContentAsString(), RecordDTO.class);
        assertEquals(record.getValidFrom().plusSeconds(10 * 3600L), updatedViaPsn.getValidTo());

        // Update using minutes
        RecordDTO updateMinutes = new RecordDTO();
        updateMinutes.setValidityTime("12 minutes");

        response = this.assertOkRequest("updateRecordViaPsnMinutes", put("/api/pseudonymization/domains/" + parentDomainName + "/pseudonym"), updateViaPsnParams, updateMinutes, this.getAccessToken());
        RecordDTO updatedMinutes = this.applySingleJsonContentToClass(response.getContentAsString(), RecordDTO.class);
        assertEquals(record.getValidFrom().plusSeconds(12 * 60L), updatedMinutes.getValidTo());

        // Update using seconds
        RecordDTO updateSeconds = new RecordDTO();
        updateSeconds.setValidityTime("53secs");

        response = this.assertOkRequest("updateRecordViaPsnSeconds", put("/api/pseudonymization/domains/" + parentDomainName + "/pseudonym"), updateViaPsnParams, updateSeconds, this.getAccessToken());
        RecordDTO updatedSeconds = this.applySingleJsonContentToClass(response.getContentAsString(), RecordDTO.class);
        assertEquals(record.getValidFrom().plusSeconds(53L), updatedSeconds.getValidTo());
    }
}

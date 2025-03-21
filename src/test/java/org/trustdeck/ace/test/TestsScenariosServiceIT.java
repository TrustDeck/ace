/*
 * Trust Deck Services
 * Copyright 2021-2024 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.trustdeck.ace.model.dto.DomainDto;
import org.trustdeck.ace.service.AssertWebRequestService;

import java.time.LocalDateTime;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;

/**
 * Testing complete scenarios.
 * 
 * @author Eric Wündisch & Armin Müller
 */
public class TestsScenariosServiceIT extends AssertWebRequestService {

    @Test
    @DisplayName("createScenarioNetTest")
    public void createScenarioNetTest() throws Exception {

        //create a complete new tree <- TAKE care the user does not have permissions to read this tree after creating

        //Create the root Domain
        DomainDto rootDomainDto = new DomainDto();
        rootDomainDto.setName("ProjectX");
        rootDomainDto.setPrefix("PX-");
        LocalDateTime validFromTime = LocalDateTime.now().withNano(0).minusMonths(2);
        rootDomainDto.setValidFrom(validFromTime);
        LocalDateTime validToTime = validFromTime.plusYears(20);
        rootDomainDto.setValidTo(validToTime);
        rootDomainDto.setEnforceEndDateValidityInherited(true);
        rootDomainDto.setEnforceEndDateValidity(true);
        rootDomainDto.setEnforceStartDateValidityInherited(true);
        rootDomainDto.setEnforceStartDateValidity(true);
        rootDomainDto.setAlgorithmInherited(true);
        rootDomainDto.setAlgorithm("MD5");
        rootDomainDto.setPseudonymLength(16);
        rootDomainDto.setDescription("That is the beginning of a long journey");
        rootDomainDto.setSuperDomainName(null);

        //we make the first request as a complete request and the other with reduce view
        this.assertCreatedRequest("createScenarioRootDomainComplete", post("/api/pseudonymization/domain/complete"), null, rootDomainDto, this.getAccessToken());

        //create the first child
        DomainDto childOneDomainDto = new DomainDto();
        childOneDomainDto.setName("ProjectX-Labor");
        childOneDomainDto.setPrefix("PX-L-");
        childOneDomainDto.setSuperDomainName("ProjectX");
        this.assertCreatedRequest("createScenarioChildOneDomainComplete", post("/api/pseudonymization/domain"), null, childOneDomainDto, this.getAccessToken());

        //create the first child
        DomainDto childTwoDomainDto = new DomainDto();
        childTwoDomainDto.setName("ProjectX-Paper");
        childTwoDomainDto.setPrefix("PX-P-");
        childTwoDomainDto.setSuperDomainName("ProjectX");
        this.assertCreatedRequest("createScenarioChildTwoDomainComplete", post("/api/pseudonymization/domain"), null, childTwoDomainDto, this.getAccessToken());

        //create the first child
        DomainDto childThreeDomainDto = new DomainDto();
        childThreeDomainDto.setName("ProjectX-MRT");
        childThreeDomainDto.setPrefix("PX-MRT-");
        childThreeDomainDto.setSuperDomainName("ProjectX");
        this.assertCreatedRequest("createScenarioChildThreeDomainComplete", post("/api/pseudonymization/domain"), null, childThreeDomainDto, this.getAccessToken());

        //create the first child
        DomainDto childOfchildTwoDomainDto = new DomainDto();
        childOfchildTwoDomainDto.setName("ProjectX-PaperXY");
        childOfchildTwoDomainDto.setPrefix("PX-PXY-");
        childOfchildTwoDomainDto.setSuperDomainName("ProjectX-Paper");
        this.assertCreatedRequest("createScenarioChildOfchildTwoDomainComplete", post("/api/pseudonymization/domain"), null, childOfchildTwoDomainDto, this.getAccessToken());

    }

    @Test
    @DisplayName("mergePseudonymsTest")
    public void mergePseudonymsTest() throws Exception {
    	/**
        String domainName = "TestStudie";

        RecordDto createFirstRecordDto = new RecordDto();
        createFirstRecordDto.setId("100");
        createFirstRecordDto.setIdType("ANY-ID");

        MockHttpServletResponse response = this.assertCreatedRequest("scenarioCreateFirstRecord", post("/api/pseudonymization/domain/" + domainName + "/pseudonym"), null, createFirstRecordDto, this.getAccessToken());
        String  content = response.getContentAsString();
        RecordDto r1 = this.applySingleJsonContentToClass(content, RecordDto.class);

        RecordDto createSecondRecordDto = new RecordDto();
        createSecondRecordDto.setId("200");
        createSecondRecordDto.setIdType("ANY-ID");

        response =  this.assertCreatedRequest("scenarioCreateSecondRecord", post("/api/pseudonymization/domain/" + domainName + "/pseudonym"), null, createSecondRecordDto, this.getAccessToken());
        content = response.getContentAsString();
        RecordDto r2 = this.applySingleJsonContentToClass(content, RecordDto.class);
		*/
    	
        /*
        //having the same id and idtype in domain does not work
        //keep all pseudonym but link to same id
        Map<String, String> updateParameter = new HashMap<>() {
            private static final long serialVersionUID = -8328746806921168250L;

            {
                put("psn", r2.getPsn());
            }
        };

        RecordDto updateRecordDto = new RecordDto();
        updateRecordDto.setId("100");
        updateRecordDto.setIdType("ANY-ID");
        this.assertOkRequest("updateRecordCompleteByPseudonym", put("/api/pseudonymization/domain/" + domainName + "/pseudonym/complete"), updateParameter, updateRecordDto, this.getAccessToken());
        */

        /*
        //having different id and idtype but same pseudonym works partial you can update but not read with pseudonym. you must read with id.
        Map<String, String> updateParameter = new HashMap<>() {
            private static final long serialVersionUID = -8328746806921168250L;

            {
                put("id", "200");
                put("idType", "ANY-ID");
            }
        };

        RecordDto updateRecordDto = new RecordDto();
        updateRecordDto.setPsn(r1.getPsn());

        this.assertOkRequest("scenarioMergePseudonym", put("/api/pseudonymization/domain/" + domainName + "/pseudonym/complete"), updateParameter, updateRecordDto, this.getAccessToken());


        Map<String, String> getParameter = new HashMap<>() {
            private static final long serialVersionUID = 6408752344331371975L;

            {
                put("psn", r1.getPsn());
            }
        };

        response = this.assertOkRequest("scenarioReadMultiple", get("/api/pseudonymization/domain/" + domainName + "/pseudonym"), getParameter, null, this.getAccessToken());
        content = response.getContentAsString();
        */
    }
}

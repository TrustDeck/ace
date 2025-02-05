/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2022-2025 Armin Müller & Eric Wündisch
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

package org.trustdeck.ace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.WebApplicationContext;
import org.trustdeck.ace.model.dto.DomainDto;
import org.trustdeck.ace.model.dto.RecordDto;
import org.trustdeck.ace.utils.Assertion;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This class offers tests for testing the REST endpoints.
 *
 * @author Armin Müller & Eric Wündisch
 */
@ExtendWith({RestDocumentationExtension.class, SpringExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AssertWebRequestService {

    /** Mocks a Model-View-Controller application. */
    private MockMvc mockMvc;

    /** The temporarily stored keycloak access token. */
    private String accessToken;

    /** Represents the environment in which the current application is running. */
    @Autowired
    private Environment env;

    /**
     * Resets the test environment and sets up the mock objects.
     *
     * @param webApplicationContext the web application context
     * @param restDocumentation the context of the REST documentation
     * @throws Exception forwards any internally thrown exceptions
     */
    @BeforeEach
    public void setUp(WebApplicationContext webApplicationContext,
                      RestDocumentationContextProvider restDocumentation) throws Exception {

        if (!resetTestEnvironment()) {
            log.error("Resetting the test environment failed.");
            throw new TestInstantiationException("Resetting the test environment failed.");
        }

        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(documentationConfiguration(restDocumentation))
                .apply(springSecurity())
                .build();

        log.debug("Obtaining a token...");
        this.obtainNewAccessToken("test", "test");

        log.debug("Ready to test.");
    }

    /**
     * Helper method to reset the test data in ACE#s database as well as in Keycloak
     * @return {@code true} when resetting the databases was successful, {@code false} otherwise
     */
    private boolean resetTestEnvironment() {
        try {
            // Connect to Postgres and drop the keycloak database
            try (Connection conn = getPostgresConnection()) {
                conn.createStatement().execute("DROP DATABASE IF EXISTS keycloak WITH (FORCE);");
            }

            // Recreate the Keycloak database
            try (Connection conn = getPostgresConnection()) {
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/keycloak-example.sql"));
            }

            // Restart the keycloak docker container
            DockerClient dockerClient = DockerClientBuilder.getInstance().build();
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            String containerID = "";
            for (Container container : containers) {
                // Docker prepends a "/" to container names
                if (Arrays.asList(container.getNames()).contains("/ace-keycloak")) {
                    containerID = container.getId();
                    dockerClient.restartContainerCmd(containerID).exec();
                }
            }

            // Wait for the container to come back up
            long start = System.currentTimeMillis();
            InspectContainerResponse containerInfo;
            while (true) {
                // Get the container's current health info
                containerInfo = dockerClient.inspectContainerCmd(containerID).exec();
                HealthState health = containerInfo.getState().getHealth();
                if (health != null && health.getStatus().equals("healthy")) {
                    // The container is back online
                    break;
                } else if (health != null && health.getStatus().equals("starting")) {
                    // Not yet back online
                    if (System.currentTimeMillis() - start >= 10000) {
                        // Waiting too long for the container to come back online
                        return false;
                    } else {
                        Thread.sleep(1000);
                    }
                } else if (health != null && health.getStatus().equals("unhealthy")) {
                    // Restart failed
                    return false;
                }
            }

            // Remove and recreate ACE's database:
            try (Connection conn = getPostgresConnection()) {
                conn.createStatement().execute("DROP DATABASE IF EXISTS ace WITH (FORCE);");
                conn.createStatement().execute("CREATE DATABASE ace;");
                conn.createStatement().execute("ALTER DATABASE ace OWNER TO \"ace-manager\";");
            }

            // Refill the database with data for testing
            try (Connection aceConn = getAceConnection()) {
                ScriptUtils.executeSqlScript(aceConn, new FileSystemResource("src/test/resources/sql/ace-schema-test.sql"));
            }

            return true;
        } catch (Exception e) {
            log.error("Error while resetting the test environment: ", e);
            return false;
        }
    }

    /**
     * Helper method to obtain a connection to the postgres database.
     *
     * @return a connection to the administrative postgres database
     * @throws IllegalStateException when the database URL, username, or password are missing in the application.yml
     * @throws SQLException if establishing the connection fails
     */
    private Connection getPostgresConnection() throws IllegalStateException, SQLException {
        // Read the ACE URL from application.yml
        String aceURL = env.getProperty("app.datasource.ace.url");
        String username = env.getProperty("app.datasource.ace.username");
        String password = env.getProperty("app.datasource.ace.password");

        if (!Assertion.assertNotNullAll(aceURL, username, password)) {
            throw new IllegalStateException("app.datasource.ace.url, app.datasource.ace.username, or app.datasource.ace.password is not configured.");
        }

        // Replace the database name with "postgres"
        String adminUrl = aceURL.replace("/ace", "/postgres");

        return DriverManager.getConnection(adminUrl, username, password);
    }

    /**
     * Helper method to obtain a connection to ACE's database.
     *
     * @return a connection to ACE's database
     * @throws IllegalStateException when the database URL, username, or password are missing in the application.yml
     * @throws SQLException if establishing the connection fails
     */
    private Connection getAceConnection() throws IllegalStateException, SQLException {
        // Read the ACE URL from application.yml
        String aceURL = env.getProperty("app.datasource.ace.url");
        String username = env.getProperty("app.datasource.ace.username");
        String password = env.getProperty("app.datasource.ace.password");

        if (!Assertion.assertNotNullAll(aceURL, username, password)) {
            throw new IllegalStateException("app.datasource.ace.url is not configured.");
        }

        return DriverManager.getConnection(aceURL, username, password);
    }

    /**
     * Returns the mock MVC object.
     *
     * @return the mock MVC
     */
    protected MockMvc getMockMvc() {
        return this.mockMvc;
    }

    /**
     * Creates the request builder from the given parameters.
     *
     * @param method the HTTP method e.g. GET, POST etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the mock HTTP servlet request builder
     * @throws JsonProcessingException the JSON processing exception
     */
    protected MockHttpServletRequestBuilder createRequestBuilder(MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws JsonProcessingException {
        if (accessToken != null && !accessToken.isEmpty()) {
            method.header("Authorization", "Bearer " + accessToken);
        }

        if (params != null && params.size() > 0) {
            for (Map.Entry<String, String> set : params.entrySet()) {
                method.param(set.getKey(), set.getValue());
            }
        }

        if (body != null) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            method.content(mapper.writeValueAsString(body));
        }

        method.contentType("application/json");
        method.characterEncoding("UTF-8");

        return method;
    }

    /**
     * Creates the handler for generating a response or request log.
     *
     * @param identifier the identifier for generating the response and request text
     * @return the REST documentation result handler
     */
    private RestDocumentationResultHandler generateHandler(String identifier) {
        return document(identifier, preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()));
    }

    /**
     * Assert that the request result is not found.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertNotFoundRequest(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isNotFound());
    }

    /**
     * Assert that the request result is not found.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertNotFoundRequest(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isNotFound());
    }

    /**
     * Assert that the request result is unauthorized.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertUnauthorizedRequest(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isUnauthorized());
    }

    /**
     * Assert that the request result is unauthorized.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertUnauthorizedRequest(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isUnauthorized());
    }

    /**
     * Assert that the request result is forbidden.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertForbiddenRequest(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isForbidden());
    }

    /**
     * Assert that the request result is forbidden.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertForbiddenRequest(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isForbidden());
    }

    /**
     * Assert that the request result is created.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertCreatedRequest(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isCreated());
    }

    /**
     * Assert that the request result is created.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertCreatedRequest(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isCreated());
    }

    /**
     * Assert that the request result is a bad request.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertBadRequestRequest(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isBadRequest());
    }

    /**
     * Assert that the request result is a bad request.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertBadRequestRequest(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isBadRequest());
    }

    /**
     * Assert that the request result is OK.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertOkRequest(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isOk());
    }

    /**
     * Assert that the request result is OK.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertOkRequest(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isOk());
    }

    /**
     * Assert that the request result is not acceptable.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertNotAcceptable(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isNotAcceptable());
    }

    /**
     * Assert that the request result is not acceptable.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertNotAcceptable(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isNotAcceptable());
    }

    /**
     * Assert that the request result is the status code no content.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertNoContent(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isNoContent());
    }

    /**
     * Assert that the request result is the status code no content.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertNoContent(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isNoContent());
    }

    /**
     * Assert that the request result is an unprocessable entity.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertUnprocessableEntity(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isUnprocessableEntity());
    }

    /**
     * Assert that the request result is an unprocessable entity.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertUnprocessableEntity(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isUnprocessableEntity());
    }

    /**
     * Assert that the request result is an internal server error.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertInternalServerError(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, method, params, body, accessToken, status().isInternalServerError());
    }

    /**
     * Assert that the request result is an internal server error.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertInternalServerError(String identifier, RequestBuilder requestBuilder) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, requestBuilder, status().isInternalServerError());
    }


    /**
     * Assert request from request builder mock HTTP servlet response.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param method the method for example get, post etc.
     * @param params the parameter in the URL
     * @param body the request body as any object
     * @param accessToken the access token string
     * @param resultMatcher the target result to assert
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertRequestFromRequestBuilder(String identifier, MockHttpServletRequestBuilder method, Map<String, String> params, Object body, String accessToken, ResultMatcher resultMatcher) throws Exception {
        return this.assertRequestFromRequestBuilder(identifier, this.createRequestBuilder(method, params, body, accessToken), resultMatcher);
    }

    /**
     * Assert request from request builder mock HTTP servlet response.
     *
     * @param identifier the identifier for generating the response and request text.
     * @param requestBuilder the request builder object
     * @param resultMatcher the target result to assert
     * @return the response from the request
     * @throws Exception the exception
     */
    protected MockHttpServletResponse assertRequestFromRequestBuilder(String identifier, RequestBuilder requestBuilder, ResultMatcher resultMatcher) throws Exception {
        ResultActions result = this.getMockMvc().perform(requestBuilder);
        result.andDo(this.generateHandler("doc_" + identifier));
        result.andExpect(resultMatcher);
        return result.andReturn().getResponse();
    }

    /**
     * Obtains very simple an access token from the given keycloak instance. This function should split in separate parts
     * in the future if it gets to complex.
     *
     * @param username the user name as String
     * @param password the password as String
     * @return the accessToken as String
     * @throws IOException the IO exception
     * @throws CertificateException the certificate exception
     * @throws NoSuchAlgorithmException the no such algorithm exception
     * @throws KeyStoreException the key store exception
     * @throws KeyManagementException the key management exception
     */
    public String obtainNewAccessToken(String username, String password) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        // Getting the trust-store file
        File trustore = ResourceUtils.getFile(Objects.requireNonNull(this.env.getProperty("spring.security.oauth2.resourceserver.jwt.truststore")));

        // Setting up a SSL context in case we want to do HTTPS requests
        SSLContext theContext = SSLContexts.custom()
                .setProtocol("TLS")
                .loadTrustMaterial(trustore, Objects.requireNonNull(this.env.getProperty("spring.security.oauth2.resourceserver.jwt.truststore-password")).toCharArray())
                .build();

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", this.env.getProperty("spring.security.oauth2.resourceserver.jwt.client-id")));
        params.add(new BasicNameValuePair("username", username));
        params.add(new BasicNameValuePair("password", password));
        params.add(new BasicNameValuePair("grant_type", "password"));
        params.add(new BasicNameValuePair("client_secret", this.env.getProperty("spring.security.oauth2.resourceserver.jwt.client-secret")));

        // Contains the realm
        String targetUrl = this.env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri") + "/protocol/openid-connect/token";
        System.setProperty("javax.net.ssl.trustStore", trustore.getAbsolutePath());

        HttpClient httpClient = HttpClientBuilder.create().setSSLContext(theContext).build();

        HttpPost httpPost = new HttpPost(targetUrl);
        // Needed to send it as a form
        httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");

        try {
            httpPost.setEntity(new UrlEncodedFormEntity(params));
            HttpResponse response = httpClient.execute(httpPost);
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            AccessTokenResponse accessTokenResponse = new ObjectMapper().readValue(responseString, AccessTokenResponse.class);
            this.accessToken = accessTokenResponse.getToken();
        } catch (Exception e) {
            this.accessToken = "";
            throw new RuntimeException(e);
        }

        return this.accessToken;
    }

    /**
     * Returns the keycloak access token if set
     *
     * @return the keycloak access token as String
     */
    public String getAccessToken() {
        return this.accessToken;
    }

    /**
     * Maps a list of JSON objects (encoded in a single String) to a proper list of the provided type.
     *
     * @param content the raw JSON content
     * @param valueType the class to map
     * @param <T> the Template class of the class to map
     * @return the list of mapped new objects
     */
    public <T> List<T> mapJsonObjectsInStringToList(String content, Class<T> valueType) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            return mapper.readValue(content, CollectionsTypeFactory.listOf(valueType));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * Maps a single JSON object to an instance of the provided type.
     *
     * @param content the raw JSON content
     * @param valueType the class to map
     * @param <T> the Template class of the class to map
     * @return the mapped new object
     */
    public <T> T applySingleJsonContentToClass(String content, Class<T> valueType) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            return mapper.readValue(content, valueType);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }

    }

    /**
     * A small helper to assert that the length of a requested
     * domain hierarchy list equals the expected length.
     *
     * @param expectedLength the expected length
     * @return the list of domains that was checked and returned by the service
     * @throws Exception forwards any internally thrown exceptions
     */
    protected List<DomainDto> assertEqualsListDomainHierarchyLength(int expectedLength) throws Exception {

        // Unauthorized tests for getting this list
        this.assertBadRequestRequest("listDomainHierarchyBadRequest", get("/api/pseudonymization/experimental/domains/hierarchy"), null, null, "");
        this.assertUnauthorizedRequest("listDomainHierarchyUnauth", get("/api/pseudonymization/experimental/domains/hierarchy"), null, null, "SomeToken");

        MockHttpServletResponse response = this.assertOkRequest("listDomainHierarchy", get("/api/pseudonymization/experimental/domains/hierarchy"), null, null, this.getAccessToken());
        String content = response.getContentAsString();
        List<DomainDto> domains = this.mapJsonObjectsInStringToList(content, DomainDto.class);

        if (expectedLength == 0) {
            assertNull(domains);
        } else {
            assertEquals(expectedLength, domains.size());
        }

        return domains;
    }

    protected List<RecordDto> assertEqualsListRecordsLength(int expectedLength, String goodDomainButNotFound, String goodDomain) throws Exception {
        this.assertNotFoundRequest("getRecordBatchNotFound", get("/api/pseudonymization/domains/" + goodDomainButNotFound + "/pseudonyms"), null, null, this.getAccessToken());
        MockHttpServletResponse response = this.assertOkRequest("getRecordBatch", get("/api/pseudonymization/domains/" + goodDomain + "/pseudonyms"), null, null, this.getAccessToken());
        String content = response.getContentAsString();
        List<RecordDto> records = this.mapJsonObjectsInStringToList(content, RecordDto.class);

        if (expectedLength == 0) {
            assertNull(records);
        } else {
            assertEquals(expectedLength, records.size());
        }

        return records;
    }

    /**
     * A helper to completely update a domain and assert that the updated attribute is updated correctly.
     *
     * @param expectedDomain the object of the expected domain
     * @param actualDomainName the domain name as string to request
     * @throws Exception forwards any internally thrown exceptions
     */
    protected void domainUpdateHelperComplete(DomainDto expectedDomain, String actualDomainName, DomainDto domainDto) throws Exception {
        Map<String, String> updateParameterChange = new HashMap<>() {
			private static final long serialVersionUID = -2708461912069760668L;
		{
            put("name", actualDomainName);
            put("recursive", "false"); // is required
        }};

        // Unauthorized tests for updating a domain
        this.assertBadRequestRequest("updateDomainBadRequestComplete", put("/api/pseudonymization/domain/complete"), updateParameterChange, null, "");
        this.assertUnauthorizedRequest("updateDomainUnauthComplete", put("/api/pseudonymization/domain/complete"), updateParameterChange, null, "SomeToken");

        this.assertOkRequest("commonUpdateDomainComplete", put("/api/pseudonymization/domain/complete"), updateParameterChange, domainDto, this.getAccessToken());

        this.getAndCheckDomain(expectedDomain, actualDomainName);
    }
    
    /**
     * A helper to update a domain and assert that the updated attribute is updated correctly.
     *
     * @param expectedDomain the object of the expected domain
     * @param actualDomainName the domain name as string to request
     * @throws Exception forwards any internally thrown exceptions
     */
    protected void domainUpdateHelperReduced(DomainDto expectedDomain, String actualDomainName, DomainDto domainDto) throws Exception {
        Map<String, String> updateParamterChange = new HashMap<>() {
        	private static final long serialVersionUID = 5655387819735798090L;
		{
            put("name", actualDomainName);
        }};

        // Unauthorized tests for updating a domain
        this.assertBadRequestRequest("updateDomainBadRequest", put("/api/pseudonymization/domain"), updateParamterChange, null, "");
        this.assertUnauthorizedRequest("updateDomainUnauth", put("/api/pseudonymization/domain"), updateParamterChange, null, "SomeToken");

        this.assertOkRequest("commonUpdateDomain", put("/api/pseudonymization/domain"), updateParamterChange, domainDto, this.getAccessToken());

        this.getAndCheckDomain(expectedDomain, actualDomainName);
    }

    /**
     * A helper to get and assert all values of a domain
     *
     * @param expectedDomain the object of the expected domain
     * @param actualDomainName the domain name as string to request
     * @throws Exception forwards any internally thrown exceptions
     */
    protected void getAndCheckDomain(DomainDto expectedDomain, String actualDomainName) throws Exception {

        Map<String, String> getParameter = new HashMap<>() {
        	private static final long serialVersionUID = 5332670507736263759L;
		{
            put("name", actualDomainName);
        }};

        MockHttpServletResponse response = this.assertOkRequest("getDomain", get("/api/pseudonymization/domain"), getParameter, null, this.getAccessToken());
        String content = response.getContentAsString();

        DomainDto actualDomain = this.applySingleJsonContentToClass(content, DomainDto.class);

        assertEquals(expectedDomain.getId(), actualDomain.getId());
        assertEquals(expectedDomain.getName(), actualDomain.getName());
        assertEquals(expectedDomain.getPrefix(), actualDomain.getPrefix());
        assertEquals(expectedDomain.getValidFrom(), actualDomain.getValidFrom());
        assertEquals(expectedDomain.getValidFromInherited(), actualDomain.getValidFromInherited());
        assertEquals(expectedDomain.getValidTo(), actualDomain.getValidTo());
        assertEquals(expectedDomain.getValidToInherited(), actualDomain.getValidToInherited());
        assertEquals(expectedDomain.getEnforceStartDateValidity(), actualDomain.getEnforceStartDateValidity());
        assertEquals(expectedDomain.getEnforceStartDateValidityInherited(), actualDomain.getEnforceStartDateValidityInherited());
        assertEquals(expectedDomain.getEnforceEndDateValidity(), actualDomain.getEnforceEndDateValidity());
        assertEquals(expectedDomain.getEnforceEndDateValidityInherited(), actualDomain.getEnforceEndDateValidityInherited());
        assertEquals(expectedDomain.getAlgorithm(), actualDomain.getAlgorithm());
        assertEquals(expectedDomain.getAlgorithmInherited(), actualDomain.getAlgorithmInherited());
        assertEquals(expectedDomain.getConsecutiveValueCounter(), actualDomain.getConsecutiveValueCounter());
        assertEquals(expectedDomain.getMultiplePsnAllowed(), actualDomain.getMultiplePsnAllowed());
        assertEquals(expectedDomain.getPseudonymLength(), actualDomain.getPseudonymLength());
        assertEquals(expectedDomain.getPseudonymLengthInherited(), actualDomain.getPseudonymLengthInherited());
        assertEquals(expectedDomain.getPaddingCharacter(), actualDomain.getPaddingCharacter());
        assertEquals(expectedDomain.getPaddingCharacterInherited(), actualDomain.getPaddingCharacterInherited());
        assertEquals(expectedDomain.getAddCheckDigit(), actualDomain.getAddCheckDigit());
        assertEquals(expectedDomain.getAddCheckDigitInherited(), actualDomain.getAddCheckDigitInherited());
        assertEquals(expectedDomain.getLengthIncludesCheckDigit(), actualDomain.getLengthIncludesCheckDigit());
        assertEquals(expectedDomain.getLengthIncludesCheckDigitInherited(), actualDomain.getLengthIncludesCheckDigitInherited());
        assertEquals(expectedDomain.getDescription(), actualDomain.getDescription());
        assertEquals(expectedDomain.getSalt(), actualDomain.getSalt());
        assertEquals(expectedDomain.getSaltLength(), actualDomain.getSaltLength());
    }

    /**
     * Tests a common delete operation without any recursion
     *
     * @param domainName the name of the domain as string
     * @throws Exception forwards any internally thrown exceptions
     */
    protected void assertIsDeletedDomain(String domainName) throws Exception {
        // Delete the domain
        Map<String, String> deleteParameter = new HashMap<>() {
			private static final long serialVersionUID = 7861959161189599718L;
		{
            put("name", domainName);
        }};

        // Unauthorized tests for delete domain
        this.assertBadRequestRequest("deleteDomainBadRequest", delete("/api/pseudonymization/domain"), deleteParameter, null, "");
        this.assertUnauthorizedRequest("deleteDomainUnauth", delete("/api/pseudonymization/domain"), deleteParameter, null, "SomeToken");

        this.assertNoContent("deleteDomain", delete("/api/pseudonymization/domain"), deleteParameter, null, this.getAccessToken());

        // Getting the domain must lead to a not found
        Map<String, String> getParameter = new HashMap<>() {
			private static final long serialVersionUID = -3594953606458809296L;
		{
            put("name", domainName);
        }};

        this.assertForbiddenRequest("getDomainNotFoundAfterDelete", get("/api/pseudonymization/domain"), getParameter, null, this.getAccessToken());
    }

    /**
     * Helper class that works like a generic container to cast a generic object into a defined list container
     * Thanks for help: https://stackoverflow.com/a/61154659
     */
    public static class CollectionsTypeFactory {
        static JavaType listOf(@SuppressWarnings("rawtypes") Class clazz) {
            return TypeFactory.defaultInstance().constructCollectionType(List.class, clazz);
        }
    }
}

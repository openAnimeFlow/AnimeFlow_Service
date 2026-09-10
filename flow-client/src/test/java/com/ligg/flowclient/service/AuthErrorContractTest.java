package com.ligg.flowclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.exception.*;
import com.ligg.common.handler.GlobalExceptionHandler;
import com.ligg.flowclient.config.ApiAuthProperties;
import com.ligg.flowclient.interceptor.ApiSignatureInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthErrorContractTest {
    @RestController
    static class Errors {
        @GetMapping("/access") public void access() { throw new AccessTokenExpiredException(); }
        @GetMapping("/refresh") public void refresh() { throw new RefreshTokenInvalidException(); }
        @GetMapping("/bangumi") public void bangumi() { throw new BangumiAuthorizationException(); }
        @GetMapping("/upstream") public void upstream() { throw new BangumiUpstreamException("temporary failure"); }
    }

    @Test
    void authReasonsAreDistinctAndPreserveLegacy401Envelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Errors())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String[] sample : new String[][] {
                {"/access", "access_token_expired"}, {"/refresh", "refresh_token_invalid"},
                {"/bangumi", "bangumi_auth_required"}}) {
            mvc.perform(get(sample[0])).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.authReason").value(sample[1]));
        }
        mvc.perform(get("/upstream")).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.authReason").doesNotExist());
    }

    @Test
    void signatureFailureHasIndependentReason() throws Exception {
        var mapper = new ObjectMapper();
        var interceptor = new ApiSignatureInterceptor(new ApiAuthProperties(), mapper);
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, new Object()));
        assertEquals(401, response.getStatus());
        assertEquals("api_signature_invalid", mapper.readTree(response.getContentAsString()).get("authReason").asText());
    }
}

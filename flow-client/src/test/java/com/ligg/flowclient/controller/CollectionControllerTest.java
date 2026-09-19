package com.ligg.flowclient.controller;

import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import com.ligg.flowclient.service.UserBgmCollectionSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

    @Test
    void updateReturnsLocalSaveAndRemoteOutcomeWithoutRequiringBangumi() throws Exception {
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(userBgmCollectionService.updateCollection(eq(10L), eq(42), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.ligg.flowclient.module.vo.CollectionUpdateVo(true,
                        com.ligg.common.statuenum.CollectionRemoteSyncStatus.LOCAL_ONLY, 1));
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/collections/42")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"type\":3,\"tags\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localSaved").value(true))
                .andExpect(jsonPath("$.data.remoteSyncStatus").value("LOCAL_ONLY"))
                .andExpect(jsonPath("$.data.localVersion").value(1));
        org.mockito.Mockito.verifyNoInteractions(bangumiOAuthTokenService);
    }

    @org.mockito.Mock
    private UserBgmCollectionService userBgmCollectionService;

    @org.mockito.Mock
    private BangumiOAuthTokenService bangumiOAuthTokenService;

    @org.mockito.Mock
    private UserBgmCollectionSyncService userBgmCollectionSyncService;

    @org.mockito.Mock
    private JwtTokenService jwtTokenService;

    @Test
    void getMeCollections_allowsMissingBangumiOauth() throws Exception {
        UserCollectionsVo collections = new UserCollectionsVo();
        collections.setTotal(2);
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(bangumiOAuthTokenService.findBangumiOauth(10L)).thenReturn(null);
        when(userBgmCollectionService.listMyCollections(null, 10L, 2, 2, null, 20, 0)).thenReturn(collections);

        mockMvc().perform(get("/api/v1/users/collections")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(2));

        verify(userBgmCollectionService).listMyCollections(
                isNull(), eq(10L), eq(2), eq(2), isNull(), eq(20), eq(0));
    }

    @Test
    void getMeCollections_passesKeywordAndBangumiToken() throws Exception {
        UserOauthEntity oauth = new UserOauthEntity();
        oauth.setAccessToken("bangumi-token");
        UserCollectionsVo collections = new UserCollectionsVo();
        collections.setTotal(1);
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(bangumiOAuthTokenService.findBangumiOauth(10L)).thenReturn(oauth);
        when(userBgmCollectionService.listMyCollections(
                "bangumi-token", 10L, 2, 2, "葬送", 20, 0)).thenReturn(collections);

        mockMvc().perform(get("/api/v1/users/collections")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token")
                        .param("keyword", "葬送"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(userBgmCollectionService).listMyCollections(
                eq("bangumi-token"), eq(10L), eq(2), eq(2), eq("葬送"), eq(20), eq(0));
    }

    private MockMvc mockMvc() {
        return standaloneSetup(new CollectionController(
                jwtTokenService,
                bangumiOAuthTokenService,
                userBgmCollectionService,
                userBgmCollectionSyncService)).build();
    }
}

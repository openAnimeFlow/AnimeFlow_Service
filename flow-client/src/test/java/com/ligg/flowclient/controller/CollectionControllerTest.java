package com.ligg.flowclient.controller;

import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.enums.CollectionSort;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import com.ligg.flowclient.service.UserBgmCollectionSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void deleteCollectionReturnsLogicalDeleteOutcome() throws Exception {
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(userBgmCollectionService.deleteCollection(10L, 42))
                .thenReturn(new com.ligg.flowclient.module.vo.CollectionUpdateVo(true,
                        com.ligg.common.statuenum.CollectionRemoteSyncStatus.LOCAL_ONLY, 2));

        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/users/collections/42")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localSaved").value(true))
                .andExpect(jsonPath("$.data.remoteSyncStatus").value("LOCAL_ONLY"))
                .andExpect(jsonPath("$.data.localVersion").value(2));

        verify(userBgmCollectionService).deleteCollection(10L, 42);
    }

    @Test
    void getMeCollections_allowsMissingBangumiOauth() throws Exception {
        UserCollectionsVo collections = new UserCollectionsVo();
        collections.setTotal(2);
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(bangumiOAuthTokenService.findBangumiOauth(10L)).thenReturn(null);
        when(userBgmCollectionService.listMyCollections(null, 10L, 2, 2, null, CollectionSort.DEFAULT, 20, 0)).thenReturn(collections);

        mockMvc().perform(get("/api/v1/users/collections")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(2));

        verify(userBgmCollectionService).listMyCollections(
                isNull(), eq(10L), eq(2), eq(2), isNull(), eq(CollectionSort.DEFAULT), eq(20), eq(0));
    }

    @Test
    void getMeCollections_exposesEpisodeTotalProgressAndNextAirDate() throws Exception {
        var item = new com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto.Item();
        item.setId(41);
        item.setTotalEpisodes(12);
        item.setWatchedEpisode(5);
        item.setNextEpisodeAirDate("2026-10-11");
        UserCollectionsVo collections = new UserCollectionsVo();
        collections.setTotal(1);
        collections.setData(java.util.List.of(item));
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(bangumiOAuthTokenService.findBangumiOauth(10L)).thenReturn(null);
        when(userBgmCollectionService.listMyCollections(null, 10L, 2, 2, null, CollectionSort.DEFAULT, 20, 0)).thenReturn(collections);

        mockMvc().perform(get("/api/v1/users/collections")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].totalEpisodes").value(12))
                .andExpect(jsonPath("$.data.data[0].watchedEpisode").value(5))
                .andExpect(jsonPath("$.data.data[0].nextEpisodeAirDate").value("2026-10-11"));
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
                "bangumi-token", 10L, 2, 2, "葬送", CollectionSort.DEFAULT, 20, 0)).thenReturn(collections);

        mockMvc().perform(get("/api/v1/users/collections")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token")
                        .param("keyword", "葬送"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(userBgmCollectionService).listMyCollections(
                eq("bangumi-token"), eq(10L), eq(2), eq(2), eq("葬送"), eq(CollectionSort.DEFAULT), eq(20), eq(0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "default")
    void getMeCollections_legacyRequestsUseDefaultSortAndKeepResponse(String sort) throws Exception {
        UserOauthEntity oauth = new UserOauthEntity();
        oauth.setAccessToken("bangumi-token");
        var first = new com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto.Item();
        first.setId(42);
        first.setTotalEpisodes(12);
        first.setWatchedEpisode(5);
        var second = new com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto.Item();
        second.setId(41);
        UserCollectionsVo collections = new UserCollectionsVo();
        collections.setTotal(3);
        collections.setData(java.util.List.of(first, second));
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(bangumiOAuthTokenService.findBangumiOauth(10L)).thenReturn(oauth);
        when(userBgmCollectionService.listMyCollections(
                "bangumi-token", 10L, 2, 3, "葬送", CollectionSort.DEFAULT, 2, 1)).thenReturn(collections);

        var request = get("/api/v1/users/collections")
                .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token")
                .param("subjectType", "2").param("type", "3").param("keyword", "葬送")
                .param("limit", "2").param("offset", "1");
        if (sort != null) {
            request.param("sort", sort);
        }
        mockMvc().perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.data.length()").value(2))
                .andExpect(jsonPath("$.data.data[0].id").value(42))
                .andExpect(jsonPath("$.data.data[1].id").value(41))
                .andExpect(jsonPath("$.data.data[0].totalEpisodes").value(12))
                .andExpect(jsonPath("$.data.data[0].watchedEpisode").value(5));

        verify(userBgmCollectionService).listMyCollections(
                "bangumi-token", 10L, 2, 3, "葬送", CollectionSort.DEFAULT, 2, 1);
    }

    @ParameterizedTest
    @CsvSource({"default, DEFAULT", "watch, WATCH", "WATCH, WATCH"})
    void getMeCollections_acceptsSort(String value, CollectionSort sort) throws Exception {
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(userBgmCollectionService.listMyCollections(null, 10L, 2, 3, "葬送", sort, 5, 10))
                .thenReturn(new UserCollectionsVo());

        mockMvc().perform(get("/api/v1/users/collections")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token")
                        .param("type", "3").param("keyword", "葬送")
                        .param("sort", value).param("limit", "5").param("offset", "10"))
                .andExpect(status().isOk());

        verify(userBgmCollectionService).listMyCollections(null, 10L, 2, 3, "葬送", sort, 5, 10);
    }

    @Test
    void getMeCollections_rejectsUnknownSortBeforeQuerying() throws Exception {
        mockMvc().perform(get("/api/v1/users/collections")
                        .requestAttr(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, "flow-token")
                        .param("sort", "invalid"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(userBgmCollectionService, jwtTokenService);
    }

    private MockMvc mockMvc() {
        var conversion = new DefaultFormattingConversionService();
        conversion.addConverter(String.class, CollectionSort.class, CollectionSort::fromValue);
        return standaloneSetup(new CollectionController(
                org.mockito.Mockito.mock(com.ligg.flowclient.service.CollectionSyncSseService.class),
                jwtTokenService,
                bangumiOAuthTokenService,
                userBgmCollectionService,
                userBgmCollectionSyncService)).setConversionService(conversion).build();
    }
}

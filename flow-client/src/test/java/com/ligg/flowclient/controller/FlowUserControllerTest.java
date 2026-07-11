package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.service.BangumiOAuthTokenService;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.UserBgmCollectionService;
import com.ligg.flowclient.service.UserBgmCollectionSyncService;
import com.ligg.flowclient.service.UserEpisodeWatchService;
import com.ligg.flowclient.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowUserControllerTest {

    @org.mockito.Mock
    private UserService userService;

    @org.mockito.Mock
    private UserBgmCollectionService userBgmCollectionService;

    @org.mockito.Mock
    private BangumiOAuthTokenService bangumiOAuthTokenService;

    @org.mockito.Mock
    private UserBgmCollectionSyncService userBgmCollectionSyncService;

    @org.mockito.Mock
    private JwtTokenService jwtTokenService;

    @org.mockito.Mock
    private UserEpisodeWatchService userEpisodeWatchService;

    @Test
    void getMeCollections_allowsMissingBangumiOauth() {
        FlowUserController controller = new FlowUserController(
                userService,
                userBgmCollectionService,
                bangumiOAuthTokenService,
                userBgmCollectionSyncService,
                jwtTokenService,
                userEpisodeWatchService);
        UserCollectionsVo collections = new UserCollectionsVo();
        when(jwtTokenService.validateAccessToken("flow-token")).thenReturn(10L);
        when(bangumiOAuthTokenService.findBangumiOauth(10L)).thenReturn(null);
        when(userBgmCollectionService.listMyCollections(null, 10L, 2, 2, 20, 0)).thenReturn(collections);

        Result<UserCollectionsVo> result = controller.getMeCollections("flow-token", 2, 2, 20, 0);

        assertEquals(ResponseCode.SUCCESS.getCode(), result.getCode());
        assertSame(collections, result.getData());
        verify(userBgmCollectionService).listMyCollections(isNull(), eq(10L), eq(2), eq(2), eq(20), eq(0));
    }
}

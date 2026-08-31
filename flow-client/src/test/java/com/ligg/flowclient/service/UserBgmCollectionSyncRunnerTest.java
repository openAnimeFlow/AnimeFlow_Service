package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.vo.UserBgmCollectionSyncStatusVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBgmCollectionSyncRunnerTest {

    @Mock
    private BangumiOAuthTokenService bangumiOAuthTokenService;

    @Mock
    private BangumiOAuthExecutor bangumiOAuthExecutor;

    @Mock
    private BangumiClient bangumiClient;

    @Mock
    private UserBgmCollectionMapper userBgmCollectionMapper;

    @Mock
    private UserBgmCollectionSyncStatusStore statusStore;

    @Test
    @SuppressWarnings("unchecked")
    void runSync_updatesExistingSubjectWhenBangumiInterestIdChanges() {
        long userId = 10L;
        UserOauthEntity oauth = new UserOauthEntity();
        UserBgmCollectionEntity existing = new UserBgmCollectionEntity();
        existing.setId(100L);
        existing.setUserId(userId);
        existing.setSubjectId(569671);
        existing.setBgmInterestId(111L);

        UserCollectionsDto page = new UserCollectionsDto();
        page.setTotal(1);
        UserCollectionsDto.Item item = new UserCollectionsDto.Item();
        item.setId(569671);
        item.setType(2);
        UserCollectionsDto.Interest interest = new UserCollectionsDto.Interest();
        interest.setId(222L);
        interest.setType(2);
        item.setInterest(interest);
        page.setData(List.of(item));

        UserBgmCollectionSyncStatusVo status = new UserBgmCollectionSyncStatusVo();
        when(bangumiOAuthTokenService.requireBangumiOauth(userId)).thenReturn(oauth);
        when(statusStore.getStatus(userId)).thenReturn(status);
        when(userBgmCollectionMapper.selectOne(any())).thenReturn(existing);
        when(bangumiOAuthExecutor.execute(eq(oauth), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, UserCollectionsDto> call = invocation.getArgument(1);
                    return call.apply("access-token");
        });
        when(bangumiClient.getMeCollections("access-token", 2, 1, 50, 0)).thenReturn(page);
        when(bangumiClient.getMeCollections("access-token", 2, 2, 50, 0))
                .thenReturn(emptyPage());
        when(bangumiClient.getMeCollections("access-token", 2, 3, 50, 0))
                .thenReturn(emptyPage());
        when(bangumiClient.getMeCollections("access-token", 2, 4, 50, 0))
                .thenReturn(emptyPage());
        when(bangumiClient.getMeCollections("access-token", 2, 5, 50, 0))
                .thenReturn(emptyPage());

        UserBgmCollectionSyncRunner runner = new UserBgmCollectionSyncRunner(
                bangumiOAuthTokenService,
                bangumiOAuthExecutor,
                bangumiClient,
                userBgmCollectionMapper,
                statusStore,
                new ObjectMapper());

        // Lambda wrappers need MyBatis-Plus table metadata when their SQL is inspected.
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                UserBgmCollectionEntity.class);

        // Direct invocation deliberately bypasses Spring's @Async proxy for a deterministic unit test.
        runner.runSync(userId, 2);

        ArgumentCaptor<LambdaQueryWrapper<UserBgmCollectionEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userBgmCollectionMapper).selectOne(queryCaptor.capture());
        String query = queryCaptor.getValue().getSqlSegment();
        assertTrue(query.contains("user_id"));
        assertTrue(query.contains("subject_id"));
        verify(userBgmCollectionMapper).updateById(existing);
        verify(userBgmCollectionMapper, never()).insert(any(UserBgmCollectionEntity.class));
    }

    private static UserCollectionsDto emptyPage() {
        UserCollectionsDto page = new UserCollectionsDto();
        page.setData(List.of());
        return page;
    }
}

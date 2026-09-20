package com.ligg.flowclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.exception.BangumiAuthorizationException;
import com.ligg.common.statuenum.CollectionRemoteSyncStatus;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollectionUploadServiceTest {
    private final UserBgmCollectionMapper mapper = mock(UserBgmCollectionMapper.class);
    private final BangumiOAuthTokenService tokens = mock(BangumiOAuthTokenService.class);
    private final BangumiOAuthExecutor executor = mock(BangumiOAuthExecutor.class);
    private final BangumiClient client = mock(BangumiClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private final LocalCollectionWriter writer = new LocalCollectionWriter(mapper, mock(BangumiSubjectMapper.class), json);
    private final CollectionUploadService service = new CollectionUploadService(
            writer, mapper, mock(CollectionWriteLock.class), tokens, executor, client, json);
    private UserBgmCollectionEntity row;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void init() {
        row = new UserBgmCollectionEntity();
        row.setId(1L); row.setUserId(10L); row.setSubjectId(42); row.setType(3);
        row.setSubjectType(2);
        row.setVersion(1L); row.setRemoteSyncStatus(CollectionRemoteSyncStatus.PENDING);
        row.setPendingPayload("{\"type\":3}"); row.setSyncOauthId(20L); row.setBgmAccountUid(30L);
        var oauth = new UserOauthEntity();
        oauth.setId(20L); oauth.setPlatformUid(30L); oauth.setAccessToken("test-token");
        when(tokens.findBangumiOauth(10L)).thenReturn(oauth);
        when(mapper.updateRemoteState(any())).thenReturn(1);
        when(executor.execute(any(UserOauthEntity.class), any(Function.class)))
                .thenAnswer(invocation -> ((Function<String, ?>) invocation.getArgument(1)).apply("test-token"));
    }

    @Test
    void unboundDoesNotCallUpstream() {
        row.setRemoteSyncStatus(CollectionRemoteSyncStatus.LOCAL_ONLY);
        assertEquals(CollectionRemoteSyncStatus.LOCAL_ONLY, service.upload(row).remoteSyncStatus());
        verifyNoInteractions(client);
    }

    @Test
    void unresolvedTaskConflictBlocksAutomaticAndExplicitUpload() {
        when(mapper.countBlockedSyncItems(10L,42)).thenReturn(1L);
        assertEquals(CollectionRemoteSyncStatus.CONFLICT,service.upload(row).remoteSyncStatus());
        assertEquals("{\"type\":3}",row.getPendingPayload());
        verifyNoInteractions(client);
    }

    @Test
    void activeFullScanDefersSingleUploadWithoutLosingIntent() {
        when(mapper.countActiveSyncTasks(10L,2)).thenReturn(1L);
        assertEquals(CollectionRemoteSyncStatus.PENDING,service.upload(row).remoteSyncStatus());
        assertNotNull(row.getPendingPayload());
        verifyNoInteractions(client);
    }

    @Test
    void successfulWriteConfirmsRemoteAndClearsOnlyPendingIntent() {
        when(client.getSubject(42, "test-token")).thenReturn(detail(1), detail(3));
        assertEquals(CollectionRemoteSyncStatus.SYNCED, service.upload(row).remoteSyncStatus());
        verify(client).updateCollection(eq("test-token"), eq(42), any());
        assertNull(row.getPendingPayload());
        assertNotNull(row.getSyncTime());
        assertEquals(1L, row.getVersion());
    }

    @Test
    void reorderedTagsDoNotCauseConflictAfterUpload() {
        row.setPendingPayload("{\"type\":3,\"tags\":[\"B\",\"A\"]}");
        var confirmed = detail(3);
        confirmed.getInterest().setTags(java.util.List.of("A", "B"));
        when(client.getSubject(42, "test-token")).thenReturn(detail(1), confirmed);
        assertEquals(CollectionRemoteSyncStatus.SYNCED, service.upload(row).remoteSyncStatus());
        assertNull(row.getPendingPayload());
    }

    @Test
    void timeoutKeepsIntentAndRetryDoesNotRewriteAlreadyAppliedValue() {
        when(client.getSubject(42, "test-token")).thenReturn(detail(1));
        doThrow(new IllegalStateException("timeout")).when(client).updateCollection(anyString(), anyInt(), any());
        assertEquals(CollectionRemoteSyncStatus.PENDING, service.upload(row).remoteSyncStatus());
        assertNotNull(row.getRemoteBaseline());
        assertNotNull(row.getPendingPayload());
        assertNotNull(row.getNextRetryAt());
        reset(client);
        when(client.getSubject(42, "test-token")).thenReturn(detail(3));
        assertEquals(CollectionRemoteSyncStatus.SYNCED, service.upload(row).remoteSyncStatus());
        verify(client, never()).updateCollection(anyString(), anyInt(), any());
    }

    @Test
    void changedRemoteDuringRetryStopsInsteadOfOverwriting() {
        row.setRemoteBaseline("{\"type\":1}");
        when(client.getSubject(42, "test-token")).thenReturn(detail(2));
        assertEquals(CollectionRemoteSyncStatus.CONFLICT, service.upload(row).remoteSyncStatus());
        verify(client, never()).updateCollection(anyString(), anyInt(), any());
        assertNotNull(row.getPendingPayload());
    }

    @Test
    void changedBindingAndExpiredAuthorizationPreserveLocalData() {
        var newBinding = new UserOauthEntity();
        newBinding.setId(99L); newBinding.setPlatformUid(30L);
        when(tokens.findBangumiOauth(10L)).thenReturn(newBinding);
        assertEquals(CollectionRemoteSyncStatus.AUTH_REQUIRED, service.upload(row).remoteSyncStatus());
        verifyNoInteractions(client);
        assertNotNull(row.getPendingPayload());
    }

    @Test
    void authorizationFailureIsNotReportedAsFlowLogout() {
        when(client.getSubject(42, "test-token")).thenThrow(new BangumiAuthorizationException());
        var result = service.upload(row);
        assertTrue(result.localSaved());
        assertEquals(CollectionRemoteSyncStatus.AUTH_REQUIRED, result.remoteSyncStatus());
        assertNotNull(row.getPendingPayload());
    }

    @Test
    void failureWritingSuccessStateDoesNotLosePendingIntent() {
        when(client.getSubject(42, "test-token")).thenReturn(detail(3));
        when(mapper.updateRemoteState(any())).thenReturn(1).thenThrow(new IllegalStateException("database unavailable"));
        assertEquals(CollectionRemoteSyncStatus.PENDING, service.upload(row).remoteSyncStatus());
        assertNotNull(row.getPendingPayload());
    }

    private SubjectDetailDto detail(int type) {
        var detail = new SubjectDetailDto();
        var interest = new SubjectDetailDto.SubjectInterest();
        interest.setId(123L); interest.setType(type); interest.setUpdatedAt(100L);
        detail.setInterest(interest);
        return detail;
    }
}

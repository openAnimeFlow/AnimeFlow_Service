package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.*;
import com.ligg.common.thirdparty.bangumi.request.UpdateCollectionBody;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.flowclient.mapper.*;
import com.ligg.flowclient.module.entity.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSyncItemExecutorTest {
    final CollectionSyncTaskService tasks=mock(CollectionSyncTaskService.class);
    final CollectionSyncTaskMapper taskMapper=mock(CollectionSyncTaskMapper.class);
    final CollectionSyncConflictMapper items=mock(CollectionSyncConflictMapper.class);
    final UserBgmCollectionMapper collections=mock(UserBgmCollectionMapper.class);
    final ObjectMapper json=new ObjectMapper();
    final LocalCollectionWriter writer=new LocalCollectionWriter(collections,mock(BangumiSubjectMapper.class),json);
    final CollectionWriteLock locks=mock(CollectionWriteLock.class);
    final BangumiOAuthExecutor oauth=mock(BangumiOAuthExecutor.class);
    final BangumiClient client=mock(BangumiClient.class);
    final TransactionTemplate tx=mock(TransactionTemplate.class);
    final CollectionSyncItemExecutor service=new CollectionSyncItemExecutor(tasks,taskMapper,items,collections,writer,locks,oauth,client,json,tx);
    final CollectionSyncTaskEntity task=new CollectionSyncTaskEntity();
    final CollectionSyncConflictEntity item=new CollectionSyncConflictEntity();
    final UserBgmCollectionEntity row=new UserBgmCollectionEntity();
    final SubjectDetailDto remote=new SubjectDetailDto();

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),UserBgmCollectionEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),CollectionSyncTaskEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),CollectionSyncConflictEntity.class);
        task.setId(1L); task.setUserId(10L); task.setSubjectType(2); task.setOauthId(8L); task.setBgmAccountUid(99L);
        item.setId(2L); item.setTaskId(1L); item.setUserId(10L); item.setSubjectId(42); item.setSubjectType(2);
        item.setStatus("PLANNED"); item.setConflictVersion(0L);
        row.setId(3L); row.setUserId(10L); row.setSubjectId(42); row.setSubjectType(2); row.setType(3);
        row.setVersion(0L); row.setTags("[]"); row.setComment(""); row.setRate(0); row.setIsPrivate(false);
        remote.setId(42); remote.setType(2);
        remote.setInterest(new SubjectDetailDto.SubjectInterest()); remote.getInterest().setType(2); remote.getInterest().setId(77L);
        when(tasks.owned(10L,1L)).thenReturn(task);
        when(tasks.requireBinding(task)).thenReturn(new UserOauthEntity());
        when(items.selectById(2L)).thenReturn(item);
        when(collections.selectOne(any())).thenReturn(row);
        when(collections.update(isNull(),any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(locks.execute(anyLong(),anyInt(),any())).thenAnswer(i -> ((Supplier<?>)i.getArgument(2)).get());
        when(oauth.execute(any(UserOauthEntity.class),any())).thenAnswer(i -> ((Function<String,?>)i.getArgument(1)).apply("token"));
        when(client.getSubject(42,"token")).thenAnswer(i -> remote);
        doAnswer(i -> { ((Consumer<org.springframework.transaction.TransactionStatus>)i.getArgument(0)).accept(null); return null; })
                .when(tx).executeWithoutResult(any());
    }
    @Test void scannedUnchangedCollectionDoesNotFetchDetails() {
        remote.getInterest().setType(3);
        item.setScanSnapshot(writer.writeJson(remote));
        service.execute(task,item);
        assertEquals("DONE",item.getStatus());
        verifyNoInteractions(client);
    }

    @Test void scannedRemoteOnlyCollectionImportsWithoutFetchingDetails() {
        when(collections.selectOne(any())).thenReturn(null);
        remote.getInterest().setEpStatus(6);
        item.setScanSnapshot(writer.writeJson(remote));
        service.execute(task,item);
        assertEquals("IMPORT",item.getOperation());
        verify(collections).insert(argThat((UserBgmCollectionEntity r) -> r.getEpStatus()==6 && r.getType()==2));
        verifyNoInteractions(client);
    }

    @Test void scannedCategoryConflictDoesNotFetchDetails() {
        item.setScanSnapshot(writer.writeJson(remote));
        service.execute(task,item);
        assertEquals("CONFLICT",item.getStatus());
        verifyNoInteractions(client);
    }

    @Test void scannedItemWithoutInterestIsSkippedWithoutInventingCollection() {
        when(collections.selectOne(any())).thenReturn(null);
        remote.setInterest(null);
        item.setScanSnapshot(writer.writeJson(remote));
        service.execute(task,item);
        assertEquals("DONE",item.getStatus());
        verify(collections,never()).insert(any(UserBgmCollectionEntity.class));
        verifyNoInteractions(client);
    }

    @Test void scannedDirtyCollectionChecksRemoteBeforeWriting() {
        remote.getInterest().setType(3);
        item.setScanSnapshot(writer.writeJson(remote));
        row.setPendingPayload("{\"rate\":7}");
        remote.getInterest().setType(4); // Changed after pagination.
        service.execute(task,item);
        assertEquals("CONFLICT",item.getStatus());
        verify(client).getSubject(42,"token");
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }

    @Test void resumedPreparedIntentIgnoresScanSnapshot() {
        item.setScanSnapshot(writer.writeJson(remote));
        item.setStatus("APPLY_PENDING"); item.setOperation("UPLOAD"); item.setLocalVersion(0L);
        item.setDesiredPayload("{\"type\":3}"); item.setRemoteSnapshot("{\"type\":2}");
        remote.getInterest().setType(3);
        service.execute(task,item);
        assertEquals("DONE",item.getStatus());
        verify(client).getSubject(42,"token");
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }

    @Test void listSnapshotPreservesInterestFieldsAndDefaults() throws Exception {
        var entry=new com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto.Item();
        entry.setId(42); entry.setType(2);
        var interest=new com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto.Interest();
        interest.setType(3); interest.setId(77L); interest.setPrivate_(true); interest.setEpStatus(6);
        entry.setInterest(interest);
        var snapshot=json.readTree(service.scanSnapshot(entry));
        assertTrue(snapshot.path("interest").path("private").asBoolean());
        assertEquals(6,snapshot.path("interest").path("epStatus").asInt());
        assertEquals(0,snapshot.path("interest").path("rate").asInt(-1));
    }

    @Test void historicalVersionZeroConflictIsFrozenWithoutPut() {
        service.execute(task,item);
        assertEquals("CONFLICT",item.getStatus());
        assertEquals(0L,item.getLocalVersion());
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
        verify(collections).update(isNull(),argThat((LambdaUpdateWrapper<UserBgmCollectionEntity> w) ->
                w.getSqlSet().contains("remote_sync_status")));
    }
    @ParameterizedTest @ValueSource(ints={2,3,5})
    void decisionAdoptsEitherSideOrThirdTypeAndPreservesDirtyFields(int type) {
        row.setPendingPayload("{\"rate\":7,\"comment\":\"local edit\",\"tags\":[]}");
        service.execute(task,item); // Detect and persist conflict.
        long version=item.getConflictVersion();
        service.resolve(10L,1L,2L,version,type);
        assertEquals("RESOLUTION_PENDING",item.getStatus());
        assertEquals(3,row.getType()); // accepting a decision is not yet completion.
        doAnswer(i -> {
            UpdateCollectionBody body=i.getArgument(2);
            assertEquals(7,body.getRate()); assertEquals("local edit",body.getComment()); assertTrue(body.getTags().isEmpty());
            remote.getInterest().setType(body.getType());
            remote.getInterest().setRate(body.getRate()); remote.getInterest().setComment(body.getComment());
            return null;
        }).when(client).updateCollection(anyString(),anyInt(),any());
        service.execute(task,item);
        assertEquals("DONE",item.getStatus()); assertEquals(type,row.getType()); assertNull(row.getPendingPayload());
        service.resolve(10L,1L,2L,version,type); // response loss is idempotent.
        verify(client,times(1)).updateCollection(anyString(),anyInt(),any());
    }
    @Test void reorderedTagsAfterPutCompleteResolution() {
        row.setPendingPayload("{\"tags\":[\"B\",\"A\"]}");
        remote.getInterest().setTags(java.util.List.of("A", "B"));
        service.execute(task,item);
        // Reordering between conflict detection and decision must not invalidate the snapshot.
        remote.getInterest().setTags(java.util.List.of("B", "A"));
        service.resolve(10L,1L,2L,item.getConflictVersion(),3);
        doAnswer(i -> {
            remote.getInterest().setType(3);
            remote.getInterest().setTags(java.util.List.of("A", "B"));
            return null;
        }).when(client).updateCollection(anyString(),anyInt(),any());
        service.execute(task,item);
        assertEquals("DONE",item.getStatus());
        assertEquals(3,row.getType());
        assertNull(row.getPendingPayload());
        verify(client,times(1)).updateCollection(anyString(),anyInt(),any());
    }

    @Test void recoveredPutWithReorderedTagsDoesNotWriteAgain() {
        item.setStatus("APPLY_PENDING"); item.setOperation("RESOLVE"); item.setLocalVersion(0L);
        item.setDesiredPayload("{\"type\":3,\"tags\":[\"B\",\"A\"]}");
        item.setRemoteSnapshot("{\"type\":2,\"tags\":[\"A\",\"B\"]}");
        remote.getInterest().setType(3);
        remote.getInterest().setTags(java.util.List.of("A", "B"));
        service.execute(task,item);
        assertEquals("DONE",item.getStatus());
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }

    @Test void changedTagContentStillInvalidatesDecision() {
        remote.getInterest().setTags(java.util.List.of("A", "B"));
        service.execute(task,item);
        remote.getInterest().setTags(java.util.List.of("A", "C"));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(10L,1L,2L,item.getConflictVersion(),3));
        assertEquals("CONFLICT",item.getStatus());
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }

    @Test void remoteAlreadyEqualsSelectedTypeNeedsNoPut() {
        service.execute(task,item); service.resolve(10L,1L,2L,item.getConflictVersion(),2);
        service.execute(task,item);
        assertEquals("DONE",item.getStatus()); assertEquals(2,row.getType());
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }
    @Test void localEditInvalidatesOldDecision() {
        service.execute(task,item); long before=item.getConflictVersion();
        row.setVersion(1L);
        assertThrows(IllegalArgumentException.class,() -> service.resolve(10L,1L,2L,before,3));
        assertEquals("CONFLICT",item.getStatus()); assertTrue(item.getConflictVersion()>before);
        assertNull(item.getSelectedType()); verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }
    @Test void remoteEditInvalidatesOldDecision() {
        service.execute(task,item); long before=item.getConflictVersion();
        remote.getInterest().setType(4);
        assertThrows(IllegalArgumentException.class,() -> service.resolve(10L,1L,2L,before,3));
        assertEquals(4,item.getRemoteType()); assertEquals("CONFLICT",item.getStatus());
    }
    @Test void changedBindingRejectsOldTaskBeforeReadingOrWritingRemote() {
        when(tasks.requireBinding(task)).thenThrow(new IllegalArgumentException("SYNC_BINDING_CHANGED"));
        assertThrows(IllegalArgumentException.class,() -> service.resolve(10L,1L,2L,1L,3));
        verifyNoInteractions(client); assertNull(row.getSyncOauthId());
    }
    @Test void recoveredPreparedPutConfirmsSuccessWithoutRepeatingIt() {
        item.setStatus("APPLY_PENDING"); item.setOperation("UPLOAD"); item.setLocalVersion(0L);
        item.setDesiredPayload("{\"type\":3}");
        item.setRemoteSnapshot("{\"type\":2}");
        remote.getInterest().setType(3);
        service.execute(task,item);
        assertEquals("DONE",item.getStatus());
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }
    @Test void localOnlyUploadsFullCollection() {
        remote.setInterest(null);
        doAnswer(i -> {
            UpdateCollectionBody body=i.getArgument(2);
            remote.setInterest(new SubjectDetailDto.SubjectInterest()); remote.getInterest().setId(77L);
            remote.getInterest().setType(body.getType());
            return null;
        }).when(client).updateCollection(anyString(),anyInt(),any());
        service.execute(task,item);
        assertEquals("UPLOAD",item.getOperation()); assertEquals("DONE",item.getStatus());
    }
    @Test void remoteOnlyImportsWithoutPut() {
        when(collections.selectOne(any())).thenReturn(null);
        service.execute(task,item);
        assertEquals("IMPORT",item.getOperation()); assertEquals("DONE",item.getStatus());
        verify(collections).insert(any(UserBgmCollectionEntity.class));
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }
    @Test void changedRemoteAfterAcceptedDecisionRequiresNewChoice() {
        service.execute(task,item); service.resolve(10L,1L,2L,item.getConflictVersion(),3);
        remote.getInterest().setComment("another device");
        service.execute(task,item);
        assertEquals("CONFLICT",item.getStatus()); assertNull(item.getDesiredPayload());
        verify(client,never()).updateCollection(anyString(),anyInt(),any());
    }
}

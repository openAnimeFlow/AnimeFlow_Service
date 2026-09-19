package com.ligg.flowclient.service;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.entity.*;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.flowclient.mapper.*;
import com.ligg.flowclient.module.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.*;
import java.util.function.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class UserBgmCollectionSyncRunnerTest {
    final CollectionSyncTaskMapper taskMapper=mock(CollectionSyncTaskMapper.class);
    final CollectionSyncConflictMapper items=mock(CollectionSyncConflictMapper.class);
    final UserBgmCollectionMapper collections=mock(UserBgmCollectionMapper.class);
    final CollectionSyncTaskService tasks=mock(CollectionSyncTaskService.class);
    final CollectionSyncItemExecutor executor=mock(CollectionSyncItemExecutor.class);
    final CollectionWriteLock locks=mock(CollectionWriteLock.class);
    final BangumiOAuthExecutor oauth=mock(BangumiOAuthExecutor.class);
    final BangumiClient client=mock(BangumiClient.class);
    final UserBgmCollectionSyncRunner runner=new UserBgmCollectionSyncRunner(taskMapper,items,collections,tasks,executor,locks,oauth,client);
    final CollectionSyncTaskEntity task=new CollectionSyncTaskEntity();
    final List<CollectionSyncConflictEntity> staged=new ArrayList<>();
    @SuppressWarnings("unchecked")
    UserBgmCollectionSyncRunnerTest() {
        task.setId(1L); task.setUserId(10L); task.setSubjectType(2); task.setStatus("QUEUED"); task.setPhase("SCANNING");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(locks.execute(anyLong(),anyInt(),any())).thenAnswer(i -> ((Supplier<?>)i.getArgument(2)).get());
        when(tasks.requireBinding(task)).thenReturn(new UserOauthEntity());
        when(oauth.execute(any(UserOauthEntity.class),any())).thenAnswer(i -> ((Function<String,?>)i.getArgument(1)).apply("token"));
        when(items.insert(any(CollectionSyncConflictEntity.class))).thenAnswer(i -> {
            var item=(CollectionSyncConflictEntity)i.getArgument(0); item.setId((long)staged.size()+1); staged.add(item); return 1;
        });
        when(tasks.allItems(1L)).thenReturn(staged);
        var empty=new UserCollectionsDto(); empty.setData(List.of()); empty.setTotal(0);
        when(client.getMeCollections(anyString(),eq(2),anyInt(),eq(50),anyInt())).thenReturn(empty);
    }

    @Test void stagesFullUnionBeforeApplyingAndDoesNotDeleteOtherTypes() {
        var local=new UserBgmCollectionEntity(); local.setSubjectId(42); local.setSubjectType(2);
        when(collections.selectList(any())).thenReturn(List.of(local));
        runner.runTask(1L);
        assertEquals(42,staged.get(0).getSubjectId());
        verify(executor).execute(task,staged.get(0));
        verify(collections,never()).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(tasks).summarize(task);
    }
    @Test void scanPersistsListSnapshotAndAcceptsMissingInterest() {
        var remote=new UserCollectionsDto.Item(); remote.setId(42); remote.setType(2);
        var page=new UserCollectionsDto(); page.setData(List.of(remote)); page.setTotal(1);
        when(client.getMeCollections("token",2,1,50,0)).thenReturn(page);
        when(executor.scanSnapshot(remote)).thenReturn("{\"id\":42,\"type\":2}");
        runner.runTask(1L);
        assertEquals(1,staged.size());
        assertEquals("{\"id\":42,\"type\":2}",staged.get(0).getScanSnapshot());
        verify(executor).execute(task,staged.get(0));
        verify(client,never()).getSubject(anyInt(),anyString());
    }

    @Test void failedScanNeverAppliesPartialData() {
        when(client.getMeCollections("token",2,2,50,0)).thenThrow(new IllegalStateException());
        runner.runTask(1L);
        verifyNoInteractions(executor);
        verify(tasks,never()).summarize(any());
    }
    @Test void schedulerRecoversTaskWithoutClientOrAsyncDispatch() {
        task.setPhase("APPLYING"); task.setStatus("RUNNING");
        var pending=new CollectionSyncConflictEntity(); pending.setStatus("APPLY_PENDING"); staged.add(pending);
        when(taskMapper.selectRecoverable()).thenReturn(List.of(task));
        runner.recover();
        verify(executor).execute(task,pending);
        verify(client,never()).getMeCollections(anyString(),anyInt(),anyInt(),anyInt(),anyInt());
    }
    @Test void completedItemsDoNotRepeatBindingQueries() {
        task.setPhase("RESOLVING"); task.setStatus("RUNNING");
        for (int i=0;i<194;i++) {
            var done=new CollectionSyncConflictEntity(); done.setStatus("DONE"); staged.add(done);
        }
        var pending=new CollectionSyncConflictEntity(); pending.setStatus("RESOLUTION_PENDING"); staged.add(pending);
        runner.runTask(1L);
        verify(tasks,times(2)).requireBinding(task); // Task entry and the one pending item.
        verify(executor).execute(task,pending);
        verifyNoMoreInteractions(executor);
    }

    @Test void runnerHasUnambiguousSpringConstructor() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(CollectionSyncTaskMapper.class,() -> taskMapper);
            context.registerBean(CollectionSyncConflictMapper.class,() -> items);
            context.registerBean(UserBgmCollectionMapper.class,() -> collections);
            context.registerBean(CollectionSyncTaskService.class,() -> tasks);
            context.registerBean(CollectionSyncItemExecutor.class,() -> executor);
            context.registerBean(CollectionWriteLock.class,() -> locks);
            context.registerBean(BangumiOAuthExecutor.class,() -> oauth);
            context.registerBean(BangumiClient.class,() -> client);
            context.registerBean(UserBgmCollectionSyncRunner.class);
            context.refresh();
            assertNotNull(context.getBean(UserBgmCollectionSyncRunner.class));
        }
    }
}

package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.CollectionSyncConflictEntity;
import com.ligg.common.entity.UserOauthEntity;
import com.ligg.common.statuenum.CollectionSyncItemStatus;
import com.ligg.common.statuenum.CollectionRemoteSyncStatus;
import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Creates and drops an isolated database; never reads or changes application rows. */
@EnabledIfEnvironmentVariable(named = "COLLECTION_TEST_MYSQL_URL", matches = ".+")
class CollectionPersistenceMySqlTest {
    private DriverManagerDataSource admin;
    private DriverManagerDataSource dataSource;
    private AnnotationConfigApplicationContext context;
    private String database;
    private UserBgmCollectionMapper mapper;
    private LocalCollectionWriter writer;

    @Configuration
    @EnableTransactionManagement
    static class Transactions {}

    @BeforeEach
    void setup() throws Exception {
        admin = new DriverManagerDataSource(System.getenv("COLLECTION_TEST_MYSQL_URL"),
                System.getenv("COLLECTION_TEST_MYSQL_USER"), System.getenv("COLLECTION_TEST_MYSQL_PASSWORD"));
        database = "anime_flow_p1_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE DATABASE " + database);
        String url = System.getenv("COLLECTION_TEST_MYSQL_URL");
        int query = url.indexOf('?');
        String urlWithoutOptions = query < 0 ? url : url.substring(0, query);
        int schemeEnd = urlWithoutOptions.indexOf("://");
        int databaseSeparator = urlWithoutOptions.indexOf('/', schemeEnd < 0 ? 0 : schemeEnd + 3);
        String base = databaseSeparator < 0
                ? urlWithoutOptions
                : urlWithoutOptions.substring(0, databaseSeparator);
        String options = query < 0 ? "" : url.substring(query);
        dataSource = new DriverManagerDataSource(base + "/" + database + options,
                System.getenv("COLLECTION_TEST_MYSQL_USER"), System.getenv("COLLECTION_TEST_MYSQL_PASSWORD"));
        Path schema = Path.of("../db/anime_flow.sql");
        if (!schema.toFile().exists()) schema = Path.of("db/anime_flow.sql");
        new ResourceDatabasePopulator(new FileSystemResource(schema)).execute(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO user_bgm_collection(user_id, subject_id, bgm_interest_id, type, comment, bgm_updated_at, is_private, `private`, local_updated_at) VALUES (8, 41, 123, 2, '', 100, 1, 0, CURRENT_TIMESTAMP(3))");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(UserBgmCollectionMapper.class);
        config.addMapper(BangumiSubjectMapper.class);
        config.addMapper(com.ligg.flowclient.mapper.CollectionSyncTaskMapper.class);
        config.addMapper(com.ligg.flowclient.mapper.CollectionSyncConflictMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/UserBgmCollectionMapper.xml"));
        var template = new SqlSessionTemplate(factory.getObject());
        mapper = template.getMapper(UserBgmCollectionMapper.class);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(UserBgmCollectionMapper.class, () -> mapper);
        context.registerBean(BangumiSubjectMapper.class, () -> template.getMapper(BangumiSubjectMapper.class));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(LocalCollectionWriter.class);
        context.refresh();
        writer = context.getBean(LocalCollectionWriter.class);
        syncTasks = template.getMapper(com.ligg.flowclient.mapper.CollectionSyncTaskMapper.class);
        syncItems = template.getMapper(com.ligg.flowclient.mapper.CollectionSyncConflictMapper.class);
    }

    private com.ligg.flowclient.mapper.CollectionSyncTaskMapper syncTasks;
    private com.ligg.flowclient.mapper.CollectionSyncConflictMapper syncItems;

    @Test
    void durableTasksEnforceUniquenessAndResolveAfterWorkerRestart() {
        var oauth = new UserOauthEntity(); oauth.setId(50L); oauth.setPlatformUid(60L);
        var tokens = org.mockito.Mockito.mock(BangumiOAuthTokenService.class);
        org.mockito.Mockito.when(tokens.requireBangumiOauth(10L)).thenReturn(oauth);
        org.mockito.Mockito.when(tokens.findBangumiOauth(10L)).thenReturn(oauth);
        var lock = new CollectionWriteLock(dataSource);
        var tasks = new CollectionSyncTaskService(syncTasks,syncItems,lock,tokens, org.mockito.Mockito.mock(CollectionSyncEvents.class));
        var task = tasks.createOrGet(10L,2,"request-1");
        assertEquals(task.getId(),tasks.createOrGet(10L,2,"request-2").getId());
        var jdbc = new JdbcTemplate(dataSource);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,() ->
                jdbc.update("INSERT INTO user_bgm_collection_sync_task(user_id,subject_type,request_id,status,phase) VALUES(10,2,'other','QUEUED','SCANNING')"));
        var dto = new UpdateUserCollectionDto(); dto.setSubjectType(2); dto.setType(3);
        dto.setComment("pending comment");
        lock.execute(10L,42,() -> writer.save(10L,42,dto,null));
        var item = new CollectionSyncConflictEntity();
        item.setUserId(10L); item.setTaskId(task.getId()); item.setSubjectId(42); item.setSubjectType(2);
        item.setStatus(CollectionSyncItemStatus.PLANNED); item.setConflictVersion(0L);
        var remote = new com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto();
        remote.setId(42); remote.setType(2);
        remote.setInterest(new com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto.SubjectInterest());
        remote.getInterest().setId(12345L); remote.getInterest().setType(2);
        item.setScanSnapshot(writer.writeJson(remote));
        syncItems.insert(item);
        item = syncItems.selectById(item.getId()); // A fresh worker reads the persisted pagination snapshot.
        assertNotNull(item.getScanSnapshot());
        var client = org.mockito.Mockito.mock(com.ligg.api.bangumiapi.BangumiClient.class);
        org.mockito.Mockito.when(client.getSubject(42,"test")).thenAnswer(i -> remote);
        var oauthExecutor = org.mockito.Mockito.mock(BangumiOAuthExecutor.class);
        org.mockito.Mockito.when(oauthExecutor.execute(org.mockito.ArgumentMatchers.any(UserOauthEntity.class),
                org.mockito.ArgumentMatchers.<java.util.function.Function<String,Object>>any()))
                .thenAnswer(i -> ((java.util.function.Function<String,?>)i.getArgument(1)).apply("test"));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        var worker = new CollectionSyncItemExecutor(tasks,syncTasks,syncItems,mapper,writer,lock,
                oauthExecutor,client,new ObjectMapper(),transaction);
        worker.execute(task,item);
        org.mockito.Mockito.verifyNoInteractions(client);
        item = syncItems.selectById(item.getId());
        assertEquals(CollectionSyncItemStatus.CONFLICT,item.getStatus());
        assertEquals(CollectionRemoteSyncStatus.CONFLICT,writer.find(10L,42).getRemoteSyncStatus());
        // Ordinary edits retain the guard and their dirty fields.
        dto.setRate(8);
        lock.execute(10L,42,() -> writer.save(10L,42,dto,oauth));
        assertEquals(CollectionRemoteSyncStatus.CONFLICT,writer.find(10L,42).getRemoteSyncStatus());
        final long conflictId=item.getId(), oldVersion=item.getConflictVersion();
        assertThrows(IllegalArgumentException.class,() -> worker.resolve(10L,task.getId(),conflictId,oldVersion,5));
        item=syncItems.selectById(conflictId);
        assertTrue(item.getConflictVersion()>oldVersion);
        long acceptedVersion=item.getConflictVersion();
        worker.resolve(10L,task.getId(),conflictId,acceptedVersion,5);
        assertEquals(3,writer.find(10L,42).getType());
        // Simulate a fresh process using only persisted task/item state.
        var restarted = new CollectionSyncItemExecutor(tasks,syncTasks,syncItems,mapper,writer,lock,
                oauthExecutor,client,new ObjectMapper(),transaction);
        org.mockito.Mockito.doAnswer(i -> {
            var payload=(com.ligg.common.thirdparty.bangumi.request.UpdateCollectionBody)i.getArgument(2);
            remote.getInterest().setType(payload.getType()); remote.getInterest().setRate(payload.getRate());
            remote.getInterest().setComment(payload.getComment()); remote.getInterest().setTags(payload.getTags());
            return null;
        }).when(client).updateCollection(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(42),
                org.mockito.ArgumentMatchers.any());
        var failingItems = org.mockito.Mockito.spy(syncItems);
        org.mockito.Mockito.doAnswer(invocation -> {
            var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) invocation.getArgument(1);
            if (update.getParamNameValuePairs().values().stream()
                    .anyMatch(value -> "DONE".equals(String.valueOf(value))))
                throw new IllegalStateException("simulated crash before item confirmation");
            return syncItems.update(null, invocation.getArgument(1));
        }).when(failingItems).update(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
        var interrupted = new CollectionSyncItemExecutor(tasks,syncTasks,failingItems,mapper,writer,lock,
                oauthExecutor,client,new ObjectMapper(),transaction);
        assertThrows(IllegalStateException.class,() -> interrupted.execute(task,syncItems.selectById(conflictId)));
        assertEquals(3,writer.find(10L,42).getType()); // local completion rolls back with item completion
        assertEquals(CollectionSyncItemStatus.APPLY_PENDING,syncItems.selectById(conflictId).getStatus());
        assertEquals(5,remote.getInterest().getType()); // external write already happened
        restarted.execute(task,syncItems.selectById(conflictId)); // confirm, do not repeat PUT
        assertEquals(CollectionSyncItemStatus.DONE,syncItems.selectById(conflictId).getStatus());
        assertEquals(5,writer.find(10L,42).getType());
        assertEquals(8,writer.find(10L,42).getRate());
        assertEquals("pending comment",writer.find(10L,42).getComment());
        assertNull(writer.find(10L,42).getPendingPayload());
        tasks.summarize(task);
        assertEquals(BgmCollectionSyncStatus.SUCCESS, syncTasks.selectById(task.getId()).getStatus());
        assertEquals(1,syncTasks.selectById(task.getId()).getResolvedCount());
        assertEquals(task.getId(),tasks.createOrGet(10L,2,"request-1").getId());
        restarted.resolve(10L,task.getId(),conflictId,acceptedVersion,5);
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(1)).updateCollection(
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any());
    }

    @AfterEach
    void cleanup() {
        if (context != null) context.close();
        if (database != null) new JdbcTemplate(admin).execute("DROP DATABASE " + database);
    }

    @Test
    void migrationLocalSaveClearAndRestartRecoveryUseRealMapper() {
        var dto = new UpdateUserCollectionDto(); dto.setType(1); dto.setSubjectType(2);
        var lock = new CollectionWriteLock(dataSource);
        var row = lock.execute(10L, 42, () -> writer.save(10L, 42, dto, null));
        assertNull(row.getBgmInterestId());
        assertEquals(List.of(), mapper.selectUserSubjectInterest(10L, 42).getTags());
        assertEquals(2, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM user_bgm_collection", Integer.class));
        assertTrue(mapper.selectUserSubjectInterest(8L, 41).getPrivately());
        assertNotNull(mapper.selectPageByUserFilter(8L, 2, 2, null, 20, 0).get(0).getLocalUpdatedAt());
        var oauth = new UserOauthEntity(); oauth.setId(5L); oauth.setPlatformUid(6L);
        dto.setTags(List.of()); dto.setComment("");
        lock.execute(10L, 42, () -> writer.save(10L, 42, dto, oauth));
        new JdbcTemplate(dataSource).update("UPDATE user_bgm_collection SET next_retry_at=CURRENT_TIMESTAMP WHERE user_id=10");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO user_bgm_collection_sync_task(user_id, subject_type, request_id, status, phase) VALUES (10, 2, 'blocking-upload-test', 'RUNNING', 'APPLYING')");
        assertTrue(mapper.selectPendingUploads().isEmpty());
        jdbc.update("UPDATE user_bgm_collection_sync_task SET status='SUCCESS', finished_at=CURRENT_TIMESTAMP WHERE user_id=10 AND request_id='blocking-upload-test'");
        var persisted = mapper.selectPendingUploads().get(0);
        assertEquals(CollectionRemoteSyncStatus.PENDING, persisted.getRemoteSyncStatus());
        assertEquals(2L, persisted.getVersion());
        assertEquals(List.of(), writer.readPayload(persisted.getPendingPayload()).getTags());
        persisted.setPendingPayload(null); persisted.setRemoteBaseline(null); persisted.setNextRetryAt(null);
        persisted.setRemoteSyncStatus(CollectionRemoteSyncStatus.SYNCED);
        assertEquals(1, mapper.updateRemoteState(persisted));
        assertNull(writer.find(10L, 42).getPendingPayload());
        assertTrue(mapper.selectPendingUploads().isEmpty());
        persisted.setVersion(1L);
        assertEquals(0, mapper.updateRemoteState(persisted));
    }

    @Test
    void separateConnectionsCannotOverlapAndExceptionsReleaseLock() {
        var first = new CollectionWriteLock(dataSource);
        var second = new CollectionWriteLock(dataSource);
        assertThrows(IllegalArgumentException.class, () -> first.execute(10L, 42, () -> {
            assertThrows(IllegalStateException.class, () -> second.execute(10L, 42, () -> "blocked"));
            throw new IllegalArgumentException("interrupted operation");
        }));
        assertEquals("released", second.execute(10L, 42, () -> "released"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadRunsAfterCommitAndANewWorkerRecoversPersistedFailure() {
        var tokens = org.mockito.Mockito.mock(BangumiOAuthTokenService.class);
        var executor = org.mockito.Mockito.mock(BangumiOAuthExecutor.class);
        var client = org.mockito.Mockito.mock(com.ligg.api.bangumiapi.BangumiClient.class);
        var oauth = new UserOauthEntity(); oauth.setId(5L); oauth.setPlatformUid(6L);
        org.mockito.Mockito.when(tokens.findBangumiOauth(10L)).thenReturn(oauth);
        org.mockito.Mockito.when(executor.execute(org.mockito.ArgumentMatchers.any(UserOauthEntity.class),
                        org.mockito.ArgumentMatchers.any(java.util.function.Function.class)))
                .thenAnswer(invocation -> ((java.util.function.Function<String, ?>) invocation.getArgument(1)).apply("test"));
        org.mockito.Mockito.when(client.getSubject(42, "test")).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
                    "SELECT COUNT(*) FROM user_bgm_collection WHERE user_id=10", Integer.class));
            throw new IllegalStateException("temporary network failure");
        });
        var lock = new CollectionWriteLock(dataSource);
        var upload = new CollectionUploadService(writer, mapper, lock, tokens, executor, client, new ObjectMapper());
        var service = new com.ligg.flowclient.service.impl.UserBgmCollectionServiceImpl(mapper,
                new ObjectMapper(), lock, writer, upload, tokens, org.mockito.Mockito.mock(ImageBackfillService.class));
        var dto = new UpdateUserCollectionDto(); dto.setType(3); dto.setSubjectType(2);
        assertEquals(com.ligg.common.statuenum.CollectionRemoteSyncStatus.PENDING,
                service.updateCollection(10L, 42, dto).remoteSyncStatus());
        var persisted = writer.find(10L, 42);
        assertNotNull(persisted.getPendingPayload());
        assertEquals(CollectionRemoteSyncStatus.PENDING, persisted.getRemoteSyncStatus());
        new JdbcTemplate(dataSource).update("UPDATE user_bgm_collection SET next_retry_at=CURRENT_TIMESTAMP WHERE user_id=10");
        org.mockito.Mockito.reset(client);
        var detail = new com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto();
        var interest = new com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto.SubjectInterest();
        interest.setId(456L); interest.setType(3); interest.setUpdatedAt(100L); detail.setInterest(interest);
        org.mockito.Mockito.when(client.getSubject(42, "test")).thenReturn(detail);
        var restarted = new CollectionUploadService(writer, mapper, lock, tokens, executor, client, new ObjectMapper());
        restarted.retryPending();
        assertEquals(CollectionRemoteSyncStatus.SYNCED, writer.find(10L, 42).getRemoteSyncStatus());
        assertNull(writer.find(10L, 42).getPendingPayload());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).updateCollection(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }
}

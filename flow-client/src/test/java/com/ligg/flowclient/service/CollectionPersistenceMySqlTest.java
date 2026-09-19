package com.ligg.flowclient.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.UserOauthEntity;
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
        String base = query < 0 ? url : url.substring(0, query);
        String options = query < 0 ? "" : url.substring(query);
        dataSource = new DriverManagerDataSource(base + database + options,
                System.getenv("COLLECTION_TEST_MYSQL_USER"), System.getenv("COLLECTION_TEST_MYSQL_PASSWORD"));
        new ResourceDatabasePopulator(new ClassPathResource("db/collection_before_p1.sql")).execute(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO user_bgm_collection(user_id, subject_id, bgm_interest_id, type, comment, bgm_updated_at, is_private, `private`) VALUES (8, 41, 123, 2, '', 100, 1, 0)");
        Path migration = Path.of("../db/migrations/20260919_collection_local_first.sql");
        if (!migration.toFile().exists()) migration = Path.of("db/migrations/20260919_collection_local_first.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(dataSource);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(UserBgmCollectionMapper.class);
        config.addMapper(BangumiSubjectMapper.class);
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
        var persisted = mapper.selectPendingUploads().get(0);
        assertEquals("PENDING", persisted.getRemoteSyncStatus());
        assertEquals(2L, persisted.getVersion());
        assertEquals(List.of(), writer.readPayload(persisted.getPendingPayload()).getTags());
        persisted.setPendingPayload(null); persisted.setRemoteBaseline(null); persisted.setNextRetryAt(null);
        persisted.setRemoteSyncStatus("SYNCED");
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
        assertEquals("PENDING", persisted.getRemoteSyncStatus());
        new JdbcTemplate(dataSource).update("UPDATE user_bgm_collection SET next_retry_at=CURRENT_TIMESTAMP WHERE user_id=10");
        org.mockito.Mockito.reset(client);
        var detail = new com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto();
        var interest = new com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto.SubjectInterest();
        interest.setId(456L); interest.setType(3); interest.setUpdatedAt(100L); detail.setInterest(interest);
        org.mockito.Mockito.when(client.getSubject(42, "test")).thenReturn(detail);
        var restarted = new CollectionUploadService(writer, mapper, lock, tokens, executor, client, new ObjectMapper());
        restarted.retryPending();
        assertEquals("SYNCED", writer.find(10L, 42).getRemoteSyncStatus());
        assertNull(writer.find(10L, 42).getPendingPayload());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).updateCollection(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }
}

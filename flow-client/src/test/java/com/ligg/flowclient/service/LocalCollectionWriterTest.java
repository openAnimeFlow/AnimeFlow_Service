package com.ligg.flowclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.entity.UserBgmCollectionEntity;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalCollectionWriterTest {
    private final UserBgmCollectionMapper mapper = mock(UserBgmCollectionMapper.class);
    private final BangumiSubjectMapper subjects = mock(BangumiSubjectMapper.class);
    private final LocalCollectionWriter writer = new LocalCollectionWriter(mapper, subjects, new ObjectMapper());

    @BeforeEach
    void subject() {
        var subject = new BangumiSubjectEntity();
        subject.setId(42);
        subject.setType(4);
        when(subjects.selectById(42)).thenReturn(subject);
    }

    @Test
    void createsLocalCollectionWithoutRemoteIdOrFakeRemoteTime() {
        var dto = new UpdateUserCollectionDto();
        dto.setType(1);
        var row = writer.save(10L, 42, dto, null);
        assertNull(row.getBgmInterestId());
        assertNull(row.getBgmUpdatedAt());
        assertNull(row.getSyncTime());
        assertEquals(4, row.getSubjectType());
        assertEquals("LOCAL_ONLY", row.getRemoteSyncStatus());
        assertEquals(1L, row.getVersion());
        verify(mapper).insert(row);
    }

    @Test
    void partialUpdatePreservesCategoryAndCoalescesPendingFieldsIncludingClears() {
        var row = new UserBgmCollectionEntity();
        row.setType(3);
        row.setSubjectType(4);
        row.setVersion(2L);
        row.setPendingPayload("{\"type\":3,\"comment\":\"old\"}");
        when(mapper.selectOne(any())).thenReturn(row);
        when(mapper.updateLocal(any())).thenReturn(1);
        var dto = new UpdateUserCollectionDto();
        dto.setTags(List.of());
        dto.setComment("");
        dto.setRate(0);
        var saved = writer.save(10L, 42, dto, null);
        assertEquals(3, saved.getType());
        assertEquals("[]", saved.getTags());
        var pending = writer.readPayload(saved.getPendingPayload());
        assertEquals(3, pending.getType());
        assertEquals("", pending.getComment());
        assertEquals(0, pending.getRate());
        assertEquals(List.of(), pending.getTags());
        verify(mapper).updateLocal(saved);
    }

    @Test
    void emptyTagsAloneIsAnUpdate() {
        var dto = new UpdateUserCollectionDto();
        dto.setTags(List.of());
        assertTrue(dto.hasUpdateField());
    }

    @Test
    void rejectsMissingInitialTypeMismatchedSubjectTypeAndUnsupportedProgressBeforeWriting() {
        var dto = new UpdateUserCollectionDto();
        dto.setRate(8);
        assertThrows(IllegalArgumentException.class, () -> writer.save(10L, 42, dto, null));
        dto.setType(1);
        dto.setSubjectType(5);
        assertThrows(IllegalArgumentException.class, () -> writer.save(10L, 42, dto, null));
        dto.setSubjectType(4);
        dto.setProgress(true);
        assertThrows(IllegalArgumentException.class, () -> writer.save(10L, 42, dto, null));
        verify(mapper, never()).insert(any(UserBgmCollectionEntity.class));
        verify(mapper, never()).updateLocal(any());
    }
}

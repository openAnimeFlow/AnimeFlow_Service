package com.ligg.flowclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiv0api.BangumiV0Client;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageBackfillServiceTest {

    @Mock
    private BangumiSubjectMapper bangumiSubjectMapper;

    @Mock
    private BangumiV0Client bangumiV0Client;

    private ImageBackfillService service;

    @BeforeEach
    void setUp() {
        service = new ImageBackfillService(bangumiSubjectMapper, bangumiV0Client, new ObjectMapper());
    }

    @Test
    void resolve_shouldReturnFromJson_whenValid() {
        String json = "{\"large\":\"http://example.com/l.jpg\",\"common\":\"http://example.com/c.jpg\","
                + "\"medium\":\"http://example.com/m.jpg\",\"small\":\"http://example.com/s.jpg\","
                + "\"grid\":\"http://example.com/g.jpg\"}";

        CoverImages result = service.resolve(json, 1);

        assertThat(result.getLarge()).isEqualTo("http://example.com/l.jpg");
        assertThat(result.getCommon()).isEqualTo("http://example.com/c.jpg");
        verify(bangumiV0Client, never()).getSubjectImageUrl(anyInt(), any());
    }

    @Test
    void resolve_shouldReturnEmpty_whenJsonIsNull() {
        CoverImages result = service.resolve(null, 1);

        assertThat(result.getLarge()).isEmpty();
        assertThat(result.getCommon()).isEmpty();
        assertThat(result.getMedium()).isEmpty();
        assertThat(result.getSmall()).isEmpty();
        assertThat(result.getGrid()).isEmpty();
    }

    @Test
    void resolve_shouldFetchFromApi_whenJsonIsEmpty() {
        when(bangumiV0Client.getSubjectImageUrl(1, SubjectImageType.LARGE))
                .thenReturn("http://example.com/large.jpg");
        when(bangumiV0Client.getSubjectImageUrl(1, SubjectImageType.COMMON))
                .thenReturn("http://example.com/common.jpg");
        when(bangumiV0Client.getSubjectImageUrl(1, SubjectImageType.MEDIUM))
                .thenReturn("http://example.com/medium.jpg");
        when(bangumiV0Client.getSubjectImageUrl(1, SubjectImageType.SMALL))
                .thenReturn("http://example.com/small.jpg");
        when(bangumiV0Client.getSubjectImageUrl(1, SubjectImageType.GRID))
                .thenReturn("http://example.com/grid.jpg");

        CoverImages result = service.resolve("", 1);

        assertThat(result.getLarge()).isEqualTo("http://example.com/large.jpg");
        verify(bangumiSubjectMapper).updateById(any(BangumiSubjectEntity.class));
    }

    @Test
    void resolve_shouldNotWriteDb_whenAllApiCallsFail() {
        when(bangumiV0Client.getSubjectImageUrl(anyInt(), any()))
                .thenThrow(new RuntimeException("API error"));

        CoverImages result = service.resolve("", 1);

        assertThat(result.getLarge()).isEmpty();
        verify(bangumiSubjectMapper, never()).updateById(any(BangumiSubjectEntity.class));
    }

    @Test
    void resolve_shouldFetchApi_whenJsonIsStaleNull() {
        CoverImages result = service.resolve("null", 1);

        assertThat(result.getLarge()).isEmpty();
        // "null" 不是有效 JSON，会走 API 但不会写 DB
        verify(bangumiV0Client, Mockito.atLeastOnce()).getSubjectImageUrl(anyInt(), any());
        verify(bangumiSubjectMapper, never()).updateById(any(BangumiSubjectEntity.class));
    }

    @Test
    void resolve_shouldUseDoubleCheck_whenEntityAlreadyHasImages() {
        BangumiSubjectEntity entity = new BangumiSubjectEntity();
        entity.setId(1);
        entity.setImages("{\"large\":\"http://example.com/cached.jpg\"}");
        when(bangumiSubjectMapper.selectById(1)).thenReturn(entity);

        CoverImages result = service.resolve("", 1);

        assertThat(result.getLarge()).isEqualTo("http://example.com/cached.jpg");
        verify(bangumiV0Client, never()).getSubjectImageUrl(anyInt(), any());
        verify(bangumiSubjectMapper, never()).updateById(any(BangumiSubjectEntity.class));
    }
}

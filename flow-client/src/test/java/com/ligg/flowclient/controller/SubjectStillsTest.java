package com.ligg.flowclient.controller;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.constants.Constants;
import com.ligg.flowclient.controller.bangumi.SubjectsController;
import com.ligg.flowclient.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SubjectStillsTest {

    private final BangumiService service = mock(BangumiService.class);
    private final MockMvc mvc = standaloneSetup(new SubjectsController(
            mock(BangumiClient.class), mock(CacheService.class), service,
            mock(JwtTokenService.class), mock(BangumiOAuthTokenService.class), mock(BangumiOAuthExecutor.class)))
            .build();

    @Test
    void immutableBackdropsReturnCdnUrlsWithoutChangingSource() throws Exception {
        String originalUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg";
        List<String> backdrops = List.of(originalUrl);
        when(service.getSubjectStills(42)).thenReturn(backdrops);

        mvc.perform(get("/api/v1/bangumi/subjects/42/stills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value(Constants.WSRV_CDN + "/?url=" + originalUrl));

        assertEquals(originalUrl, backdrops.getFirst());
    }

    @Test
    void immutableEmptyBackdropsReturnEmptySuccess() throws Exception {
        when(service.getSubjectStills(42)).thenReturn(List.of());

        mvc.perform(get("/api/v1/bangumi/subjects/42/stills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}

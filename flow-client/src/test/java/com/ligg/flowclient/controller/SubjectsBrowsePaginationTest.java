package com.ligg.flowclient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.handler.GlobalExceptionHandler;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.enums.SubjectBrowseSort;
import com.ligg.flowclient.controller.bangumi.SubjectsController;
import com.ligg.flowclient.mapper.BangumiEpisodeMapper;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.mapper.UserBgmCollectionMapper;
import com.ligg.flowclient.module.dto.SubjectBrowseRow;
import com.ligg.flowclient.service.*;
import com.ligg.flowclient.service.impl.BangumiServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SubjectsBrowsePaginationTest {

    private final BangumiSubjectMapper subjectMapper = mock(BangumiSubjectMapper.class);
    private final ImageBackfillService imageBackfillService = mock(ImageBackfillService.class);
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BangumiServiceImpl service = new BangumiServiceImpl(
                mock(BangumiEpisodeMapper.class), subjectMapper, new ObjectMapper(),
                imageBackfillService, mock(UserBgmCollectionMapper.class), mock(UserEpisodeWatchService.class));
        SubjectsController controller = new SubjectsController(
                mock(BangumiClient.class), mock(BangumiCacheService.class), service,
                mock(JwtTokenService.class), mock(BangumiOAuthTokenService.class), mock(BangumiOAuthExecutor.class));

        // Exercise the controller's @Validated constraints as in the running application.
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
        DefaultFormattingConversionService conversion = new DefaultFormattingConversionService();
        conversion.addConverter(String.class, SubjectBrowseSort.class, SubjectBrowseSort::fromValue);
        mvc = standaloneSetup(proxy.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setConversionService(conversion)
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {101, Integer.MAX_VALUE})
    void beyondPageLimitReturnsEmptySuccessWithoutDatabaseAccess(int page) throws Exception {
        mvc.perform(get("/api/v1/bangumi/subjects").param("page", Integer.toString(page)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
        verifyNoInteractions(subjectMapper, imageBackfillService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositivePageStillFailsValidation(int page) throws Exception {
        mvc.perform(get("/api/v1/bangumi/subjects").param("page", Integer.toString(page)))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(subjectMapper, imageBackfillService);
    }

    @Test
    void lastAllowedPageStillReturnsSubjects() throws Exception {
        when(subjectMapper.countBrowseSubjects(2, null, null)).thenReturn(2401);
        SubjectBrowseRow row = new SubjectBrowseRow();
        row.setId(42);
        when(subjectMapper.selectBrowseSubjects("rank", 2, null, null, 24, 2376))
                .thenReturn(List.of(row));
        when(imageBackfillService.resolve(null, 42, null)).thenReturn(new CoverImages());

        mvc.perform(get("/api/v1/bangumi/subjects").param("page", "100"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.data[0].id").value(42))
                .andExpect(jsonPath("$.data.total").value(100));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "24,1", "25,2", "2400,100", "2401,100", "2147483647,100"})
    void totalPagesRoundsUpAndNeverExceedsLimit(int records, int pages) throws Exception {
        when(subjectMapper.countBrowseSubjects(2, null, null)).thenReturn(records);

        mvc.perform(get("/api/v1/bangumi/subjects"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(pages));
    }
}

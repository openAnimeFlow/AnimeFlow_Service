package com.ligg.flowclient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.flowclient.module.dto.SearchSuggestionRow;
import com.ligg.flowclient.module.dto.SubjectRecommendationRow;
import com.ligg.flowclient.module.dto.SubjectRelationRow;
import com.ligg.flowclient.module.dto.SubjectSearchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BangumiSubjectMapper extends BaseMapper<BangumiSubjectEntity> {

    List<SearchSuggestionRow> selectSearchSuggestions(@Param("keyword") String keyword,
                                                    @Param("type") int type,
                                                    @Param("limit") int limit);

    List<SubjectSearchRow> selectLocalSearchSubjects(@Param("keyword") String keyword,
                                                     @Param("compactKeyword") String compactKeyword,
                                                     @Param("anchorKeyword") String anchorKeyword,
                                                     @Param("anchoredTitleSearch") boolean anchoredTitleSearch,
                                                     @Param("exactSubjectId") Integer exactSubjectId,
                                                     @Param("types") List<Integer> types,
                                                     @Param("includeNsfw") boolean includeNsfw,
                                                     @Param("tags") List<String> tags,
                                                     @Param("metaTags") List<String> metaTags,
                                                     @Param("minYear") Integer minYear,
                                                     @Param("maxYear") Integer maxYear,
                                                     @Param("minRating") Double minRating,
                                                     @Param("maxRating") Double maxRating,
                                                     @Param("minRank") Integer minRank,
                                                     @Param("maxRank") Integer maxRank,
                                                     @Param("sort") String sort,
                                                     @Param("limit") int limit,
                                                     @Param("offset") int offset);

    Integer countLocalSearchSubjects(@Param("keyword") String keyword,
                                     @Param("compactKeyword") String compactKeyword,
                                     @Param("anchorKeyword") String anchorKeyword,
                                     @Param("anchoredTitleSearch") boolean anchoredTitleSearch,
                                     @Param("exactSubjectId") Integer exactSubjectId,
                                     @Param("types") List<Integer> types,
                                     @Param("includeNsfw") boolean includeNsfw,
                                     @Param("tags") List<String> tags,
                                     @Param("metaTags") List<String> metaTags,
                                     @Param("minYear") Integer minYear,
                                     @Param("maxYear") Integer maxYear,
                                     @Param("minRating") Double minRating,
                                     @Param("maxRating") Double maxRating,
                                     @Param("minRank") Integer minRank,
                                     @Param("maxRank") Integer maxRank);

    IPage<SubjectRelationRow> selectRelatedSubjects(IPage<?> page, @Param("subjectId") Integer subjectId ,@Param("type") Integer type);

    List<SubjectRecommendationRow> selectRecommendedSubjects(@Param("subjectId") Integer subjectId,
                                                             @Param("type") Integer type,
                                                             @Param("nsfw") Boolean nsfw,
                                                             @Param("releaseYear") Integer releaseYear,
                                                             @Param("tagNames") List<String> tagNames,
                                                             @Param("metaTagNames") List<String> metaTagNames,
                                                             @Param("limit") int limit,
                                                             @Param("offset") int offset);

}

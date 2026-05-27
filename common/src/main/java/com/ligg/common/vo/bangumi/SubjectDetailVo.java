package com.ligg.common.vo.bangumi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ligg.common.thirdparty.SubjectDetailDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubjectDetailVo extends SubjectDetailDto {
}

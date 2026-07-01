/**
 * @author Ligg
 * @date 2026/7/2 02:48
 */
package com.ligg.flowclient.module.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSubjectInterestRow {
    private Long id;
    private Integer rate;
    private Integer type;
    private String comment;
    private List<String> tags;
    private Integer epStatus;
    private Integer volStatus;
    private Boolean privately;
    private Long bgmUpdatedAt;
}

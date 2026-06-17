package com.ligg.common.vo.bangumi;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SearchSuggestionsVo {

    private List<Item> data = new ArrayList<>();

    @Data
    public static class Item {
        private Integer id;
        private String name;
        private String nameCn;
    }
}

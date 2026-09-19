package com.ligg.flowclient.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Objects;

/** Bangumi may reorder tags; their order is not part of collection content. */
final class CollectionFieldComparison {
    private CollectionFieldComparison() {}

    static boolean same(String field, JsonNode expected, JsonNode actual) {
        if ("tags".equals(field) && expected != null && actual != null
                && expected.isArray() && actual.isArray()) {
            var expectedTags = new HashSet<JsonNode>();
            var actualTags = new HashSet<JsonNode>();
            expected.forEach(expectedTags::add);
            actual.forEach(actualTags::add);
            return expectedTags.equals(actualTags);
        }
        return Objects.equals(expected, actual);
    }
}

package com.ligg.flowscheduler.archive;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * Bangumi Archive jsonlines 资源类型枚举，{@link #order} 决定同步顺序。
 *
 * @author Ligg
 */
@Getter
@RequiredArgsConstructor
public enum ArchiveDataType {

    SUBJECT("subject", 1),
    CHARACTER("character", 2),
    PERSON("person", 3),
    EPISODE("episode", 4),
    SUBJECT_CHARACTER("subject-characters", 5),
    SUBJECT_PERSON("subject-persons", 6),
    SUBJECT_RELATION("subject-relations", 7),
    PERSON_CHARACTER("person-characters", 8),
    PERSON_RELATION("person-relations", 9);

    private final String fileSuffix;
    private final int order;

    public static Optional<ArchiveDataType> fromAssetName(String assetName, String dumpName) {
        if (assetName == null || dumpName == null) {
            return Optional.empty();
        }
        String prefix = dumpName + "-";
        if (!assetName.startsWith(prefix) || !assetName.endsWith(".jsonlines")) {
            return Optional.empty();
        }
        String suffix = assetName.substring(prefix.length(), assetName.length() - ".jsonlines".length());
        return Arrays.stream(values())
                .filter(type -> type.fileSuffix.equals(suffix))
                .findFirst();
    }

    public static Comparator<ArchiveDataType> orderComparator() {
        return Comparator.comparingInt(ArchiveDataType::getOrder);
    }
}

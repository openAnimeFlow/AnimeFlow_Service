package com.ligg.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InfoboxParserTest {

    @Test
    void toInfo_shouldExtractStandardFields() {
        String infobox = """
                {{Infobox animanga/TVAnime
                |中文名= 呪術廻戦
                |话数= 24
                |放送开始= 2020年10月2日
                |导演= 朴性厚
                |原作= 芥見下々（集英社「週刊少年ジャンプ」連載）
                |人物设定= 平松禎史
                |动画制作= MAPPA
                }}""";

        String info = InfoboxParser.toInfo(infobox);

        assertThat(info).isEqualTo("24话 / 2020年10月2日 / 朴性厚 / 芥見下々（集英社「週刊少年ジャンプ」連載） / 平松禎史");
    }

    @Test
    void toInfo_shouldSkipMissingKeys() {
        String infobox = """
                {{Infobox animanga/TVAnime
                |中文名= 测试
                |放送开始= 2020年1月1日
                |导演= 某导演
                }}""";

        String info = InfoboxParser.toInfo(infobox);

        assertThat(info).isEqualTo("2020年1月1日 / 某导演");
    }

    @Test
    void toInfo_shouldReturnEmpty_whenEmpty() {
        assertThat(InfoboxParser.toInfo("")).isEmpty();
        assertThat(InfoboxParser.toInfo(null)).isEmpty();
    }

    @Test
    void toInfo_shouldReturnEmpty_whenOnlyHeader() {
        assertThat(InfoboxParser.toInfo("{{Infobox animanga/TVAnime}}")).isEmpty();
    }

    @Test
    void toInfo_shouldNotDoubleAppendEpisodeSuffix() {
        String infobox = """
                {{Infobox
                |话数= 12话
                }}""";

        assertThat(InfoboxParser.toInfo(infobox)).isEqualTo("12话");
    }

    @Test
    void toInfo_shouldAppendEpisodeSuffix() {
        String infobox = """
                {{Infobox
                |话数= 12
                }}""";

        assertThat(InfoboxParser.toInfo(infobox)).isEqualTo("12话");
    }

    @Test
    void toMap_shouldParseAllFields() {
        String infobox = """
                {{Infobox animanga/Manga
                |中文名= 测试漫画
                |话数= 10
                |放送开始= 2019年
                |别名={
                [别名1]
                [别名2]
                }
                }}""";

        Map<String, String> map = InfoboxParser.toMap(infobox);

        assertThat(map).containsEntry("中文名", "测试漫画");
        assertThat(map).containsEntry("话数", "10");
        assertThat(map).containsEntry("放送开始", "2019年");
        // 列表行 '|别名={' 只有 key，value 是 '{' 
        assertThat(map).containsEntry("别名", "{");
    }

    @Test
    void toMap_shouldHandleCrLf() {
        String infobox = "{{Infobox\r\n|key= value\r\n}}";

        Map<String, String> map = InfoboxParser.toMap(infobox);

        assertThat(map).containsEntry("key", "value");
    }

    @Test
    void toMap_shouldHandleMultipleEqualsInValue() {
        String infobox = "{{Infobox\n|key= a=b=c\n}}";

        Map<String, String> map = InfoboxParser.toMap(infobox);

        assertThat(map).containsEntry("key", "a=b=c");
    }
}

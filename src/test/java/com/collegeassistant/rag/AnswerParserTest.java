package com.collegeassistant.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnswerParserTest {

    private final AnswerParser parser = new AnswerParser(new ObjectMapper());

    @Test
    void parsesPlainJson() {
        var p = parser.parse("{\"answerable\": true, \"answer\": \"75% is required.\", \"sources\": [1, 3]}");
        assertThat(p).isPresent();
        assertThat(p.get().answerable()).isTrue();
        assertThat(p.get().answer()).isEqualTo("75% is required.");
        assertThat(p.get().sources()).containsExactly(1, 3);
    }

    @Test
    void toleratesMarkdownFencesAndChatter() {
        var p = parser.parse("Sure!\n```json\n{\"answerable\": false, \"answer\": \"\", \"sources\": []}\n```");
        assertThat(p).isPresent();
        assertThat(p.get().answerable()).isFalse();
    }

    @Test
    void garbageOrMissingFieldsGiveEmpty() {
        assertThat(parser.parse("I think the answer is 75%.")).isEmpty();
        assertThat(parser.parse("{\"answer\": \"x\"}")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
    }
}

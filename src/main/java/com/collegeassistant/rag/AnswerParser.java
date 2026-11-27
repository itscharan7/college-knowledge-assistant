package com.collegeassistant.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Parses the model's JSON reply. Tolerates markdown fences and surrounding text; returns empty on anything
 * unparseable, which the caller treats as "not found" (we never show unparseable output as an answer).
 */
@Component
public class AnswerParser {

    public record ParsedAnswer(boolean answerable, String answer, List<Integer> sources) {
    }

    private final ObjectMapper mapper;

    public AnswerParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<ParsedAnswer> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(raw.substring(start, end + 1));
            if (!node.has("answerable") || !node.get("answerable").isBoolean()) {
                return Optional.empty();
            }
            boolean answerable = node.get("answerable").asBoolean();
            String answer = node.has("answer") && !node.get("answer").isNull() ? node.get("answer").asText() : "";
            List<Integer> sources = new ArrayList<>();
            if (node.has("sources") && node.get("sources").isArray()) {
                for (JsonNode s : node.get("sources")) {
                    if (s.isInt()) {
                        sources.add(s.asInt());
                    }
                }
            }
            return Optional.of(new ParsedAnswer(answerable, answer.strip(), sources));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

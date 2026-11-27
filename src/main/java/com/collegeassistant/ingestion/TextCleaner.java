package com.collegeassistant.ingestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns raw per-page PDF text into clean paragraphs while preserving page numbers:
 * normalises characters, joins hyphenated line breaks, removes repeated headers/footers,
 * converts detected tables, rebuilds paragraphs and drops near-empty pages.
 */
@Component
public class TextCleaner {

    private static final int MIN_PAGE_CHARS = 30;
    private static final int MIN_PAGES_FOR_HEADER_DETECTION = 4;

    private final TableFormatter tableFormatter;

    public TextCleaner(TableFormatter tableFormatter) {
        this.tableFormatter = tableFormatter;
    }

    public record Cleaned(List<PageText> pages, int tablesFound) {
        public int totalChars() {
            return pages.stream().mapToInt(p -> p.text().length()).sum();
        }
    }

    public Cleaned clean(List<PageText> raw) {
        List<List<String>> pageLines = new ArrayList<>();
        for (PageText p : raw) {
            pageLines.add(normalizeToLines(p.text()));
        }
        Set<String> repeated = repeatedLines(pageLines);

        List<PageText> out = new ArrayList<>();
        int tables = 0;
        for (int i = 0; i < raw.size(); i++) {
            List<String> lines = new ArrayList<>();
            for (String l : pageLines.get(i)) {
                if (repeated.isEmpty() || !repeated.contains(signature(l))) {
                    lines.add(l);
                }
            }
            TableFormatter.Result t = tableFormatter.format(lines);
            tables += t.tablesFound();
            String text = toParagraphs(t.lines());
            if (text.length() >= MIN_PAGE_CHARS) {
                out.add(new PageText(raw.get(i).pageNumber(), text));
            }
        }
        return new Cleaned(out, tables);
    }

    private List<String> normalizeToLines(String text) {
        String t = text == null ? "" : text;
        t = t.replace("\r\n", "\n").replace('\r', '\n')
                .replace(' ', ' ').replace("­", "");
        // join words hyphenated across a line break: "exam-\nination" -> "examination"
        t = t.replaceAll("(\\p{L})-\\n(\\p{Ll})", "$1$2");
        return new ArrayList<>(List.of(t.split("\n", -1)));
    }

    /** Lines (digits normalised) that appear on more than half the pages: headers, footers, page numbers. */
    private Set<String> repeatedLines(List<List<String>> pageLines) {
        Set<String> result = new HashSet<>();
        if (pageLines.size() < MIN_PAGES_FOR_HEADER_DETECTION) {
            return result;
        }
        Map<String, Integer> pagesWith = new HashMap<>();
        for (List<String> lines : pageLines) {
            Set<String> seen = new HashSet<>();
            for (String l : lines) {
                String s = signature(l);
                if (!s.isEmpty() && s.length() < 120 && seen.add(s)) {
                    pagesWith.merge(s, 1, Integer::sum);
                }
            }
        }
        int threshold = pageLines.size() / 2;
        pagesWith.forEach((sig, n) -> {
            if (n > threshold) {
                result.add(sig);
            }
        });
        return result;
    }

    private String signature(String line) {
        return line.strip().replaceAll("\\d+", "#").replaceAll("\\s+", " ");
    }

    /** Joins wrapped lines into paragraphs; table rows stay one per line. Paragraphs are separated by a blank line. */
    private String toParagraphs(List<String> lines) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        List<String> rows = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                flushPara(para, paragraphs);
                flushRows(rows, paragraphs);
            } else if (line.startsWith(TableFormatter.ROW_PREFIX)) {
                flushPara(para, paragraphs);
                rows.add(line);
            } else {
                flushRows(rows, paragraphs);
                if (para.length() > 0) {
                    para.append(' ');
                }
                para.append(line.replaceAll("[ \\t]+", " "));
            }
        }
        flushPara(para, paragraphs);
        flushRows(rows, paragraphs);
        return String.join("\n\n", paragraphs).strip();
    }

    private void flushPara(StringBuilder para, List<String> out) {
        if (para.length() > 0) {
            out.add(para.toString());
            para.setLength(0);
        }
    }

    private void flushRows(List<String> rows, List<String> out) {
        if (!rows.isEmpty()) {
            out.add(String.join("\n", rows));
            rows.clear();
        }
    }
}

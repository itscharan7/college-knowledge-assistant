package com.collegeassistant.ingestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Best-effort table handling for DIGITAL PDFs, without Tabula (see DECISIONS.md: Tabula-java depends on
 * PDFBox 2.x and conflicts with PDFBox 3.x).
 *
 * Heuristic: a run of 3+ consecutive lines that each split into the same number (>=2) of cells on
 * tabs, pipes or 2+ spaces is treated as a table; the first line is the header. Each data row becomes
 * one self-contained line "• Header: value; Header: value", so row meaning is never split from its headers.
 * Anything that does not match is left untouched. This is a heuristic: tables with unusual layouts
 * may not be detected. Detection results are logged per document.
 */
@Component
public class TableFormatter {

    public static final String ROW_PREFIX = "• ";
    private static final Pattern CELL_SPLIT = Pattern.compile("\\t|\\s*\\|\\s*|\\s{2,}");
    private static final int MIN_ROWS = 3;

    public record Result(List<String> lines, int tablesFound) {
    }

    public Result format(List<String> lines) {
        try {
            return doFormat(lines);
        } catch (RuntimeException e) {
            return new Result(lines, 0); // fall back to plain text
        }
    }

    private Result doFormat(List<String> lines) {
        List<String> out = new ArrayList<>();
        int tables = 0;
        int i = 0;
        while (i < lines.size()) {
            List<String> cells = cells(lines.get(i));
            if (cells == null) {
                out.add(lines.get(i));
                i++;
                continue;
            }
            int n = cells.size();
            int j = i;
            List<List<String>> run = new ArrayList<>();
            while (j < lines.size()) {
                List<String> c = cells(lines.get(j));
                if (c == null || c.size() != n) {
                    break;
                }
                run.add(c);
                j++;
            }
            if (run.size() >= MIN_ROWS) {
                tables++;
                List<String> header = run.get(0);
                for (int r = 1; r < run.size(); r++) {
                    List<String> row = run.get(r);
                    StringBuilder sb = new StringBuilder(ROW_PREFIX);
                    for (int k = 0; k < n; k++) {
                        if (k > 0) {
                            sb.append("; ");
                        }
                        sb.append(header.get(k)).append(": ").append(row.get(k));
                    }
                    out.add(sb.toString());
                }
                i = j;
            } else {
                out.add(lines.get(i));
                i++;
            }
        }
        return new Result(out, tables);
    }

    /** Returns the cells of a line, or null if it does not look tabular (fewer than 2 non-empty cells). */
    private List<String> cells(String line) {
        String t = line.strip();
        if (t.isEmpty()) {
            return null;
        }
        List<String> parts = Arrays.stream(CELL_SPLIT.split(t)).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return parts.size() >= 2 ? parts : null;
    }
}

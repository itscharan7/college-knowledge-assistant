package com.collegeassistant.ingestion;

import com.collegeassistant.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Splits page text into overlapping chunks.
 * - Size/overlap are configured in TOKENS and converted with a rough chars-per-token ratio (default 4).
 * - Packing works on paragraphs/sentences, so sentences are only split if a single one is longer than a chunk.
 * - Each chunk is built from ONE page only, so its page number is exact.
 * - Overlap: the tail of the previous chunk is repeated at the start of the next, so a fact that straddles
 *   a boundary appears whole in at least one chunk.
 */
@Component
public class Chunker {

    private final int maxChars;
    private final int overlapChars;

    @Autowired
    public Chunker(AppProperties props) {
        this(props.getChunking().getSizeTokens() * props.getChunking().getCharsPerToken(),
                props.getChunking().getOverlapTokens() * props.getChunking().getCharsPerToken());
    }

    public Chunker(int maxChars, int overlapChars) {
        if (maxChars < 50) {
            throw new IllegalArgumentException("chunk size too small");
        }
        this.maxChars = maxChars;
        this.overlapChars = Math.max(0, Math.min(overlapChars, maxChars / 2));
    }

    public List<Chunk> chunk(List<PageText> pages) {
        List<Chunk> out = new ArrayList<>();
        int index = 0;
        for (PageText page : pages) {
            for (String text : chunkPage(page.text())) {
                out.add(new Chunk(page.pageNumber(), index++, text));
            }
        }
        return out;
    }

    List<String> chunkPage(String pageText) {
        List<String> segments = toSegments(pageText);
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentLen = 0;
        int newSegments = 0;

        for (String seg : segments) {
            int added = seg.length() + (current.isEmpty() ? 0 : 1);
            if (currentLen + added > maxChars && !current.isEmpty()) {
                if (newSegments > 0) {
                    chunks.add(String.join(" ", current).strip());
                    current = overlapTail(current);
                } else {
                    current = new ArrayList<>(); // only overlap left and still does not fit: drop it
                }
                currentLen = joinedLength(current);
                newSegments = 0;
                added = seg.length() + (current.isEmpty() ? 0 : 1);
            }
            current.add(seg);
            currentLen += added;
            newSegments++;
        }
        if (newSegments > 0) {
            chunks.add(String.join(" ", current).strip());
        }
        return chunks;
    }

    /** Paragraphs first; paragraphs that are too long are split into sentences/lines, then hard-split. */
    private List<String> toSegments(String text) {
        List<String> segs = new ArrayList<>();
        for (String para : text.split("\n\\s*\n")) {
            String p = para.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= maxChars) {
                segs.add(p);
                continue;
            }
            for (String piece : p.split("(?<=[.!?])\\s+|\\n")) {
                String s = piece.strip();
                while (s.length() > maxChars) {
                    segs.add(s.substring(0, maxChars));
                    s = s.substring(maxChars);
                }
                if (!s.isEmpty()) {
                    segs.add(s);
                }
            }
        }
        return segs;
    }

    private List<String> overlapTail(List<String> segments) {
        List<String> tail = new ArrayList<>();
        int len = 0;
        for (int i = segments.size() - 1; i >= 0; i--) {
            int add = segments.get(i).length() + (tail.isEmpty() ? 0 : 1);
            if (len + add > overlapChars) {
                break;
            }
            tail.add(0, segments.get(i));
            len += add;
        }
        return tail;
    }

    private int joinedLength(List<String> segs) {
        if (segs.isEmpty()) {
            return 0;
        }
        return segs.stream().mapToInt(String::length).sum() + segs.size() - 1;
    }
}

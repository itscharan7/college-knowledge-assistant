package com.collegeassistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    private final Chunker chunker = new Chunker(300, 60);

    private static String longPage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            sb.append("Sentence number ").append(i).append(" talks about attendance rules. ");
        }
        return sb.toString().trim();
    }

    @Test
    void splitsLongPageAndRespectsMaxSize() {
        List<Chunk> chunks = chunker.chunk(List.of(new PageText(3, longPage())));
        assertThat(chunks.size()).isGreaterThan(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(300));
        assertThat(chunks).noneMatch(c -> c.text().isBlank());
    }

    @Test
    void chunksNeverCrossPagesAndKeepPageNumbers() {
        List<Chunk> chunks = chunker.chunk(List.of(new PageText(3, longPage()),
                new PageText(4, "Short page two about fees.")));
        assertThat(chunks).filteredOn(c -> c.text().contains("fees")).allMatch(c -> c.pageNumber() == 4);
        assertThat(chunks).filteredOn(c -> c.pageNumber() == 3).hasSize(chunks.size() - 1);
    }

    @Test
    void consecutiveChunksOverlap() {
        List<Chunk> chunks = chunker.chunk(List.of(new PageText(1, longPage())));
        boolean overlap = false;
        for (int i = 1; i < chunks.size(); i++) {
            String firstSentence = chunks.get(i).text().split("(?<=[.!?])\\s+")[0];
            overlap |= chunks.get(i - 1).text().contains(firstSentence);
        }
        assertThat(overlap).isTrue();
    }

    @Test
    void chunkIndexIsSequentialAcrossDocument() {
        List<Chunk> chunks = chunker.chunk(List.of(new PageText(1, longPage()), new PageText(2, longPage())));
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
    }

    @Test
    void oversizedSingleSentenceIsHardSplitNotDropped() {
        List<Chunk> chunks = chunker.chunk(List.of(new PageText(1, "x".repeat(1000))));
        assertThat(chunks.stream().mapToInt(c -> c.text().length()).sum()).isGreaterThanOrEqualTo(1000);
    }
}

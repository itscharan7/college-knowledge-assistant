package com.collegeassistant.ingestion;

/** A piece of text that lives on exactly one page (chunks never cross page boundaries). */
public record Chunk(int pageNumber, int chunkIndex, String text) {
}

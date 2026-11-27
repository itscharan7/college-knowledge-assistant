package com.collegeassistant.ingestion;

/** Text of ONE PDF page. Page numbers are 1-based and come straight from PDFBox, never guessed. */
public record PageText(int pageNumber, String text) {
}

package com.collegeassistant.ingestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts text page by page with Apache PDFBox so every piece of text keeps its page number. */
@Component
public class PdfExtractor {

    public record Extraction(int pageCount, List<PageText> pages) {
    }

    public Extraction extract(byte[] pdfBytes) throws IOException {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int total = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // helps multi-column layouts
            for (int p = 1; p <= total; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.add(new PageText(p, stripper.getText(doc)));
            }
            return new Extraction(total, pages);
        }
    }
}

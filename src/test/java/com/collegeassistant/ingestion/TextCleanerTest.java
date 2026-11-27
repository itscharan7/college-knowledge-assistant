package com.collegeassistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextCleanerTest {

    private final TextCleaner cleaner = new TextCleaner(new TableFormatter());

    @Test
    void convertsTableRowsKeepingHeaders() {
        String page = "Placement rules\nCGPA        Eligibility\n7.0+        Eligible\n"
                + "6.5-6.99    Conditional\nBelow 6.5   Not eligible\nEnd of table text here for padding.";
        TextCleaner.Cleaned c = cleaner.clean(List.of(new PageText(1, page)));
        String out = c.pages().get(0).text();
        assertThat(c.tablesFound()).isEqualTo(1);
        assertThat(out).contains("• CGPA: 7.0+; Eligibility: Eligible");
        assertThat(out).contains("• CGPA: Below 6.5; Eligibility: Not eligible");
    }

    @Test
    void removesRepeatedHeadersFootersAndJoinsHyphenation() {
        String[] topics = {"alpha", "bravo", "charlie", "delta", "echo"};
        List<PageText> pages = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            pages.add(new PageText(i, "Greenfield College Handbook\nSection " + topics[i - 1] + " covers exam-\n"
                    + "ination rules for " + topics[i - 1] + " students and more words.\nPage " + i + " of 5"));
        }
        TextCleaner.Cleaned c = cleaner.clean(pages);
        String first = c.pages().get(0).text();
        assertThat(first).doesNotContain("Greenfield").doesNotContain("of 5").contains("examination");
        assertThat(c.pages().get(2).pageNumber()).isEqualTo(3);
    }

    @Test
    void dropsNearEmptyPages() {
        assertThat(cleaner.clean(List.of(new PageText(1, "  "))).pages()).isEmpty();
    }
}

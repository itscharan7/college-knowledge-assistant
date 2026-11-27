package com.collegeassistant.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All tunable application settings, bound from the "app.*" keys in application.yml. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private List<String> categories = new ArrayList<>();
    private Ai ai = new Ai();
    private Chunking chunking = new Chunking();
    private Ingestion ingestion = new Ingestion();
    private Storage storage = new Storage();
    private Bootstrap bootstrap = new Bootstrap();

    @Getter
    @Setter
    public static class Ai {
        private int topK = 6;
        private double similarityThreshold = 0.5;
        private int historyMessages = 6;
        private boolean rewriteEnabled = true;
        private int maxQuestionLength = 500;
        private int dailyQuota = 50;
        private int embedBatchSize = 20;
        private long embedBatchDelayMs = 0;
        private int excerptChars = 400;
    }

    @Getter
    @Setter
    public static class Chunking {
        private int sizeTokens = 500;
        private int overlapTokens = 60;
        private int charsPerToken = 4;
    }

    @Getter
    @Setter
    public static class Ingestion {
        private int maxPages = 500;
        private int minTextChars = 100;
    }

    @Getter
    @Setter
    public static class Storage {
        private String dir = "./data/uploads";
    }

    @Getter
    @Setter
    public static class Bootstrap {
        private String adminName = "Administrator";
        private String adminEmail = "";
        private String adminPassword = "";
    }
}

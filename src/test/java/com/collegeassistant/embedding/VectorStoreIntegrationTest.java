package com.collegeassistant.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL + pgvector (Testcontainers), real Flyway migrations, real PgVectorStore.
 * Only the embedding model is faked (deterministic bag-of-words vectors) so the test is free and offline.
 * Needs Docker. Run: mvn -Dtest=VectorStoreIntegrationTest test
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.placeholders.embedding-dimensions=16",
        "spring.ai.vectorstore.pgvector.dimensions=16",
        "spring.ai.openai.api-key=test-key",
        "app.bootstrap.admin-email=",
        "app.storage.dir=target/test-uploads"
})
class VectorStoreIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @TestConfiguration
    static class FakeEmbeddings {
        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    List<Embedding> out = new ArrayList<>();
                    int i = 0;
                    for (String t : request.getInstructions()) {
                        out.add(new Embedding(vector(t), i++));
                    }
                    return new EmbeddingResponse(out);
                }

                @Override
                public float[] embed(Document document) {
                    return vector(document.getText());
                }

                @Override
                public int dimensions() {
                    return 16;
                }
            };
        }

        static float[] vector(String text) {
            float[] v = new float[16];
            for (String w : text.toLowerCase().split("\\W+")) {
                if (!w.isBlank()) {
                    v[Math.floorMod(w.hashCode(), 16)] += 1f;
                }
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            norm = Math.sqrt(norm == 0 ? 1 : norm);
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
            return v;
        }
    }

    @Autowired
    VectorStore vectorStore;
    @Autowired
    VectorIndexService index;

    private Document chunk(UUID docId, String audience, String text) {
        return new Document(UUID.randomUUID().toString(), text,
                Map.of("documentId", docId.toString(), "audience", audience, "pageNumber", 1,
                        "documentTitle", "Doc", "category", "General", "chunkId", "c"));
    }

    private List<Document> search(String q, String... audiences) {
        Filter.Expression f = new FilterExpressionBuilder().in("audience", (Object[]) audiences).build();
        return vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(10).filterExpression(f).build());
    }

    @Test
    void audienceFilterHidesFacultyOnlyChunksFromStudents() {
        UUID studentDoc = UUID.randomUUID();
        UUID facultyDoc = UUID.randomUUID();
        index.add(List.of(
                chunk(studentDoc, "STUDENT", "minimum attendance required for students is seventy five percent"),
                chunk(facultyDoc, "FACULTY", "faculty salary revision circular attendance of staff")));

        List<Document> forStudent = search("attendance", "ALL", "STUDENT");
        assertThat(forStudent).isNotEmpty();
        assertThat(forStudent).noneMatch(d -> d.getMetadata().get("audience").equals("FACULTY"));
        assertThat(forStudent).noneMatch(d -> d.getMetadata().get("documentId").equals(facultyDoc.toString()));

        List<Document> forFaculty = search("attendance", "ALL", "STUDENT", "FACULTY");
        assertThat(forFaculty).anyMatch(d -> d.getMetadata().get("documentId").equals(facultyDoc.toString()));
    }

    @Test
    void deleteByDocumentRemovesAllItsVectors() {
        UUID id = UUID.randomUUID();
        index.add(List.of(chunk(id, "ALL", "alpha beta gamma"), chunk(id, "ALL", "delta epsilon zeta")));
        assertThat(index.countForDocument(id)).isEqualTo(2);

        index.deleteByDocument(id);

        assertThat(index.countForDocument(id)).isZero();
    }

    @Test
    void updatingAudienceMetadataTakesEffectWithoutReEmbedding() {
        UUID id = UUID.randomUUID();
        index.add(List.of(chunk(id, "ALL", "examination schedule for semester five")));
        assertThat(search("examination schedule", "ALL", "STUDENT"))
                .anyMatch(d -> d.getMetadata().get("documentId").equals(id.toString()));

        index.updateMetadata(id, "Doc", "Examination", com.collegeassistant.document.Audience.FACULTY);

        assertThat(search("examination schedule", "ALL", "STUDENT"))
                .noneMatch(d -> d.getMetadata().get("documentId").equals(id.toString()));
        assertThat(search("examination schedule", "FACULTY"))
                .anyMatch(d -> d.getMetadata().get("documentId").equals(id.toString()));
    }
}

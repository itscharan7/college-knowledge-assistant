package com.collegeassistant.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<CollegeDocument, UUID> {

    Optional<CollegeDocument> findByChecksum(String checksum);

    List<CollegeDocument> findByStatusAndAudienceInOrderByTitleAsc(DocStatus status, Collection<Audience> audiences);

    List<CollegeDocument> findByStatus(DocStatus status);

    long countByStatus(DocStatus status);

    List<CollegeDocument> findTop5ByOrderByCreatedAtDesc();

    @Query("select d from CollegeDocument d where lower(d.title) like :p or lower(d.originalFilename) like :p "
            + "order by d.createdAt desc")
    List<CollegeDocument> search(@Param("p") String pattern);

    @Query("select d from CollegeDocument d where d.status = :s and (lower(d.title) like :p "
            + "or lower(d.originalFilename) like :p) order by d.createdAt desc")
    List<CollegeDocument> searchByStatus(@Param("p") String pattern, @Param("s") DocStatus status);
}

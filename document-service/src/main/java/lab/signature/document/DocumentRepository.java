package lab.signature.document;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DocumentRepository extends JpaRepository<Document, UUID> {
    interface Metadata {
        UUID getId();
        String getTitle();
        Instant getCreatedAt();
    }

    Optional<Metadata> findMetadataById(UUID id);

    @Modifying
    @Query("""
            update Document d set d.status = :next
            where d.id = :id and d.status = :expected
            """)
    int advance(@Param("id") UUID id, @Param("expected") DocumentStatus expected,
                @Param("next") DocumentStatus next);
}

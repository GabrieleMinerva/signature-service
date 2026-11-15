package lab.signature.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
class Document {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Document() {
    }

    Document(String title) {
        id = UUID.randomUUID();
        this.title = title;
        status = DocumentStatus.DRAFT;
        createdAt = Instant.now();
    }

    UUID id() {
        return id;
    }

    String title() {
        return title;
    }

    DocumentStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }
}

package lab.signature.document;

import java.time.Instant;
import java.util.UUID;

record DocumentView(UUID id, String title, DocumentStatus status, Instant createdAt) {
    static DocumentView from(Document document) {
        return new DocumentView(document.id(), document.title(), document.status(), document.createdAt());
    }
}

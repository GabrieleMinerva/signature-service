package lab.signature.messaging;

import java.util.Objects;
import java.util.UUID;

public record SignatureEvent(UUID documentId, UUID eventId) {
    public SignatureEvent {
        Objects.requireNonNull(documentId, "documentId is required");
        Objects.requireNonNull(eventId, "eventId is required");
    }
}

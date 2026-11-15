package lab.signature.document;

import java.util.List;
import java.util.UUID;
import lab.signature.messaging.SignatureEvent;
import lab.signature.messaging.SignatureMessaging;
import lab.signature.messaging.SignaturePublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

@Service
class DocumentService {
    private final DocumentRepository documents;
    private final DocumentStatusCache cache;
    private final SignaturePublisher publisher;
    private final TransactionOperations transactions;

    DocumentService(DocumentRepository documents, DocumentStatusCache cache,
                    SignaturePublisher publisher, TransactionOperations transactions) {
        this.documents = documents;
        this.cache = cache;
        this.publisher = publisher;
        this.transactions = transactions;
    }

    DocumentView create(String title) {
        return DocumentView.from(documents.save(new Document(title)));
    }

    DocumentView get(UUID id) {
        var cached = cache.get(id);
        if (cached.isPresent()) {
            var metadata = documents.findMetadataById(id).orElseThrow(() -> notFound(id));
            return new DocumentView(metadata.getId(), metadata.getTitle(), cached.get(), metadata.getCreatedAt());
        }

        var document = documents.findById(id).orElseThrow(() -> notFound(id));
        cache.put(id, document.status());
        return DocumentView.from(document);
    }

    List<DocumentView> list() {
        return documents.findAll(Sort.by("createdAt", "id")).stream()
                .map(DocumentView::from)
                .toList();
    }

    void requestSignature(UUID id) {
        transactions.executeWithoutResult(transaction -> {
            if (documents.advance(id, DocumentStatus.DRAFT, DocumentStatus.PENDING_SIGNATURE) == 0) {
                if (!documents.existsById(id)) {
                    throw notFound(id);
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Document is no longer a draft");
            }
        });

        cache.evict(id);
        publisher.publish(SignatureMessaging.REQUESTS, new SignatureEvent(id, UUID.randomUUID()));
    }

    void completeSignature(UUID id) {
        Integer updated = transactions.execute(transaction ->
                documents.advance(id, DocumentStatus.PENDING_SIGNATURE, DocumentStatus.SIGNED));
        if (Integer.valueOf(1).equals(updated)) {
            cache.evict(id);
        }
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Document " + id + " does not exist");
    }
}

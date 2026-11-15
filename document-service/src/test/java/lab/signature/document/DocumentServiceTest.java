package lab.signature.document;

import java.util.Optional;
import java.util.UUID;
import lab.signature.messaging.SignatureEvent;
import lab.signature.messaging.SignatureMessaging;
import lab.signature.messaging.SignaturePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock DocumentRepository repository;
    @Mock DocumentStatusCache cache;
    @Mock SignaturePublisher publisher;

    @Test
    void requestsSignatureOnlyForDrafts() {
        UUID id = UUID.randomUUID();
        var service = service();
        when(repository.advance(id, DocumentStatus.DRAFT, DocumentStatus.PENDING_SIGNATURE)).thenReturn(1, 0);
        when(repository.existsById(id)).thenReturn(true);

        service.requestSignature(id);
        assertThatThrownBy(() -> service.requestSignature(id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));

        var order = inOrder(repository, cache, publisher);
        order.verify(repository).advance(id, DocumentStatus.DRAFT, DocumentStatus.PENDING_SIGNATURE);
        order.verify(cache).evict(id);
        order.verify(publisher).publish(eq(SignatureMessaging.REQUESTS),
                argThat((SignatureEvent event) -> event.documentId().equals(id) && event.eventId() != null));
        order.verify(repository).advance(id, DocumentStatus.DRAFT, DocumentStatus.PENDING_SIGNATURE);
        order.verify(repository).existsById(id);
        order.verifyNoMoreInteractions();
    }

    @Test
    void repeatedResultDoesNotApplyAnotherTransition() {
        UUID id = UUID.randomUUID();
        var service = service();
        when(repository.advance(id, DocumentStatus.PENDING_SIGNATURE, DocumentStatus.SIGNED)).thenReturn(1, 0);

        service.completeSignature(id);
        service.completeSignature(id);

        verify(cache).evict(id);
    }

    @Test
    void cacheMissLoadsAndCachesPostgresStatus() {
        var document = new Document("Agreement");
        when(cache.get(document.id())).thenReturn(Optional.empty());
        when(repository.findById(document.id())).thenReturn(Optional.of(document));

        assertThat(service().get(document.id()).status()).isEqualTo(DocumentStatus.DRAFT);
        verify(cache).put(document.id(), DocumentStatus.DRAFT);
    }

    @Test
    void cacheHitLoadsOnlyMetadata() {
        UUID id = UUID.randomUUID();
        DocumentRepository.Metadata metadata = org.mockito.Mockito.mock(DocumentRepository.Metadata.class);
        when(metadata.getId()).thenReturn(id);
        when(cache.get(id)).thenReturn(Optional.of(DocumentStatus.SIGNED));
        when(repository.findMetadataById(id)).thenReturn(Optional.of(metadata));

        assertThat(service().get(id).status()).isEqualTo(DocumentStatus.SIGNED);
        verify(repository, never()).findById(id);
    }

    private DocumentService service() {
        return new DocumentService(repository, cache, publisher, TransactionOperations.withoutTransaction());
    }
}

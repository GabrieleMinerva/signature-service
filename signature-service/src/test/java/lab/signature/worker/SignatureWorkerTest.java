package lab.signature.worker;

import java.util.UUID;
import lab.signature.messaging.SignatureEvent;
import lab.signature.messaging.SignatureMessaging;
import lab.signature.messaging.SignaturePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SignatureWorkerTest {
    private final SignaturePublisher publisher = mock(SignaturePublisher.class);
    private final SignatureWorker worker = new SignatureWorker(publisher);

    @Test
    void publishesResultWithSameDocumentAndEventIds() {
        var event = new SignatureEvent(UUID.randomUUID(), UUID.randomUUID());

        worker.sign(event);

        verify(publisher).publish(SignatureMessaging.RESULTS, event);
    }

    @Test
    void letsListenerHandlePublicationFailure() {
        var event = new SignatureEvent(UUID.randomUUID(), UUID.randomUUID());
        doThrow(new AmqpException("offline")).when(publisher).publish(SignatureMessaging.RESULTS, event);

        assertThatThrownBy(() -> worker.sign(event)).isInstanceOf(AmqpException.class);
    }
}

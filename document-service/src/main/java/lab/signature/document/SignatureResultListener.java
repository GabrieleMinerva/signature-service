package lab.signature.document;

import lab.signature.messaging.SignatureEvent;
import lab.signature.messaging.SignatureMessaging;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class SignatureResultListener {
    private final DocumentService documents;

    SignatureResultListener(DocumentService documents) {
        this.documents = documents;
    }

    @RabbitListener(queues = SignatureMessaging.RESULTS)
    void onResult(SignatureEvent result) {
        documents.completeSignature(result.documentId());
    }
}

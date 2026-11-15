package lab.signature.worker;

import lab.signature.messaging.SignatureEvent;
import lab.signature.messaging.SignatureMessaging;
import lab.signature.messaging.SignaturePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class SignatureWorker {
    private static final Logger log = LoggerFactory.getLogger(SignatureWorker.class);
    private final SignaturePublisher publisher;

    SignatureWorker(SignaturePublisher publisher) {
        this.publisher = publisher;
    }

    @RabbitListener(queues = SignatureMessaging.REQUESTS)
    void sign(SignatureEvent request) {
        log.info("Simulated signature for document {} (event {})", request.documentId(), request.eventId());
        publisher.publish(SignatureMessaging.RESULTS, request);
    }
}

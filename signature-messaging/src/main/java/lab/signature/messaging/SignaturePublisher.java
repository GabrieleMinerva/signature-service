package lab.signature.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SignaturePublisher {
    private final RabbitTemplate rabbit;

    public SignaturePublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publish(String routingKey, SignatureEvent event) {
        // The correlation ID belongs to this send. The event ID survives redelivery.
        var correlation = new CorrelationData();
        rabbit.convertAndSend(SignatureMessaging.EXCHANGE, routingKey, event, correlation);
        try {
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new AmqpException("RabbitMQ did not route the event to " + routingKey);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ confirmation", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new AmqpException("RabbitMQ confirmation failed for " + routingKey, ex);
        }
    }
}

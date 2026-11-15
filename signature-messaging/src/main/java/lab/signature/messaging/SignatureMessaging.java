package lab.signature.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SignatureMessaging {
    public static final String EXCHANGE = "signature";
    public static final String REQUESTS = "signature.requests";
    public static final String RESULTS = "signature.results";

    @Bean
    Declarables signatureTopology() {
        var exchange = new DirectExchange(EXCHANGE);
        var requests = QueueBuilder.durable(REQUESTS).build();
        var results = QueueBuilder.durable(RESULTS).build();
        return new Declarables(exchange, requests, results,
                BindingBuilder.bind(requests).to(exchange).with(REQUESTS),
                BindingBuilder.bind(results).to(exchange).with(RESULTS));
    }

    @Bean
    Jackson2JsonMessageConverter signatureJsonConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

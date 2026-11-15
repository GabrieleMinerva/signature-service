package lab.signature.worker;

import lab.signature.messaging.SignatureMessaging;
import lab.signature.messaging.SignaturePublisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({SignatureMessaging.class, SignaturePublisher.class})
public class SignatureApplication {
    public static void main(String[] args) {
        SpringApplication.run(SignatureApplication.class, args);
    }
}

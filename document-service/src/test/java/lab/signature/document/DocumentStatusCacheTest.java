package lab.signature.document;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentStatusCacheTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;

    @Test
    void usesExpectedKeyAndTtl() {
        UUID id = UUID.randomUUID();
        String key = "document:status:" + id;
        var cache = new DocumentStatusCache(redis);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(key)).thenReturn("SIGNED");

        cache.put(id, DocumentStatus.SIGNED);
        assertThat(cache.get(id)).contains(DocumentStatus.SIGNED);
        cache.evict(id);

        verify(values).set(key, "SIGNED", Duration.ofMinutes(5));
        verify(redis).delete(key);
    }

    @Test
    void redisUnavailableIsACacheMiss() {
        UUID id = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("document:status:" + id)).thenThrow(new RedisConnectionFailureException("offline"));

        assertThat(new DocumentStatusCache(redis).get(id)).isEmpty();
    }
}

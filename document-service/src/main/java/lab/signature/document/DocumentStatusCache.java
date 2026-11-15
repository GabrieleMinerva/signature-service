package lab.signature.document;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class DocumentStatusCache {
    private static final Logger log = LoggerFactory.getLogger(DocumentStatusCache.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private final StringRedisTemplate redis;

    DocumentStatusCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    Optional<DocumentStatus> get(UUID id) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key(id))).map(DocumentStatus::valueOf);
        } catch (DataAccessException | IllegalArgumentException ex) {
            log.warn("Could not read cached status for {}", id, ex);
            return Optional.empty();
        }
    }

    void put(UUID id, DocumentStatus status) {
        try {
            redis.opsForValue().set(key(id), status.name(), TTL);
        } catch (DataAccessException ex) {
            log.warn("Could not cache status for {}", id, ex);
        }
    }

    void evict(UUID id) {
        try {
            redis.delete(key(id));
        } catch (DataAccessException ex) {
            log.warn("Could not invalidate cached status for {}", id, ex);
        }
    }

    private String key(UUID id) {
        return "document:status:" + id;
    }
}

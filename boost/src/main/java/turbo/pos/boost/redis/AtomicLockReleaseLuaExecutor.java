package turbo.pos.boost.redis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Atomic check-and-delete lock release via Lua script. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AtomicLockReleaseLuaExecutor {

    private final RedissonClient redissonClient;

    private String script;
    private String scriptSha;

    @PostConstruct
    void loadScript() throws IOException {
        try (InputStream in = new ClassPathResource("redis/atomic-lock-release.lua").getInputStream()) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        scriptSha = redissonClient.getScript(StringCodec.INSTANCE).scriptLoad(script);
        log.info("Loaded atomic-lock-release.lua (sha={})", scriptSha);
    }

    /**
     * @return true if lock was owned by caller and deleted; false otherwise
     */
    public boolean releaseLock(String lockKey, String expectedValue) {
        Long result = redissonClient.getScript(StringCodec.INSTANCE).evalSha(
                RScript.Mode.READ_WRITE,
                scriptSha,
                RScript.ReturnType.INTEGER,
                List.of(lockKey),
                expectedValue);

        return result != null && result == 1L;
    }

    public String getScriptSha() {
        return scriptSha;
    }
}

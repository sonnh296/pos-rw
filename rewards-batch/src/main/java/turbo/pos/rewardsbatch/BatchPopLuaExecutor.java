package turbo.pos.rewardsbatch;

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

/** Pop nhiều phần tử từ Redis list trong một round-trip (Lua script). */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchPopLuaExecutor {

    private final RedissonClient redissonClient;

    private String script;
    private String scriptSha;

    @PostConstruct
    void loadScript() throws IOException {
        try (InputStream in = new ClassPathResource("redis/batch-pop.lua").getInputStream()) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        scriptSha = redissonClient.getScript(StringCodec.INSTANCE).scriptLoad(script);
        log.info("Loaded batch-pop.lua (sha={})", scriptSha);
    }

    @SuppressWarnings("unchecked")
    public List<String> popBatch(String key, int batchSize) {
        return (List<String>) redissonClient.getScript(StringCodec.INSTANCE).evalSha(
                RScript.Mode.READ_WRITE,
                scriptSha,
                RScript.ReturnType.MULTI,
                List.of(key),
                String.valueOf(batchSize));
    }

    public String getScriptSha() {
        return scriptSha;
    }
}

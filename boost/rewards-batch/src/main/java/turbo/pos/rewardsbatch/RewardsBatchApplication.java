package turbo.pos.rewardsbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RewardsBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(RewardsBatchApplication.class, args);
    }
}


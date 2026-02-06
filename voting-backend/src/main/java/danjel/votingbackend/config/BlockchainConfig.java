package danjel.votingbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainConfig {

    // Getters and Setters
    private int difficulty = 4;
    private int blockSize = 100;
    private String hashAlgorithm = "SHA-256";
    private String signatureAlgorithm = "SHA256withRSA";
    private int keySize = 2048;
    private boolean enableMining = true;
    private int miningThreads = 2;
    private long blockTimeTargetMs = 10000;

    @Bean
    public KeyPairGenerator keyPairGenerator() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        return generator;
    }

    @Bean
    public KeyPair serverKeyPair(KeyPairGenerator keyPairGenerator) {
        return keyPairGenerator.generateKeyPair();
    }

    @Bean(name = "blockchainTaskExecutor")
    public Executor blockchainTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(miningThreads);
        executor.setMaxPoolSize(miningThreads * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("blockchain-");
        executor.initialize();
        return executor;
    }

}

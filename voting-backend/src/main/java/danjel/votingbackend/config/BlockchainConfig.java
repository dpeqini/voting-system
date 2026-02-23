package danjel.votingbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.Executor;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainConfig {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainConfig.class);

    // Blockchain Properties
    private int difficulty = 4;
    private int blockSize = 100;
    private String hashAlgorithm = "SHA-256";
    private String signatureAlgorithm = "SHA256withRSA";
    private int keySize = 2048;
    private boolean enableMining = true;
    private int miningThreads = 2;
    private long blockTimeTargetMs = 10000;

    // File paths for persistent keys
    private static final String PRIVATE_KEY_FILE = "blockchain_private.key";
    private static final String PUBLIC_KEY_FILE = "blockchain_public.key";

    /**
     * Provides the server's KeyPair.
     * It attempts to load an existing key from disk. If none exists, it generates
     * a new pair and saves them to disk to ensure signatures survive server restarts.
     */
    @Bean
    public KeyPair serverKeyPair() {
        try {
            File privateKeyFile = new File(PRIVATE_KEY_FILE);
            File publicKeyFile = new File(PUBLIC_KEY_FILE);

            if (privateKeyFile.exists() && publicKeyFile.exists()) {
                logger.info("Loading existing Blockchain RSA keys from disk...");
                return loadKeyPair(privateKeyFile, publicKeyFile);
            } else {
                logger.info("No existing keys found. Generating and saving new RSA KeyPair...");
                return generateAndSaveKeyPair(privateKeyFile, publicKeyFile);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Blockchain KeyPair", e);
        }
    }

    private KeyPair generateAndSaveKeyPair(File privateKeyFile, File publicKeyFile) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        // Using the keySize property defined in your class
        keyGen.initialize(this.keySize);
        KeyPair pair = keyGen.generateKeyPair();

        // Save keys to files as raw bytes
        Files.write(privateKeyFile.toPath(), pair.getPrivate().getEncoded());
        Files.write(publicKeyFile.toPath(), pair.getPublic().getEncoded());

        return pair;
    }

    private KeyPair loadKeyPair(File privateKeyFile, File publicKeyFile) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // Read public key
        byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        // Read private key
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return new KeyPair(publicKey, privateKey);
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
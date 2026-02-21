package danjel.votingbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configures the RestTemplate used by DeepFaceClient.
 *
 * Separate connect and read timeouts ensure that if the Python DeepFace server
 * is slow (first call downloads the Facenet512 model ~100MB) or unresponsive,
 * the HTTP thread does not hang indefinitely.
 */
@Configuration
public class RestTemplateConfig {

    @Value("${deepface.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${deepface.timeout-ms:30000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
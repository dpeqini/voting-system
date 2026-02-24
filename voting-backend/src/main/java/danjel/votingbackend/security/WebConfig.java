package danjel.votingbackend.security;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ══════════════════════════════════════════════════════════════
 *  WebConfig — Register Interceptors
 *
 *  Plugs ReplayPreventionInterceptor into the Spring MVC pipeline so that
 *  every request to /api/v1/auth/** and /api/v1/vote/** is validated for:
 *    • Fresh timestamp (within 60 seconds)
 *    • Unique nonce (not seen before in the 5-minute window)
 *    • Valid HMAC signature (when device secret is registered)
 *
 *  The OPTIONS method is excluded to allow CORS preflight through.
 * ══════════════════════════════════════════════════════════════
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ReplayPreventionInterceptor replayPreventionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(replayPreventionInterceptor)
                // Protected paths — all authentication and voting endpoints
                .addPathPatterns(
                        "/api/v1/auth/id-card",
                        "/api/v1/vote/**",
                        "/api/v1/verification/**",
                        "/api/v1/elections/**"
                )
                // Allow CORS preflight through without anti-replay headers
                .excludePathPatterns()   // add specific exclusions here if needed
                .order(1);              // run before any auth filter
    }
}
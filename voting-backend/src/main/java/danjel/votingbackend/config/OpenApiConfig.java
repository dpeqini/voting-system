package danjel.votingbackend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        description = "Enter your JWT token obtained from /api/auth/login"
)
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Albania Voting System API")
                        .version("1.0.0")
                        .description("""
                    ## Blockchain-based Electronic Voting System for Albania
                    
                    This API provides secure electronic voting capabilities for:
                    - **Parliamentary Elections** - Candidates grouped by 12 counties (Qarks)
                    - **Local Government Elections** - Candidates grouped by 65 municipalities
                    
                    ### Features
                    - JWT-based authentication
                    - Face recognition verification
                    - Blockchain vote recording
                    - Real-time election management
                    
                    ### Authentication
                    Most endpoints require a JWT token. To authenticate:
                    1. Register or login via `/api/auth/register` or `/api/auth/login`
                    2. Copy the `accessToken` from the response
                    3. Click the **Authorize** button and enter: `Bearer <your-token>`
                    
                    ### Default Test Users
                    | Email | Password | Role |
                    |-------|----------|------|
                    | admin@voting.albania.gov | Admin@2024!Secure | ADMIN |
                    | voter@example.com | Voter@2024! | VOTER |
                    """)
                        .contact(new Contact()
                                .name("Albania Election Commission")
                                .email("support@voting.albania.gov")
                                .url("https://voting.albania.gov"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://voting.albania.gov/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.voting.albania.gov")
                                .description("Production Server")))
                .tags(Arrays.asList(
                        new Tag().name("Authentication").description("User registration, login, and token management"),
                        new Tag().name("Elections").description("Election creation and management (Admin only)"),
                        new Tag().name("Voting").description("Vote casting and candidate retrieval"),
                        new Tag().name("Verification").description("Face verification and vote verification"),
                        new Tag().name("Public").description("Public endpoints (no authentication required)")
                ));
    }
}
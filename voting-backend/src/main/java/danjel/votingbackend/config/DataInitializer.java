package danjel.votingbackend.config;
import danjel.votingbackend.model.Voter;
import danjel.votingbackend.repository.VoterRepository;
import danjel.votingbackend.utils.enums.AlbanianCounty;
import danjel.votingbackend.utils.enums.AlbanianMunicipality;
import danjel.votingbackend.utils.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(VoterRepository voterRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Create admin user if not exists
            if (!voterRepository.existsByEmail("admin@voting.albania.gov")) {
                Voter admin = new Voter();
                admin.setNationalId("ADMIN00001");
                admin.setFirstName("System");
                admin.setLastName("Administrator");
                admin.setEmail("admin@voting.albania.gov");
                admin.setPasswordHash(passwordEncoder.encode("Admin@2024!Secure"));
                admin.setDateOfBirth(LocalDate.of(1980, 1, 1));
                admin.setCounty(AlbanianCounty.TIRANE);
                admin.setMunicipality(AlbanianMunicipality.TIRANE);
                admin.setAddress("Ministry of Interior, Tirana");
                admin.setRole(UserRole.ADMIN);
                admin.setVerified(true);
                admin.setFaceVerified(true);
                admin.setEnabled(true);

                voterRepository.save(admin);
                logger.info("Admin user created: admin@voting.albania.gov");
            }

            // Create election official user if not exists
            if (!voterRepository.existsByEmail("official@voting.albania.gov")) {
                Voter official = new Voter();
                official.setNationalId("OFFCL00001");
                official.setFirstName("Election");
                official.setLastName("Official");
                official.setEmail("official@voting.albania.gov");
                official.setPasswordHash(passwordEncoder.encode("Official@2024!"));
                official.setDateOfBirth(LocalDate.of(1985, 6, 15));
                official.setCounty(AlbanianCounty.TIRANE);
                official.setMunicipality(AlbanianMunicipality.TIRANE);
                official.setAddress("Central Election Commission, Tirana");
                official.setRole(UserRole.ELECTION_OFFICIAL);
                official.setVerified(true);
                official.setFaceVerified(true);
                official.setEnabled(true);

                voterRepository.save(official);
                logger.info("Election official user created: official@voting.albania.gov");
            }

            // Create sample voter for testing (development only)
            if (!voterRepository.existsByEmail("voter@example.com")) {
                Voter testVoter = new Voter();
                testVoter.setNationalId("I12345678A");
                testVoter.setFirstName("Test");
                testVoter.setLastName("Voter");
                testVoter.setEmail("voter@example.com");
                testVoter.setPasswordHash(passwordEncoder.encode("Voter@2024!"));
                testVoter.setDateOfBirth(LocalDate.of(1990, 3, 20));
                testVoter.setCounty(AlbanianCounty.TIRANE);
                testVoter.setMunicipality(AlbanianMunicipality.TIRANE);
                testVoter.setAddress("Rruga e Kavajes, Tirana");
                testVoter.setRole(UserRole.VOTER);
                testVoter.setVerified(true);
                testVoter.setFaceVerified(false);
                testVoter.setEnabled(true);

                voterRepository.save(testVoter);
                logger.info("Test voter created: voter@example.com");
            }

            logger.info("Database initialization completed");
        };
    }
}
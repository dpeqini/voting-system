# Albania Voting Backend System

A comprehensive, blockchain-based electronic voting system designed for Albania's Parliamentary and Local Government elections.

## Features

### Election Management
- **Election Types Support**:
    - **Parliamentary Elections**: Candidates grouped by 12 Albanian counties (Qarks)
    - **Local Government Elections**: Candidates grouped by 65 municipalities

### Admin Capabilities
- Create and configure elections with dates, types, and deadlines
- Import candidates and parties from external services
- Start and close elections
- Monitor real-time voting statistics

### Security Features
- JWT-based authentication with refresh tokens
- Face recognition enrollment and verification
- Liveness detection for anti-spoofing
- Account lockout after failed attempts
- Role-based access control (Admin, Voter, Election Official, Observer)

### Blockchain Integration
- Immutable vote recording
- SHA-256 hashing with proof-of-work
- Merkle tree for vote verification
- Chain validation and integrity checks
- Vote receipt generation

### Voter Features
- Region-based candidate display
- Vote verification with receipt
- Anonymous voting (voter hash)
- One-vote-per-election enforcement

## Project Structure

```
voting-backend/
├── src/main/java/com/voting/
│   ├── VotingApplication.java          # Main application entry
│   ├── config/
│   │   ├── SecurityConfig.java         # Security configuration
│   │   ├── BlockchainConfig.java       # Blockchain settings
│   │   ├── JwtAuthenticationFilter.java
│   │   └── DataInitializer.java        # Initial admin setup
│   ├── controller/
│   │   ├── AuthController.java         # Authentication endpoints
│   │   ├── VotingController.java       # Vote casting
│   │   ├── ElectionController.java     # Election management
│   │   └── VerificationController.java # Vote & face verification
│   ├── service/
│   │   ├── AuthService.java            # Authentication logic
│   │   ├── VotingService.java          # Voting logic
│   │   ├── ElectionService.java        # Election management
│   │   ├── BlockchainService.java      # Blockchain operations
│   │   ├── FaceRecognitionService.java # Face verification
│   │   ├── ExternalDataService.java    # External API integration
│   │   └── JwtService.java             # JWT handling
│   ├── model/
│   │   ├── Voter.java
│   │   ├── Election.java
│   │   ├── Vote.java
│   │   ├── Candidate.java
│   │   ├── Party.java
│   │   └── Block.java
│   ├── dto/                            # Data Transfer Objects
│   ├── repository/                     # JPA Repositories
│   ├── enums/
│   │   ├── ElectionType.java
│   │   ├── ElectionStatus.java
│   │   ├── AlbanianCounty.java         # 12 counties
│   │   ├── AlbanianMunicipality.java   # 65 municipalities
│   │   └── UserRole.java
│   └── exception/                      # Custom exceptions
└── src/main/resources/
    └── application.yml                 # Configuration
```

## Albanian Administrative Divisions

### Counties (Qarks) - For Parliamentary Elections
1. Berat
2. Dibër
3. Durrës
4. Elbasan
5. Fier
6. Gjirokastër
7. Korçë
8. Kukës
9. Lezhë
10. Shkodër
11. Tiranë
12. Vlorë

### Municipalities - For Local Government Elections
65 municipalities distributed across the 12 counties.

## API Endpoints

### Authentication
```
POST /api/auth/register     - Register new voter
POST /api/auth/login        - Login
POST /api/auth/refresh      - Refresh token
POST /api/auth/logout       - Logout
```

### Elections (Admin)
```
POST   /api/elections                           - Create election
POST   /api/elections/{id}/import-candidates    - Import candidates
POST   /api/elections/{id}/start                - Start election
POST   /api/elections/{id}/close                - Close election
```

### Elections (Public/Voter)
```
GET    /api/elections/active                    - Get active elections
GET    /api/elections/{id}                      - Get election details
GET    /api/elections/{id}/candidates           - Get all candidates
GET    /api/elections/{id}/candidates/my-region - Get voter's regional candidates
GET    /api/elections/{id}/parties              - Get parties
```

### Voting
```
POST   /api/vote                     - Cast vote
GET    /api/vote/candidates/{id}     - Get candidates for voter
GET    /api/vote/status/{id}         - Check if voted
```

### Verification
```
POST   /api/verification/face/enroll    - Enroll face
POST   /api/verification/face/verify    - Verify face
GET    /api/verification/vote/{hash}    - Verify vote
GET    /api/verification/blockchain/{id}/validate - Validate chain
```

## Configuration

### Environment Variables
```
DB_USERNAME=voting_admin
DB_PASSWORD=voting_secret_password
JWT_SECRET=your-base64-encoded-secret
EXTERNAL_API_KEY=your-external-api-key
```

### Default Users
| Email | Password | Role |
|-------|----------|------|
| admin@voting.albania.gov | Admin@2024!Secure | ADMIN |
| official@voting.albania.gov | Official@2024! | ELECTION_OFFICIAL |
| voter@example.com | Voter@2024! | VOTER |

## Running the Application

### Development
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=development
```

### Production
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=production
```

### Build
```bash
./mvnw clean package
java -jar target/voting-backend-1.0.0.jar
```

## Election Workflow

1. **Admin creates election** with type (Parliamentary/Local Government)
2. **Admin imports candidates** from external service
3. **Candidates are assigned** to counties/municipalities based on election type
4. **Admin starts election** - Blockchain is initialized
5. **Voters authenticate** and complete face verification
6. **Voters see candidates** for their region only
7. **Voters cast encrypted votes** - Added to blockchain
8. **Admin closes election** - Final block is mined
9. **Results are verified** through blockchain validation

## Security Considerations

- All votes are encrypted before storage
- Voter identity is anonymized using hashes
- Blockchain ensures vote immutability
- Face verification prevents impersonation
- Liveness detection prevents photo attacks
- Rate limiting on authentication endpoints
- Account lockout after 5 failed attempts

## License

Proprietary - Albania Ministry of Interior
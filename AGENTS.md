# Agent Instructions for instant-need

## Project Overview
**Type:** Spring Boot 4.0.6 REST API Service  
**Language:** Java 25  
**Build System:** Maven (via `./mvnw`)  
**Key Focus:** B2B service platform with security and data persistence  
**Package Root:** `com.b2b.instantneed`

## Architecture & Key Components

### Technology Stack
- **Spring Boot Framework:** `spring-boot-starter-*` for modular feature loading
- **Data Layer:** Spring Data JPA + PostgreSQL (configured in `application.properties`)
- **Web Layer:** Spring MVC for REST endpoints
- **Security:** Spring Security (requires configuration for authentication/authorization)
- **Actuator:** Built-in health checks and metrics at `/actuator/*`
- **Code Generation:** Lombok (via `@Data`, `@Builder`, `@Slf4j` annotations)

### Project Structure
```
src/main/java/com/b2b/instantneed/      # Application source code
src/main/resources/                       # Configuration files
src/test/java/com/b2b/instantneed/      # Test code (JUnit 5)
pom.xml                                   # Maven dependencies & build config
```

### Current State
- Single entry point: `InstantNeedApplication.java` with `@SpringBootApplication`
- Minimal codebase - early development stage
- Test scaffolding exists with `@SpringBootTest` for integration testing
- Database configuration requires PostgreSQL setup in `application.properties`

## Build & Development Workflows

### Essential Maven Commands
```bash
./mvnw clean install          # Full build with tests
./mvnw spring-boot:run        # Run application (auto-reload with devtools)
./mvnw test                    # Run test suite
./mvnw clean                   # Clean build artifacts
```

### Development Environment
- **Spring Boot DevTools** enabled: Auto-reloads application on file changes during `mvnw spring-boot:run`
- Run the application with Spring Boot Maven plugin to enable hot-reload
- Compiler configured with Lombok annotation processor for both compile and test phases

### Database Setup Required
- PostgreSQL driver included (runtime dependency)
- Add database connection properties to `application.properties`:
  ```properties
  spring.application.name=instant-need
  spring.datasource.url=jdbc:postgresql://localhost:5432/instant_need
  spring.datasource.username=<user>
  spring.datasource.password=<password>
  spring.jpa.hibernate.ddl-auto=update
  ```

## Code Patterns & Conventions

### Package Structure Pattern
All application code follows: `com.b2b.instantneed.{domain}.*`  
Examples: `com.b2b.instantneed.user`, `com.b2b.instantneed.product`, `com.b2b.instantneed.order`

### Lombok Usage
- Reduce boilerplate with `@Data`, `@Builder`, `@Getter`, `@Setter`
- Use `@Slf4j` for logger fields (generates `log` field)
- Maven compiler plugin configured to process Lombok annotations at compile time
- **Note:** Lombok excluded from spring-boot-maven-plugin fat jar builds

### Entity & Repository Pattern
When creating new features, follow Spring Data JPA conventions:
```java
// Entity: src/main/java/com/b2b/instantneed/entity/User.java
@Entity
@Data
public class User { }

// Repository: src/main/java/com/b2b/instantneed/repository/UserRepository.java
public interface UserRepository extends JpaRepository<User, Long> { }
```

### Test Conventions
- Use `@SpringBootTest` for integration tests (loads full application context)
- Place tests in matching package structure: `src/test/java/com/b2b/instantneed/...`
- Test dependencies: junit-jupiter, spring-boot-starter-*-test modules

## Security Configuration
Spring Security is included but **not yet configured**. When implementing authentication:
- Create `SecurityConfig.java` with `@Configuration` and `@EnableWebSecurity`
- Define authentication providers (LDAP, OAuth2, DB-based, etc. - see HELP.md references)
- Spring Security test support available via `spring-boot-starter-security-test`

## Running & Debugging

### To Run Locally
```bash
./mvnw spring-boot:run
# Application starts on default port 8080
# Access Actuator: http://localhost:8080/actuator
```

### To Debug
1. IDE breakpoints work with `./mvnw spring-boot:run` (Spring Boot DevTools restarts on changes)
2. For remote debug: Add `-Dagentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005` to maven command

### Testing
- Run all tests: `./mvnw test`
- Run single test: `./mvnw test -Dtest=InstantNeedApplicationTests`
- Context load test validates Spring configuration correctness

## Integration Points & Dependencies

### External Services (NOT yet configured - to be added)
- **PostgreSQL Database:** Connection required in `application.properties`
- **OAuth2/Authorization Services:** Spring Security OAuth2 starters available
- **Monitoring:** Actuator endpoints for production monitoring

### Inter-Component Communication
- Controllers → Services → Repositories → Entities pattern (standard Spring)
- No current cross-module dependencies - structure flat for early-stage project
- Add service layer in `com.b2b.instantneed.service` as features grow

## Key Development Notes
1. **Entity Mapping:** Use Spring Data JPA annotations (`@Entity`, `@Table`, `@Column`) for database mapping
2. **HTTP Methods:** Follow REST conventions (GET, POST, PUT, DELETE)
3. **Error Handling:** Create custom exceptions in `com.b2b.instantneed.exception` package
4. **Configuration:** Use `application.properties` properties, not hardcoded values
5. **Lombok Processor:** Ensure IDE recognizes Lombok or build will fail (configure IDE's annotation processor path)

## When Adding New Features
1. Create domain/feature packages under `com.b2b.instantneed`
2. Structure as: `entity/`, `repository/`, `controller/`, `service/`, `dto/`, `exception/`
3. Add tests alongside in matching test directory structure
4. Add new dependencies to `pom.xml` under appropriate sections
5. Update `application.properties` with any new configuration
6. Ensure Spring component scanning picks up new `@Component`, `@Service`, `@Repository`, `@RestController` classes (automatic within root package)


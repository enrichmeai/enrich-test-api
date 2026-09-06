# Contributing to Dev Easy Test API

We welcome contributions! This guide will help you get started.

## Development Setup

1. **Prerequisites:**
   - Java 17+
   - Maven 3.8+
   - Docker (for LocalStack integration tests)

2. **Clone and Build:**

```bash
git clone https://github.com/enrichmeai/dev-easy-test-api.git
cd dev-easy-test-api
mvn clean verify
```

3. **Run Tests:**

```bash
mvn -B verify          # everything: unit, integration, Cucumber, all gates
mvn -B verify -DskipITs # skip the LocalStack integration tests and Cucumber suites
```

Docker must be running for the integration tests and the Cucumber suites. The first
run pulls `localstack/localstack:3.8`, roughly 1.3 GB.

## Code Standards

- **Java 17:** Use modern Java features (records, sealed classes where appropriate)
- **Formatting:** Run `mvn spotless:apply` before committing
- **Static Analysis:** Checkstyle runs in `verify`. Error Prone runs under `-Perrorprone`
  and fails only on ERROR-severity findings
- **Coverage:** JaCoCo floors are per module and set to what each module measures today
  (see README). Raise them when you add tests; never lower them. The project target is
  80% line and 70% branch, which neither module currently meets
- **Tests:** Every capability implementation needs integration tests

## Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make changes with clear, atomic commits, signed off with `git commit -s` (DCO)
4. Run `mvn -B verify` and ensure all checks pass
5. Push to your fork and create a pull request
6. PR must pass all GitHub Actions checks

## Commit Messages

Follow conventional commits:
- `feat: Add Azure Blob Storage adapter`
- `fix: Handle null values in DynamoDB items`
- `docs: Update README with PubSub examples`
- `test: Add edge case tests for S3 pagination`

## Questions?

Open an issue or discussion on GitHub!

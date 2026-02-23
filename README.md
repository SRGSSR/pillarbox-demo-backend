# Pillarbox Demo Backend

![Pillarbox logo](docs/README-images/logo.jpg)

Pillarbox Demo Backend is a Kotlin-based Koin & Ktor service designed to act as the lightweight
backbone for media management.

This service provides a simple REST API to publish, organize, and retrieve media assets using a
simplified format. It is built to serve the Pillarbox demo ecosystem.

## Quick Guide

**Prerequisites and Requirements**

- **JDK 24** or higher

**Setup**

1. Build the Application:
   ```bash
   ./gradlew clean build -x test
   ```

2. Run the Application:

- Using Gradle:
  ```bash
  ./gradlew run
  ```

### Continuous Integration

This project automates its own development workflow using GitHub Actions:

1. **Quality Check for Pull Requests**
   Triggered on every pull request to the `main` branch, this workflow ensures the code passes
   static analysis and unit tests.

2. **Release Workflow**
   When changes are pushed to `main`, this workflow handles versioning and releases with
   `semantic-release`. It automatically bumps the version, generates release notes, creates a tag.

## Contributing

Contributions are welcome! If you'd like to contribute, please follow the project's code style and
linting rules. Here are some commands to help you get started:

Check your code style:

```shell
./gradlew ktlintCheck
```

You can try an automatically apply the style by running:

```shell
./gradlew ktlintFormat
```

Detect potential issues:

```shell
./gradlew detekt
```

All commits must follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
format to ensure compatibility with our automated release system. A pre-commit hook is available to
validate commit messages.

You can set up hook to automate these checks before commiting and pushing your changes, to do so
update the Git hooks path:

```bash
git config core.hooksPath .githooks/
```

Refer to our [Contribution Guide](docs/CONTRIBUTING.md) for more detailed information.

## License

This project is licensed under the [MIT License](LICENSE).

[main-entry-point]: src/main/kotlin/ch/srgssr/pillarbox/backend/PillarboxBackendApplication.kt

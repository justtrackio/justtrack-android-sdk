# Contributing to justtrack SDK

Thank you for your interest in contributing to the justtrack SDK. This document provides guidelines and instructions for contributing to this project.

## Project Structure

- `sdk/` - Main Android SDK module
- `app/` - Sample/demo application
- `integrations/` - Integration modules
- `buildSrc/` - Build configuration source
- `buildSystem/` - Build system utilities
- `config/` - Project configuration files
- `scripts/` - Helper scripts

## Prerequisites

- Android Studio (latest stable version recommended)
- JDK 11 or higher
- Android SDK with appropriate API levels installed
- Gradle (wrapper included in the project)

## Code Quality

Before submitting changes, make sure your code passes all quality checks:

### Linting

```bash
./gradlew detekt
```

### Formatting

```bash
./gradlew ktlintFormat
```

### Running Tests

Unit tests:

```bash
./gradlew :sdk:testDebugUnitTest
```

Instrumentation tests (requires a connected device or emulator):

```bash
./gradlew :sdk:connectedDebugAndroidTest
```

## How to Contribute

1. Fork the repository.
2. Create a feature branch from `main`:

   ```bash
   git checkout -b feature/your-feature-name
   ```

3. Make your changes.
4. Ensure all linting, formatting, and tests pass.
5. Commit your changes with a clear, descriptive commit message.
6. Push your branch and open a merge request.

## Reporting Issues

If you find a bug or have a feature request, please open an issue with:

- A clear and descriptive title
- Steps to reproduce the issue (for bugs)
- Expected vs actual behavior
- SDK version and Android API level

## License

By contributing to this project, you agree that your contributions will be licensed under the [MIT License](LICENSE).

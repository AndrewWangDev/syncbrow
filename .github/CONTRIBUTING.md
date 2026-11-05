# Contributing to SyncBrow

Thank you for your interest in contributing to SyncBrow! We welcome contributions ranging from bug fixes and documentation enhancements to security hardening and localization.

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md) in all community interactions.

## Prerequisites & Development Setup

Before you start developing, make sure your environment is properly set up:

1. **JDK Toolchain**: **JDK 21 LTS** (Eclipse Temurin or OpenJDK 21). Set `JAVA_HOME` to this JDK.
2. **Android Studio**: Android Studio Iguana, Jellyfish, or newer.
3. **Android SDK**: Platform 34 with SDK Build-Tools 34.0.0.
4. **Git**: Git 2.30+ installed.

### Quick Verification
Clone and verify that your local toolchain compiles and passes all checks:

```bash
git clone https://github.com/<your-username>/SyncBrow.git
cd SyncBrow

# Run unit tests
./gradlew test

# Assemble release APK (with R8 obfuscation and resource shrinking)
./gradlew assembleRelease
```

## Development Workflow

1. **Create an Issue**: Before implementing substantial architectural changes, open a [Feature Proposal](https://github.com/AndrewWangDev/SyncBrow/issues/new?template=feature_request.yml) or [Bug Report](https://github.com/AndrewWangDev/SyncBrow/issues/new?template=bug_report.yml) so we can align on design.
2. **Branching**:
   - Branch off `main`.
   - Name your branch descriptively:
     - `feat/add-quad9-doh`
     - `fix/browser-tab-leak`
     - `docs/update-security-guide`
3. **Commit Standards (Conventional Commits)**:
   We enforce clear, standardized commit messages:
   - `feat: <summary>` for new features
   - `fix: <summary>` for bug fixes
   - `security: <summary>` for security fixes or hardening
   - `refactor: <summary>` for code restructuring
   - `docs: <summary>` for documentation
   - `test: <summary>` for unit or integration tests

## Coding & Security Standards

- **Kotlin Idioms**: Write clean, idiomatic Kotlin code leveraging Coroutines, Flows, and unidirectional data flow in Jetpack Compose.
- **Crypto & Secrets**: **NEVER** hardcode keys, passwords, testing tokens, or secrets. All cryptographic operations must adhere to modern cryptographic standards (AES-GCM, PBKDF2) using the centralized `CryptoManager`.
- **Defensive Limits**: When dealing with external input, network sockets, or file paths, always apply upper-bound length checks and resource cleanup blocks (`use {}`, `finally {}`).
- **Preserve ProGuard/R8 Rules**: If introducing reflection or Room entities, update `app/proguard-rules.pro` accordingly.

## Submitting a Pull Request

1. Rebase your branch onto the latest `origin/main`.
2. Ensure `./gradlew test` passes with zero failures.
3. Ensure `./gradlew assembleRelease` compiles cleanly.
4. Fill in all sections of the [Pull Request Template](PULL_REQUEST_TEMPLATE.md).
5. All automated GitHub Actions CI checks must pass before maintainer review.

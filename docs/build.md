# Build

## Toolchain

| Component | Version |
| --- | --- |
| compile SDK | 36 |
| target SDK | 36 |
| minimum SDK | 24 |
| Android Gradle Plugin | 8.10.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.2.21 |
| JDK | 17 |
| Java and Kotlin bytecode | 17 |
| Android Build Tools | 35.0.0 |

## Local Commands

The repository does not commit the Gradle Wrapper JAR. Install Gradle 8.11.1 or generate the wrapper deterministically before building:

```bash
gradle wrapper --gradle-version 8.11.1
./gradlew assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. The project can be configured and debug-built without release signing properties.

## Release Signing

A signed release build reads all four of these Gradle properties:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Release signing is enabled only when all four properties are available. The manual GitHub Actions workflow validates its repository secrets, writes the properties securely, restores the upload keystore inside the job, builds the AAB, and verifies the resulting signature strictly.

## Release Configuration

R8 minification and resource shrinking remain disabled. The app contains no native libraries and requests no Internet permission.

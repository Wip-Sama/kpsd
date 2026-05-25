# KPsd

KPsd is a high-performance Kotlin/JVM library for reading and writing Photoshop Document (PSD) files.

## Features
- Parse PSD files into structured object models.
- Support for layer channel data, text layers, descriptor structures, and engine data.
- Serialize structures back into binary PSD files.
- Support for RLE and Zip compression.

## Local Development

### Prerequisites
- JDK 21 or higher.

### Run Tests
```bash
./gradlew test
```

### Local Publishing
Publish to the local Maven repository (`~/.m2/repository`) for testing integrations:
```bash
./gradlew publishToMavenLocal
```

## Release & Publishing

### Automatic (GitHub Actions)
1. Commit and push your changes.
2. Tag the commit with `v<major>.<minor>.<patch>` (e.g., `v1.0.0`):
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. The GitHub Action will automatically build, test, and publish the artifact to GitHub Packages.

### Manual Publishing
Set the environment variables and run the publish command:
```bash
export GITHUB_ACTOR="your-github-username"
export GITHUB_TOKEN="your-github-personal-access-token"
./gradlew publish
```

## Usage

Add the library to your project's dependency catalog:

### 1. Configure Maven Repository
Add GitHub Packages to your repository list:
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Wip-Sama/kpsd")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### 2. Add Dependency
```kotlin
dependencies {
    implementation("com.wip:kpsd:1.0.0")
}
```

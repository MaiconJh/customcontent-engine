# Cloud Build

CustomContent Engine includes a GitHub Actions workflow for remote build and tests.

The workflow runs on GitHub-hosted Linux runners with Java 21 and Gradle cache enabled. It uses the Gradle Wrapper from this repository.

## Authenticate

```bash
gh auth login
```

## Trigger From The Current Branch

```bash
./scripts/build-cloud.sh
```

The script detects the current branch and runs:

```bash
gh workflow run build-test.yml --ref <current-branch>
```

## Watch The Run

```bash
gh run watch
```

## Manual Trigger

```bash
gh workflow run build-test.yml --ref main
```

## Tasks

The workflow executes:

```bash
./gradlew test --no-daemon
./gradlew build --no-daemon
./gradlew integrationTest --no-daemon
```

`test` includes unit tests and Architecture Fitness Functions.

## Artifacts

When available, the workflow uploads:

- `build/libs/*.jar`
- `build/reports/tests/`
- `build/reports/`
- `build/test-results/`

Artifacts are available from the GitHub Actions run page.

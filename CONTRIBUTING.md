# Contributing

Thanks for contributing to this Jenkins plugin family.

## Development environment

- Java 21
- Maven 3.9.6 or newer

Run before opening a pull request:

```bash
mvn -B -ntp verify
```

Provider repositories require the matching `iac-pipeline-api` build to be installed first:

```bash
(cd ../iac-pipeline-api-plugin && mvn -B -ntp install)
mvn -B -ntp verify
```

## Cross-repository changes

Shared lifecycle or provider contracts belong in `iac-pipeline-api-plugin` first. Keep provider changes coordinated with the core branch/version they require.

Public Pipeline DSL changes must be documented and tested. Avoid unnecessary breaking changes once a plugin has a published release.

## Tests

Add or update tests for behavior changes. Jenkins-specific lifecycle, descriptor, Pipeline, persistence, or restart behavior should use the Jenkins test harness rather than only plain unit tests.

## Security

Never commit credentials, API tokens, PEM keys, production endpoints containing secrets, or sensitive build logs. Follow [SECURITY.md](SECURITY.md) for vulnerability reporting.

## Pull requests

Keep changes focused, document user-visible behavior, and make sure CI is green before merge.

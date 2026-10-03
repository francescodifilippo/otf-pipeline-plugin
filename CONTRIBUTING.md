# Contributing

This repository is an independently versioned Jenkins plugin. Changes to shared
interfaces belong in `iac-pipeline-api-plugin` first; update dependency versions
deliberately. Do not merge API-breaking changes into dependent provider plugins
without cross-repository compatibility checks.

## Development

Use Java 21 and Maven 3.9.6+. Run `mvn -B -ntp verify` before submitting PRs.
Changes to Declarative stage options require JenkinsRule and Pipeline parser tests.
Until integration validation is complete, treat these sources as experimental.

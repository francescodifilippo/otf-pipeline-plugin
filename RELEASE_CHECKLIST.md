# Release checklist — OTF Pipeline

Current status: **source prototype**, not a verified installable or publicly released plugin.

- [ ] Verify the Maven build and Jenkins plugin dependencies resolve.
- [ ] Complete resolved dependency-tree license audit and mandatory third-party notices (see `DEPENDENCY_LICENSE_REVIEW.md`).
- [ ] Add / run JenkinsRule + Declarative Pipeline validation and restart tests.
- [ ] Validate HTTPS configuration, permissions and credential handling.
- [ ] Run contract tests against supported live OTF versions.
- [ ] Review remote execution failure, stop/restart behavior and asynchronous race conditions.
- [ ] Add repository URL, SCM and developer ownership once the GitHub repository is created.
- [ ] Check Artifact ID availability before submitting a Jenkins hosting request.
- [ ] Build and test an HPI candidate and perform a safe internal install.
- [ ] Complete Jenkins plugin hosting / distribution authorization for an official release.
- [ ] Release the core plugin as a Maven/Jenkins artifact before releasing this provider; switch off the SNAPSHOT dependency.

Official Jenkins documentation: https://www.jenkins.io/doc/developer/publishing/requesting-hosting/

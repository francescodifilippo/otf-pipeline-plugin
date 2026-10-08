# Release checklist — OTF Pipeline

Status: **pre-release**.

## Repository readiness

- [x] MIT license declared in POM and repository
- [x] Jenkins-compatible artifact ID and `io.jenkins.plugins` group ID
- [x] project URL, SCM metadata, and GitHub issue tracker declared in POM
- [x] Java 21 GitHub Actions build
- [x] root `Jenkinsfile` for future `ci.jenkins.io`
- [x] Dependabot, CODEOWNERS, PR template, CONTRIBUTING and security guidance
- [x] documentation-as-code README and Pipeline examples
- [x] Jenkins test-harness plugin wiring coverage
- [ ] Validate provider-side approval behavior and status mapping against a real OTF deployment

## Before Jenkins hosting / first release

- [ ] Release `iac-pipeline-api` first and replace the provider SNAPSHOT dependency with the released version
- [ ] Complete resolved dependency-tree and bundled-license audit
- [ ] Validate against a real non-production provider endpoint
- [ ] Add deeper Pipeline restart/abort integration coverage
- [ ] Run Jenkins Plugin Compatibility Tester / plugin verifier
- [ ] Perform an internal HPI installation on the target Jenkins baseline
- [ ] Open Jenkins plugin hosting request
- [ ] After Jenkins forks the repository, update project/SCM URLs to the canonical `jenkinsci` repository
- [ ] Obtain Jenkins repository release permissions
- [ ] Publish release notes

Official hosting guide: https://www.jenkins.io/doc/developer/publishing/requesting-hosting/

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
- [ ] Validate `otfWorkspace` creation (VCS connection, plain and sensitive variables), its restart behaviour, and the required team permission against a real OTF deployment

## Before Jenkins hosting / first release

- [ ] Release `iac-pipeline-api` first and replace the provider SNAPSHOT dependency with the released version
- [x] Complete resolved dependency-tree and bundled-license audit: the HPI bundles no third-party library, only Jenkins plugin dependencies
- [ ] Validate against a real non-production provider endpoint
- [x] Add deeper Pipeline restart/abort integration coverage (`OtfWorkspaceRestartTest`; abort while polling is covered in the core)
- [x] Run Jenkins Plugin Compatibility Tester / plugin verifier (PCT 1614.v891225c29865 on 2026-10-30: all tests pass against Jenkins 2.580.2, the latest LTS; the BOM 2.580.x plugins have the same versions as the tested ones, so this checks the newer core)
- [x] Perform an internal HPI installation on the target Jenkins baseline (2.568.3 on 2026-10-30: plugins resolved by the BOM, `agent none` Declarative builds; `otfWorkspace` and `terrakubeWorkspace` against a local stub, `ociRmStack` up to the real OCI endpoint (401 with a dummy key))
- [ ] Open Jenkins plugin hosting request
- [ ] After Jenkins forks the repository, update project/SCM URLs to the canonical `jenkinsci` repository
- [ ] Obtain Jenkins repository release permissions
- [ ] Publish release notes

Official hosting guide: https://www.jenkins.io/doc/developer/publishing/requesting-hosting/

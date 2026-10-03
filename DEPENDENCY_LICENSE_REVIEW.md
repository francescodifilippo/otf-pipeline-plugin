# Dependency license review (pre-release)

Project license: **MIT**, copyright (c) 2026 Francesco Di Filippo.

This review covers direct dependencies **declared in the source POM** and does not certify the Maven-resolved transitive dependency tree or binary artifacts. It must be updated using resolved versions after the first successful Maven build and before distributing HPI files. Direct Jenkins plugin dependencies are installed alongside plugins, while some Java libraries may be bundled; verify actual package contents.

| Direct dependency | Declared use | Upstream-reported license / preliminary status |
| --- | --- | --- |
| `workflow-step-api` | Core | MIT ([upstream](https://github.com/jenkinsci/workflow-step-api-plugin)) |
| `credentials` | Core | MIT ([upstream](https://github.com/jenkinsci/credentials-plugin)) |
| `plain-credentials` | Core | MIT ([upstream](https://github.com/jenkinsci/plain-credentials-plugin)) |
| `jackson2-api` | Core | Apache-2.0 (upstream POM also lists BSD-3-Clause for an included ASM component; [upstream](https://github.com/jenkinsci/jackson2-api-plugin/blob/master/pom.xml)) |
| `structs` | Core | **Pending**: review license declared for the exact BOM-resolved version ([upstream](https://github.com/jenkinsci/structs-plugin)) |
| `pipeline-model-definition` | All three | MIT ([upstream](https://plugins.jenkins.io/pipeline-model-definition/)) |
| `pipeline-model-extensions` | All three | MIT ([upstream](https://plugins.jenkins.io/pipeline-model-extensions/)) |
| `workflow-cps` | All three | **Pending**: review exact BOM-resolved release metadata ([upstream](https://github.com/jenkinsci/workflow-cps-plugin)) |
| `iac-pipeline-api` | OTF, Terrakube | MIT (this project's own shared plugin) |
| `junit-jupiter` | All three, test only | EPL-2.0 ([upstream](https://github.com/junit-team/junit-framework/blob/main/LICENSE.md)); not intended for HPI runtime distribution |

**No direct declared dependency has been identified that inherently requires relicensing this project from MIT.** This is a preliminary finding, *not* final clearance for distribution. OTF and Terrakube are contacted over HTTP; their server code is not included in these source projects.

## Mandatory release checks

1. Build `iac-pipeline-api` first (`mvn -B -ntp clean verify install`) with Maven and then `mvn -B -ntp clean verify` independently in the two provider plugins.
2. Generate each resolved dependency tree (`mvn -B -ntp dependency:tree`), review all production and runtime dependencies, their exact license notices, and any third-party notices bundled in generated HPI archives.
3. Resolve any missing or conflicting licensing information, including `structs`/`workflow-cps`; include mandatory third-party notices when distributing bundled binaries.
4. Run Jenkins plugin verifier and integration/Declarative Pipeline tests before making any compatibility or production-readiness claim.

Official Jenkins policy: https://www.jenkins.io/doc/developer/publishing/preparation/

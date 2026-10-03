# OTF Pipeline

**Proposed Jenkins plugin ID:** `otf-pipeline`  
**Proposed GitHub repository:** `otf-pipeline-plugin`  
**Version:** `0.1.0-SNAPSHOT` (source prototype)

Declarative Jenkins Pipeline integration with **OTF**. The provider-specific stage options are `otfProvision` for submission and `otfAwait` for optional deferred waiting, including the `waitForCompletion` flag. See `examples/Jenkinsfile.otf` for a prototype Jenkinsfile. Connection and credentials configuration is managed through Jenkins.

Requires the separate **`iac-pipeline-api`** Jenkins plugin (mandatory dependency), currently version `0.1.0-SNAPSHOT`.

## Local build from three sibling Git repositories

Ensure `../iac-pipeline-api-plugin` has the same core SNAPSHOT version referenced in this repo's POM:

```bash
(cd ../iac-pipeline-api-plugin && mvn -B -ntp install)
mvn -B -ntp verify
```

The GitHub Actions workflow checks out the core repository from the **same GitHub owner** and builds it first; if the core repository is private, configure checkout access explicitly. The workflow cannot pass until the core repository exists remotely.

## Example

The Declarative stage options accept `workspaceId`, `mode`, and support background submission via `waitForCompletion: false` followed by `otfAwait` in a later stage.

## Release order and caveats

Release `iac-pipeline-api` first and replace the core SNAPSHOT dependency in this POM with its tested release version. Validate the Declarative syntax with JenkinsRule, controller restart/abort cases and the provider's API before shipping an HPI. **The source ZIP is not an installable plugin.**

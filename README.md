# OTF Pipeline

**Plugin ID:** `otf-pipeline`  
**Status:** pre-release

Declarative Jenkins Pipeline integration for Open Terraforming Framework (OTF). The plugin submits remote OTF runs through the shared [IaC Pipeline API](https://github.com/francescodifilippo/iac-pipeline-api-plugin) and can either wait for completion or continue the Jenkins Pipeline and await the run later. It can also create the workspaces those runs use.

## Requirements

- Jenkins 2.568.3 or newer
- Java 21
- `iac-pipeline-api` of the matching tested version
- an OTF API endpoint and Jenkins credential

## Installation

For local pre-release testing, build and install the core first:

```bash
(cd ../iac-pipeline-api-plugin && mvn -B -ntp install)
mvn -B -ntp verify
```

The generated HPI is under `target/`.

## Configuration

In **Manage Jenkins → System → OTF connections**, configure the API connection and Jenkins credential. Production endpoints should use HTTPS. Credentials are resolved at runtime and are not persisted in build operation metadata.

## Pipeline syntax

### `otfProvision`

Key parameters:

| Parameter | Purpose |
| --- | --- |
| `connection` | configured OTF connection name |
| `workspaceId` | OTF workspace ID (`ws-...`); set either `workspaceId` or `workspaceName` |
| `workspaceName` | exact workspace name, looked up when the stage runs (see [Workspace names](#workspace-names)) |
| `organizationName` | name of the OTF organization that contains the workspace; required with `workspaceName`, not allowed with `workspaceId` |
| `mode` | `plan` or `apply` |
| `operationKey` | build-local correlation key |
| `waitForCompletion` | wait now or continue after submission |
| `pollingSeconds` | remote status polling interval |
| `timeoutMinutes` | maximum Jenkins-side wait |

### `otfAwait`

Uses `connection` and a previously submitted `operationKey` to resume waiting later in the same build. The provision stage it refers to must set that `operationKey` explicitly.

### `otfWorkspace`

Finds a workspace by name, or creates it with its variables, then runs the stage. It starts no run. See [Workspace creation](#workspace-creation).

| Parameter | Purpose |
| --- | --- |
| `connection` | configured OTF connection name |
| `organizationName` | name of the OTF organization that contains the workspace |
| `workspaceName` | exact workspace name |
| `vcsProviderId` | optional ID of an OTF VCS provider of the organization (`vcs-...`); requires `repository` |
| `repository` | repository path in the VCS provider, for example `acme/infrastructure`; requires `vcsProviderId` |
| `branchName` | optional branch; the repository's default branch otherwise |
| `workingDirectory` | optional directory of the Terraform configuration inside the repository |
| `terraformVersion` | optional Terraform version, for example `1.6.0` or `latest` |
| `executionMode` | optional `remote`, `local` or `agent`; OTF's default otherwise |
| `agentPoolId` | agent pool ID (`apool-...`); required with `executionMode: 'agent'` and allowed only with it |
| `description` | optional workspace description |
| `variables` | optional map of plain Terraform variables |
| `environmentVariables` | optional map of plain environment variables |
| `sensitiveVariables` | optional map from Terraform variable name to the ID of a Jenkins Secret text credential |
| `sensitiveEnvironmentVariables` | optional map from environment variable name to the ID of a Jenkins Secret text credential |
| `ifExists` | `keep` (default) or `fail` when the workspace already exists |

## Workspace names

A stage can name its workspace instead of giving its ID:

```groovy
stage('Plan') {
  options {
    otfProvision(
      connection: 'otf-prod',
      organizationName: 'acme',
      workspaceName: 'network',
      mode: 'plan',
      operationKey: 'network-plan'
    )
  }
  steps { echo 'Plan completed' }
}
```

- Set exactly one of `workspaceId` or `workspaceName`. `workspaceName` requires `organizationName`, and `organizationName` is rejected with `workspaceId`, so a stage never carries an organization that is not used. These checks run when the stage starts, before any OTF call.
- Names follow OTF's rules: `organizationName` uses letters, numbers, hyphens and underscores; `workspaceName` also allows dots. The plugin limits each to 255 characters (OTF sets no limit) and rejects the workspace names `.` and `..`, which cannot be looked up by name.
- When the stage runs, the plugin reads the workspace with the stage's connection through OTF's Terraform-compatible "show workspace by name" endpoint, `GET /api/v2/organizations/:organization/workspaces/:name`. Names are case-sensitive and must match exactly. If OTF answers 404 (no such organization or workspace), the stage fails before any run is created. Other errors, such as 403 for a token that cannot read the workspace, fail the stage with their HTTP status. Lookup failures are not retried.
- The build log shows the resolved workspace, for example `IaC otf: resolved target to ws-abc123`.
- Everything after the lookup uses the workspace ID: the stored operation, the OTF run, and status polling. The lookup runs once per `otfProvision` stage. `otfAwait` refers to an operation by `operationKey` and never looks up names.
- A stage with `workspaceId` makes no lookup call and behaves as before.

The lookup needs the workspace `read` permission in OTF. The `plan`, `write` and `admin` workspace roles include it, as do organization owners, so a token that can create runs on the workspace can also look it up.

See [examples/Jenkinsfile.otf-by-name](examples/Jenkinsfile.otf-by-name).

## Workspace creation

`otfWorkspace` creates a workspace in a stage of its own, separate from the stages that run plans and applies:

```groovy
stage('Workspace') {
  options {
    otfWorkspace(
      connection: 'otf-prod',
      organizationName: 'acme',
      workspaceName: 'network',
      vcsProviderId: 'vcs-example',
      repository: 'acme/infrastructure',
      branchName: 'main',
      variables: [region: 'eu-west-1', subnet_count: 3],
      sensitiveVariables: [db_password: 'network-db-password']
    )
  }
  steps { echo 'Workspace is ready' }
}
```

A later stage runs plans on it with `otfProvision(organizationName: 'acme', workspaceName: 'network')`, as described in [Workspace names](#workspace-names). See [examples/Jenkinsfile.otf-create-workspace](examples/Jenkinsfile.otf-create-workspace).

### Existing workspaces

When the stage runs, the plugin reads the workspace by name, as `otfProvision` does.

- No workspace: the plugin creates it, then creates its variables.
- An existing workspace: with `ifExists: 'keep'` (default) the stage uses it. Its settings and variables are not compared or changed. With `ifExists: 'fail'` the stage fails.

OTF keeps workspace names unique in an organization. If another build creates the workspace at the same time, OTF rejects the second create, and the plugin treats the workspace as existing.

### VCS repository

The VCS provider must already exist in the organization; the plugin does not create one. Without `vcsProviderId` and `repository` the workspace has no repository, and runs need a configuration uploaded by other means, such as the Terraform CLI. When a repository is connected, OTF registers a webhook in it through the VCS provider.

### Variables

- `variables` and `environmentVariables` hold plain values. Numbers and booleans are turned into strings. They are visible in OTF to anyone who can read the workspace.
- `sensitiveVariables` and `sensitiveEnvironmentVariables` name a Jenkins **Secret text** credential for each variable. The plugin reads the credential when it creates the variable and sends the value to OTF as a sensitive variable, which OTF never shows again. The value is not stored in the build and never written to the log.
- A name can be in either the plain or the sensitive map of its category, not both.
- All values are sent as strings (`hcl: false`).

The plugin checks that every credential exists before it creates the workspace. The build log shows the variable names, never their values.

If a variable cannot be created, the stage fails and names the workspace. The workspace then exists with only some of its variables, and a later run of the stage keeps it as it is: set the missing variables in OTF, or delete the workspace before running the stage again.

### Restarts

The step holds no executor. It saves that it sent the create request before sending it. If Jenkins restarts afterwards, the resumed step finds the workspace, treats it as its own, and creates only the variables that are still missing.

### Permissions

- **User or team token:** creating a workspace needs a team with the organization permission **manage workspaces**, or the owners team. That permission also covers creating the workspace's variables.
- **Organization token:** it can create workspaces and their variables in its organization, but OTF does not let it create runs. A pipeline that also runs `otfProvision` needs a user or team token for those stages, on another connection.

## Operation keys

- Without `operationKey`, the default key is derived from the provider, `workspaceId` (or `organizationName` and `workspaceName` instead), and `mode`, so a plan stage and an apply stage on the same workspace both submit. Default keys are internal: do not reference them from `otfAwait`.
- Reusing an `operationKey` in another `otfProvision` stage of the same build fails that stage instead of adopting the earlier run. This includes a provision stage re-run in the same build, for example inside `retry`: give each attempt its own key.

## Run status handling

Runs are always created with `auto-apply: false`. The plugin maps OTF run statuses as follows:

| OTF status | Result |
| --- | --- |
| `applied`, `planned_and_finished` | success; the stage body runs |
| `errored`, `canceled`, `force_canceled`, `discarded` | the stage fails |
| `planned`, `cost_estimated` | approval pending: the log reports it and polling continues until the run is confirmed in OTF or `timeoutMinutes` elapses |
| any other status (`pending`, `plan_queued`, `planning`, `confirmed`, `apply_queued`, `applying`, ...) | in progress; polling continues |

- `mode: 'plan'` creates a plan-only run, which ends in `planned_and_finished`.
- `mode: 'apply'` waits in `planned` for confirmation in the OTF UI. An apply run without changes ends in `planned_and_finished` and counts as success. Discarding the run in OTF fails the stage.
- The plugin does not confirm runs itself.

Transient status poll errors (I/O errors, HTTP 5xx, 408, 429) are retried as described in the IaC Pipeline API; other HTTP 4xx responses fail the stage.

## Example

```groovy
stage('Start OTF plan') {
  options {
    otfProvision(
      connection: 'otf-prod',
      workspaceId: 'ws-prod',
      mode: 'plan',
      operationKey: 'network-plan',
      waitForCompletion: false
    )
  }
  steps { echo 'Plan submitted' }
}

stage('Await OTF plan') {
  options {
    otfAwait(
      connection: 'otf-prod',
      operationKey: 'network-plan',
      timeoutMinutes: 90
    )
  }
  steps { echo 'Plan completed' }
}
```

See [examples/Jenkinsfile.otf](examples/Jenkinsfile.otf), and [examples/Jenkinsfile.otf-by-name](examples/Jenkinsfile.otf-by-name) for a workspace named by `organizationName` and `workspaceName`. [examples/Jenkinsfile.otf-create-workspace](examples/Jenkinsfile.otf-create-workspace) creates the workspace first.

## Asynchronous execution

When `waitForCompletion: false` is used, the provider submits the OTF run, persists its remote identity through `iac-pipeline-api`, and allows Jenkins to continue. A later `otfAwait` waits for that same operation without requiring the remote run ID in the Jenkinsfile.

## Restart and durability

Once the remote ID has been persisted, polling can continue after a Jenkins controller restart. OTF currently does not opt in to idempotent resubmission, so an interruption before the remote ID is stored fails closed and requires provider-side reconciliation.

## Security

Store API credentials in Jenkins Credentials, not Pipeline source. Do not persist tokens in operation metadata. See [SECURITY.md](SECURITY.md).

The token needs the `plan` workspace role (or a higher one) to create runs, in either mode. `otfWorkspace` needs the organization permission **manage workspaces** (see [Permissions](#permissions)); use a separate connection and token for pipelines that create workspaces. The plugin never confirms an apply run; that is done in OTF by a user with the `write` role. Stages that use `workspaceName` also read the workspace, which the `plan` role already allows.

## Compatibility

The public DSL uses `connection`, like the other IaC provider plugins, and the OTF-specific `workspaceId`, with `organizationName` and `workspaceName` as an alternative to `workspaceId`, like the Terrakube provider; internally they map to the provider-neutral core connection and target identifiers.

After `0.1.0-beta-1`, the step parameters `server` and `organization` were renamed to `connection` and `organizationName`. Pipelines written for that pre-release must replace `server:` with `connection:` and `organization:` with `organizationName:`. The saved connection list was renamed too, so connections configured with `0.1.0-beta-1` must be entered again.

## Development

```bash
(cd ../iac-pipeline-api-plugin && mvn -B -ntp install)
mvn -B -ntp verify
```

GitHub Actions automatically uses a same-named core branch when present, otherwise it falls back to core `main`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT License. See [LICENSE](LICENSE).

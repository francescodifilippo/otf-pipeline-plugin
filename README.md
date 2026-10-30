# OTF Pipeline

**Plugin ID:** `otf-pipeline`  
**Status:** pre-release

Declarative Jenkins Pipeline integration for Open Terraforming Framework (OTF). The plugin submits remote OTF runs through the shared [IaC Pipeline API](https://github.com/francescodifilippo/iac-pipeline-api-plugin) and can either wait for completion or continue the Jenkins Pipeline and await the run later.

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
| `server` | configured OTF connection name |
| `workspaceId` | OTF workspace ID (`ws-...`); set either `workspaceId` or `workspaceName` |
| `workspaceName` | exact workspace name, looked up when the stage runs (see [Workspace names](#workspace-names)) |
| `organization` | OTF organization that contains the workspace; required with `workspaceName`, not allowed with `workspaceId` |
| `mode` | `plan` or `apply` |
| `operationKey` | build-local correlation key |
| `waitForCompletion` | wait now or continue after submission |
| `pollingSeconds` | remote status polling interval |
| `timeoutMinutes` | maximum Jenkins-side wait |

### `otfAwait`

Uses `server` and a previously submitted `operationKey` to resume waiting later in the same build. The provision stage it refers to must set that `operationKey` explicitly.

## Workspace names

A stage can name its workspace instead of giving its ID:

```groovy
stage('Plan') {
  options {
    otfProvision(
      server: 'otf-prod',
      organization: 'acme',
      workspaceName: 'network',
      mode: 'plan',
      operationKey: 'network-plan'
    )
  }
  steps { echo 'Plan completed' }
}
```

- Set exactly one of `workspaceId` or `workspaceName`. `workspaceName` requires `organization`, and `organization` is rejected with `workspaceId`, so a stage never carries an organization that is not used. These checks run when the stage starts, before any OTF call.
- Names follow OTF's rules: `organization` uses letters, numbers, hyphens and underscores; `workspaceName` also allows dots. The plugin limits each to 255 characters (OTF sets no limit) and rejects the workspace names `.` and `..`, which cannot be looked up by name.
- When the stage runs, the plugin reads the workspace with the stage's connection through OTF's Terraform-compatible "show workspace by name" endpoint, `GET /api/v2/organizations/:organization/workspaces/:name`. Names are case-sensitive and must match exactly. If OTF answers 404 (no such organization or workspace), the stage fails before any run is created. Other errors, such as 403 for a token that cannot read the workspace, fail the stage with their HTTP status. Lookup failures are not retried.
- The build log shows the resolved workspace, for example `IaC otf: resolved target to ws-abc123`.
- Everything after the lookup uses the workspace ID: the stored operation, the OTF run, and status polling. The lookup runs once per `otfProvision` stage. `otfAwait` refers to an operation by `operationKey` and never looks up names.
- A stage with `workspaceId` makes no lookup call and behaves as before.

The lookup needs the workspace `read` permission in OTF. The `plan`, `write` and `admin` workspace roles include it, as do organization owners, so a token that can create runs on the workspace can also look it up.

See [examples/Jenkinsfile.otf-by-name](examples/Jenkinsfile.otf-by-name).

## Operation keys

- Without `operationKey`, the default key is derived from the provider, `workspaceId` (or `organization` and `workspaceName` instead), and `mode`, so a plan stage and an apply stage on the same workspace both submit. Default keys are internal: do not reference them from `otfAwait`.
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
      server: 'otf-prod',
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
      server: 'otf-prod',
      operationKey: 'network-plan',
      timeoutMinutes: 90
    )
  }
  steps { echo 'Plan completed' }
}
```

See [examples/Jenkinsfile.otf](examples/Jenkinsfile.otf), and [examples/Jenkinsfile.otf-by-name](examples/Jenkinsfile.otf-by-name) for a workspace named by `organization` and `workspaceName`.

## Asynchronous execution

When `waitForCompletion: false` is used, the provider submits the OTF run, persists its remote identity through `iac-pipeline-api`, and allows Jenkins to continue. A later `otfAwait` waits for that same operation without requiring the remote run ID in the Jenkinsfile.

## Restart and durability

Once the remote ID has been persisted, polling can continue after a Jenkins controller restart. OTF currently does not opt in to idempotent resubmission, so an interruption before the remote ID is stored fails closed and requires provider-side reconciliation.

## Security

Store API credentials in Jenkins Credentials, not Pipeline source. Do not persist tokens in operation metadata. See [SECURITY.md](SECURITY.md).

The token needs the `plan` workspace role (or a higher one) to create runs, in either mode. The plugin never confirms an apply run; that is done in OTF by a user with the `write` role. Stages that use `workspaceName` also read the workspace, which the `plan` role already allows.

## Compatibility

The public DSL intentionally retains the OTF-specific names `server` and `workspaceId`, with `organization` and `workspaceName` as an alternative to `workspaceId`; internally they map to the provider-neutral core connection and target identifiers.

## Possible future features

These ideas are not planned and may never be implemented. They are listed so that the current scope is clear.

- **Workspace creation.** The plugin works only with workspaces that already exist in OTF. A future step could create a workspace, or update its settings such as the VCS repository and variables, and reuse an existing workspace instead of creating a duplicate. Creating and configuring workspaces is a different concern from running plans and applies on them, so this does not seem to belong in this plugin at the moment.

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

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
| `workspaceId` | OTF workspace/target |
| `mode` | `plan` or `apply` |
| `operationKey` | build-local correlation key |
| `waitForCompletion` | wait now or continue after submission |
| `pollingSeconds` | remote status polling interval |
| `timeoutMinutes` | maximum Jenkins-side wait |

### `otfAwait`

Uses `server` and a previously submitted `operationKey` to resume waiting later in the same build. The provision stage it refers to must set that `operationKey` explicitly.

## Operation keys

- Without `operationKey`, the default key is derived from the provider, `workspaceId`, and `mode`, so a plan stage and an apply stage on the same workspace both submit. Default keys are internal: do not reference them from `otfAwait`.
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

See [examples/Jenkinsfile.otf](examples/Jenkinsfile.otf).

## Asynchronous execution

When `waitForCompletion: false` is used, the provider submits the OTF run, persists its remote identity through `iac-pipeline-api`, and allows Jenkins to continue. A later `otfAwait` waits for that same operation without requiring the remote run ID in the Jenkinsfile.

## Restart and durability

Once the remote ID has been persisted, polling can continue after a Jenkins controller restart. OTF currently does not opt in to idempotent resubmission, so an interruption before the remote ID is stored fails closed and requires provider-side reconciliation.

## Security

Store API credentials in Jenkins Credentials, not Pipeline source. Do not persist tokens in operation metadata. See [SECURITY.md](SECURITY.md).

## Compatibility

The public DSL intentionally retains the OTF-specific names `server` and `workspaceId`; internally they map to the provider-neutral core connection and target identifiers.

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

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A teaching project: one Spring Boot application routes each authenticated user's jobs to that
tenant's own Temporal namespace and task queue, and each tenant's workers run in their own
Kubernetes namespace with their own credential. Java 21, Spring Boot 4.1.2, Temporal Java SDK
1.40.0. The Maven enforcer rule rejects any Java major version other than 21 and Maven older
than 3.9.9.

## Commands

This Mac's default Java is 23, which fails the enforcer rule. Point Maven at 21 first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn verify                                   # compile, run all tests, package the jar
mvn test -Dtest=RoutingTest                  # one test class
mvn test -Dtest=RoutingTest#methodName       # one test method
./scripts/check.sh                           # mvn verify + bash -n + shellcheck on scripts/
```

Minikube demonstration (targets only the minikube profile and kubectl context
`temporal-tenants`; mode is `local` or `cloud`):

```bash
./scripts/start-minikube.sh
./scripts/build-minikube.sh          # tests, packages, builds the image inside minikube
./scripts/deploy-minikube.sh local
./scripts/test-minikube.sh local     # deletes and scales down Tenant A's worker pods
```

`cloud` mode needs `TENANT_A_NAMESPACE`, `TENANT_B_NAMESPACE`, `TENANT_A_KEY`, `TENANT_B_KEY`,
`TENANT_A_WORKER_KEY`, `TENANT_B_WORKER_KEY` exported (see `scripts/common.sh` `load_mode`).

## Architecture

One jar, three roles, chosen by Spring profile (`SPRING_PROFILES_ACTIVE`, set in the
`k8s/*.yaml.tpl` templates; default is `app`):

| Profile | Classes | Role |
| --- | --- | --- |
| `app` | `AppConfiguration`, `SecurityConfiguration`, `JobController`, `RequestErrors` | Web application. Holds one Temporal connection and client per tenant. |
| `worker` | `WorkerConfiguration` | Polls one tenant's task queue in one namespace. No web server. |
| `probe` | `AccessProbe` | Tries Tenant A's operations with Tenant B's credential, to show whether the server refuses. No web server. |

Request flow in the `app` profile:

```text
HTTP request -> SecurityConfiguration (demo users alice/bob)
  -> TenantResolver: tenant comes ONLY from the "tenant:<id>" authority, never from headers
  -> JobService: picks the TenantClient for that tenant (Map keyed by tenant id)
  -> WorkflowClient bound to that tenant's namespace, task queue "<tenant>-work"
```

The tenant rules are enforced in record constructors, so a wrong configuration fails at startup:

- `TenantRoute`: tenant must be `tenant-a` or `tenant-b`; namespace must equal the tenant id or
  start with `<tenant>.` (Cloud names like `tenant-a.acme123`); queue must be `<tenant>-work`.
- `WorkerLocation`: the worker's tenant must equal its Kubernetes namespace (`POD_NAMESPACE`).
  Its `identity()` (`<pod namespace>/<pod name>`) is the Temporal worker identity, which the
  minikube test checks against workflow history.
- `DemoActivities` calls `requireTenant` so a worker rejects another tenant's input.
- `Connections.options`: secured mode requires a key and uses TLS; local mode refuses a key,
  because the local connection is unencrypted.

Workflow IDs are `<tenant>/job/<jobId>` with `jobId` matching `[A-Za-z0-9_-]{1,64}`
(`JobService.workflowId`). Duplicate IDs are rejected. `JobWorkflowImpl` runs three activities
with a 20-second durable timer between steps.

## Kubernetes templates and scripts

`k8s/*.yaml.tpl` use `__PLACEHOLDER__` tokens filled by `sed` in `scripts/common.sh`
(`render_app`, `render_worker`); `scripts/render-policies.sh` generates the NetworkPolicies.
`ManifestTest` shells out to these same functions and fails if any `__` token or credential
appears in the output, so a new placeholder must be added to both the template and the sed
call. The image tag is derived from the jar's SHA-256, so build before deploy.

`local` mode uses `k8s/local-temporal.yaml`, a development server with no authorization and
in-memory state. There the access probe is expected to report
`LOCAL_SERVER_ACCEPTED_CROSS_TENANT_ACCESS`. Only `cloud` mode tests real isolation.

## Tests

The Java tests use Temporal's in-memory test service (`temporal-testing`); no cluster or
network account is needed. `WebSecurityTest` mocks Temporal job operations. `DemoActivitiesTest`
runs the real ten-second activity wait, so it is slow. `docs/testing.md` maps each test class
to what it checks and explains the minikube test's evidence files.

## Files and docs

- `.local/` holds credentials and `test-evidence/` holds test output; both are git-ignored.
  Never commit or print their contents.
- The Markdown under `docs/` is the editable source. `docs/handbook.html` is a pandoc-generated
  single-file copy of the whole guide, so update it after changing the Markdown docs.
- `docs/verification.md` records which checks were actually run for this delivery. Update it
  when what has been verified changes.

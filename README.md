# Temporal tenant routing with Java 21

```text
                    One Spring Boot application
                      verified user -> tenant
                        /                \
                       v                  v
             Shared Temporal service (remembers progress)
             +-----------------+  +-----------------+
             | namespace A     |  | namespace B     |
             | tenant-a-work   |  | tenant-b-work   |
             +--------+--------+  +--------+--------+
                      ^                    ^
                      | workers poll       | workers poll
             +--------+--------+  +--------+--------+
             | Kubernetes      |  | Kubernetes      |
             | tenant-a        |  | tenant-b        |
             | Spring workers  |  | Spring workers  |
             | A credential    |  | B credential    |
             +-----------------+  +-----------------+
```

This project teaches tenant routing, worker replacement, and access restrictions. It uses
Java 21, Spring Boot 4.1.2, and Temporal Java 1.40.0. Maven rejects a different Java major version.
Both workflow tasks and activity tasks stay on each tenant's configured route.

There are two test modes:

| Mode | Temporal service | What the test proves |
| --- | --- | --- |
| `local` | Development server inside minikube | Routing and worker recovery. The access probe deliberately demonstrates that Temporal authorization is absent. |
| `cloud` | Your two secured Temporal Cloud namespaces | The same routing and recovery checks, plus rejection of Tenant B's credential when it polls or reads Tenant A's work. |

Local mode is a learning setup. A worker that changes its namespace setting can read another
tenant's work there. Use the secured mode to test the security requirement.

## Read in this order

1. [Concepts, diagrams, design comparison, and Java configuration](docs/guide.md)
2. [Run on Windows Subsystem for Linux 2 with minikube](docs/wsl-minikube.md)
3. [Configure Temporal Cloud credentials](docs/cloud.md)
4. [Understand the isolation controls](docs/security.md)
5. [Run and interpret the tests](docs/testing.md)
6. [Use a self-hosted Temporal service](docs/self-hosted.md)
7. [Official sources and pinned versions](docs/sources.md)
8. [Checks performed on this delivery](docs/verification.md)

The diagrams are plain text so they remain readable offline. `docs/handbook.html` contains the
whole guide in one browser-readable file; the Markdown files remain the editable sources.

## Quick start inside your Linux terminal

Install the prerequisites in the [Windows/Linux guide](docs/wsl-minikube.md), then run from
the extracted project directory:

```bash
java -version                     # Must say 21
mvn -version                      # Must also use Java 21
./scripts/start-minikube.sh
./scripts/build-minikube.sh        # Tests, packages, and builds the image inside minikube
./scripts/deploy-minikube.sh local
./scripts/test-minikube.sh local
```

These scripts target only the minikube profile and Kubernetes context named `temporal-tenants`.
They create demonstration resources when you run them. The test deletes one busy Tenant A pod,
then stops and restarts all of Tenant A's workers. Use this disposable cluster only.

To run Java tests without Kubernetes:

```bash
mvn verify
```

To also check the shell scripts:

```bash
./scripts/check.sh
```

## Files to follow through the example

| File under `src/main/java/example/tenants/` | Responsibility |
| --- | --- |
| `SecurityConfiguration.java` | Authenticates the two demonstration users. |
| `TenantResolver.java` | Gets the tenant from the authenticated user's server-assigned authority. |
| `AppConfiguration.java` | Holds the fixed tenant-to-namespace and tenant-to-queue mapping. |
| `JobService.java` | Starts workflows and reads results within the authenticated tenant's namespace. |
| `JobWorkflowImpl.java` | Runs three activities, with a durable 20-second timer between steps. |
| `WorkerConfiguration.java` | Creates a worker that polls one tenant's queue in one Temporal namespace. |
| `DemoActivities.java` | Simulates work and records the worker's location and attempt number. |
| `WorkerLocation.java` | Rejects accidental deployment in the wrong Kubernetes namespace. |
| `AccessProbe.java` | Tries Tenant A's operations using Tenant B's credential. |

`k8s/` contains deployment templates. `scripts/` renders them with matching names and runs the
demonstration. Credentials go into Kubernetes Secrets and private files under `.local/`.
Do not commit or share that directory. Test observations go into `test-evidence/`.

## What I cheated on

This delivery was compiled and tested on Java 21 on macOS. No minikube cluster, Windows/Linux
installation, Docker image build, or live Temporal Cloud access test was run here. The project
includes those runnable tests. Work is simulated, and login uses two demonstration users.
See [verification](docs/verification.md) for the exact checks and limits.

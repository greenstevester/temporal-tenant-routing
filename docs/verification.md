# Delivery verification

Checked on 15 November 2026 on macOS with Temurin 21.0.13+9 and Maven 3.9.9.

| Check | Result |
| --- | --- |
| `mvn verify` using Java 21 | Passed: 46 tests, zero failures, zero errors, zero skipped. Executable Spring Boot package created. |
| Maven Java-version rule | Passed with Java 21; the project requires major version 21. |
| `bash -n` for every script | Passed. |
| `shellcheck -x scripts/*.sh` | Passed without reported findings. |
| Local and Cloud template rendering | Passed in `ManifestTest`; no keys or unresolved template placeholders in rendered output. |
| Static Kubernetes document parsing | Passed in `ManifestTest`. |
| Current official documentation | Consulted for routing, security, recovery, affinity, Java configuration, and Windows/Linux minikube setup. Links are in the guide and source list. |

The tests use Temporal's in-memory test service. They exercise workflow execution, replay,
queue selection, namespace selection, duplicate-job rejection, activity behavior, and the
application's authentication and tenant routing. They do not emulate Cloud authorization.
The supplied live probe tests that authorization against the account you configure.

During development, a test-compilation error was corrected because the activity test environment
requires explicit closing. A password-encoding prefix was also corrected after authentication
tests failed. No tests were deleted or disabled to obtain the final pass.

## What I cheated on

* `scripts/start-minikube.sh`, `scripts/build-minikube.sh`, `scripts/deploy-minikube.sh`, and
  `scripts/test-minikube.sh` were not executed against a cluster here. This machine runs macOS,
  and no minikube installation or Windows/Linux environment was available. Shell checks and
  offline rendering do not prove cluster behavior.
* `Dockerfile` was not built into a container image here. Its base image manifest was checked,
  and the Java package compiled successfully. Container startup and the chosen memory limits
  still need the supplied minikube exercise.
* `AccessProbe.java` was not run against real Cloud credentials. The Java tests check its
  treatment of accepted calls, permission rejections, and timeouts. The full live sequence is
  exercised by `scripts/test-minikube.sh cloud`.
* `DemoActivities.java` simulates work with logging and a timed wait. It has no external business
  effect and therefore does not demonstrate duplicate protection for payments or database writes.
* `SecurityConfiguration.java` has two demonstration users with generated deployment passwords.
  A production identity provider is not included. Namespace names ending in `acme123` in the
  documentation are placeholders for your Cloud account.
* `WorkflowTest` and `JobServiceTest` use recording activities; `WebSecurityTest` mocks Temporal
  job operations. Framework startup, main methods, and every possible failure branch are not
  individually tested.
* `k8s/local-temporal.yaml` has no Temporal authorization and keeps service state in memory.
  Service durability, production self-hosted configuration, and storage recovery are outside
  this demonstration. No deployments or infrastructure changes were made for this delivery.
* The Kubernetes documents were parsed, but full server-side schema validation, policy
  enforcement, memory sizing, credential revocation, and hostile-container testing were not run.
* The archive contains source and documentation. Downloaded Java tools, Maven dependencies,
  compiled packages, credentials, and generated test evidence are excluded. The first build
  needs internet access.

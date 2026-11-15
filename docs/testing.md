# Tests and expected evidence

## Java tests without a running Temporal service

Run `mvn verify` with Java 21. Temporal's in-memory testing service records workflow history
and advances workflow timers so these tests need no Kubernetes installation or external account.
It is not a substitute for the authorization checks against your secured service.

| Test class | Behavior checked |
| --- | --- |
| `RoutingTest` | Trusted tenant identity; rejected unknown or ambiguous tenants; queue, namespace, and pod mismatch checks; validated job identifiers; connection credential rules; rejected false security-test results. |
| `WorkflowTest` | Both tenant queues route activities correctly; replay reads completed results without invoking activities again. |
| `JobServiceTest` | Starting, reading, and auditing workflows under each configured namespace; rejection of duplicate jobs. |
| `DemoActivitiesTest` | Recorded worker location, heartbeat activity, and rejection of the other tenant's input. |
| `WebSecurityTest` | Real password authentication, ignored forged tenant header, request-forgery protection, result/status/history routes, and input rejection. Temporal job operations are mocked here. |
| `ManifestTest` | Both modes render all three deployments without credentials or unresolved placeholders; static Kubernetes documents parse. This does not check the full Kubernetes schema or cluster admission rules. |

The workflow tests replace external business effects with recording activities. The activity
test runs the demonstration's real ten-second wait. No external paid services are contacted.

## The two-tenant minikube test

Run `./scripts/test-minikube.sh local` after local deployment, or `cloud` after secured deployment.
The test uses the already built image and refuses a mismatch with the application's deployed
image. It always targets context `temporal-tenants`.

1. Start a job for Alice and a job for Bob. Send a forged A tenant header with both requests;
   Bob must still use B. Save the initial pod lists.
2. Wait for the log proving an A activity is processing, then force-delete that busy pod.
   Wait for both jobs to finish. A's processing attempt number must be greater than one.
3. Scale A's worker Deployment to zero and wait until its worker pods disappear.
4. Start another job for each tenant. B must finish while A has no workers. A's status must
   remain running; its job is waiting for work to be performed.
5. Restore A to two replicas. The original waiting job must finish on replacement A pods.
6. Check every returned activity's tenant, Temporal namespace, queue, Kubernetes namespace,
   and pod name. Check workflow-task worker identities from Temporal history against recorded
   Kubernetes pod names too.
7. Check that B's Kubernetes identity cannot read or list A's Secrets, create A pods or
   Deployments, or enter A pods.
8. Launch the access probe in B with B's worker key. In Cloud mode, require explicit denial of
   A workflow polling, A activity polling, and A history access. First prove access to B works.

```text
Evidence in test-evidence/<run>/
  *-start.json         requested workflow identifiers and routes
  *-result.json        activity locations and retry attempts
  *-workers.json       workflow-task worker identities from history
  *-pods.json          observed Kubernetes pod names and namespaces
  a-while-stopped.json A remained running while A had no workers
  *.log               worker logs and access-probe result
```

The forced pod deletion is for a healthy disposable minikube node. Force-deleting a pod in a
partitioned production cluster does not prove its old process has stopped. Protect external
effects against duplicate execution in all cases.
[Forced pod termination](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination-forced).

## Interpreting the result

| Result | Meaning |
| --- | --- |
| Routing and recovery checks pass | The tested workers and configuration kept both task kinds on the intended tenant route through these failures. |
| `SECURITY_DENIAL_PASSED` | B could access B, while its A polling and history calls received explicit access rejection. |
| `LOCAL_SERVER_ACCEPTED_CROSS_TENANT_ACCESS` | The local service accepted B's request for A history. Local Temporal authorization is absent. |
| A permission probe times out | Inconclusive access control; the test fails. An empty queue can make an authorized poll wait. |
| A probe reports missing history | The setup or identifier is wrong; the test fails. Missing data is not access denial. |

The test can disprove isolation for the exercised routes if it observes cross-tenant execution
or unauthorized access. Passing this finite exercise does not prove resistance to all possible
attacks. Pod metadata and worker identity are checked together, but malicious worker reports
still require independent operational controls.

Worker outage and service outage are separate tests. This project tests worker outages. The
local development server is ephemeral, and no test kills it. Durable service failover, storage
recovery, long outages beyond activity time limits, workflow-code version changes, and large
tenant counts need separate tests in a suitable environment.

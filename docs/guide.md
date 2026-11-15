# Follow Tenant A's work

```text
User Alice -> Spring application -> Temporal namespace tenant-a
                                         |
                                 queue tenant-a-work
                                         ^
                                         | polled by
                                 Kubernetes tenant-a
                                 Spring Boot workers

User Bob   -> Spring application -> Temporal namespace tenant-b
                                         |
                                 queue tenant-b-work
                                         ^
                                         | polled by
                                 Kubernetes tenant-b
                                 Spring Boot workers
```

This is the local naming scheme. In Temporal Cloud, the full namespace includes your account
identifier, for example `tenant-a.acme123`. Kubernetes still calls its namespace `tenant-a`.
Both Temporal namespaces belong to the same shared Temporal service.

## 1. What each namespace separates

A tenant is one customer whose work must stay separate from another customer's work.
A Kubernetes cluster is a group of machines managed together. A node is one of those machines.
A pod is Kubernetes' running unit for containers. A Deployment maintains the requested number
of pods and replaces them when needed.
A Secret is a Kubernetes object for credentials or other confidential values. A Service gives
pods a stable network address.

A Kubernetes namespace groups named resources. Both tenants can have a Deployment named
`temporal-worker` because each Deployment is in a different namespace. Secrets, Services, and
pods are also namespaced. Nodes and some administrative resources belong to the whole cluster.
A namespace alone does not prevent network traffic, reserve a machine, or forbid users from
reading another namespace. Add permissions, network rules, and resource limits.
[Kubernetes namespaces](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/),
[multi-tenancy](https://kubernetes.io/docs/concepts/security/multi-tenancy/).

A workflow is your Java code for coordinating a job. A workflow execution is one running or
completed instance of that code. Temporal records its progress as an event history. An activity
is a piece of work called by a workflow, such as charging a card or calling another service.
Workers are your application processes that run workflow and activity code.
[Workflow executions](https://docs.temporal.io/workflow-execution),
[activities](https://docs.temporal.io/activity-definition).

Authentication checks a caller's identity; authorization checks its permissions. A credential,
such as a secret key, lets the caller prove its identity.

A Temporal namespace groups workflow executions, task queues, and namespace settings such as
history retention. A workflow identifier belongs to that Temporal namespace. Authorization
can restrict a credential to a namespace when the service is configured to enforce it.
[Temporal namespaces](https://docs.temporal.io/namespaces).

```text
Kubernetes namespace                     Temporal namespace
pods, Secrets, Deployments               workflows, history, queues
        |                                         |
        +----- configuration connects these ------+
                   no automatic relationship
```

Naming both `tenant-a` makes configuration easier to inspect. Temporal does not discover that
name from Kubernetes or check where a pod lives. Our manifests pass the Temporal namespace
explicitly. Our security design separately restricts who can use it.

## 2. How work reaches a worker

A task is a request from Temporal for a worker to do something. A workflow task asks the worker
to run workflow code until it decides what should happen next. An activity task asks it to run
an activity. A task queue is the named place workers ask Temporal for those tasks. Polling means
asking for work and waiting for a response. Queues are created on demand; this project has no
queue-creation command. Workflow and activity queues are separate task kinds even when their
names match. [Tasks](https://docs.temporal.io/tasks), [task queues](https://docs.temporal.io/task-queue).

For Alice's request:

```text
Alice                   Spring application         Temporal             A worker
  | login + POST /jobs         |                      |                    |
  |-------------------------->|                      |                    |
  |                    authenticate Alice            |                    |
  |                    resolve tenant-a              |                    |
  |                    select A client               |                    |
  |                    namespace=tenant-a            |                    |
  |                    queue=tenant-a-work           |                    |
  |                            |--- start workflow -->|                    |
  |<---- accepted + job id -----|                      |<-- poll workflow --|
  |                                                   |--- workflow task ->|
  |                                                   |<-- schedule step --|
  |                                                   |<-- poll activity --|
  |                                                   |--- activity task ->|
  |                                                   |<-- activity result-|
  |                                                   |                    |
  |                          another workflow task advances the workflow   |
```

The application chooses the Temporal namespace through `WorkflowClientOptions`. It chooses
the workflow queue through `WorkflowOptions`. The workflow chooses the activity queue through
`ActivityOptions`. The worker's client selects its namespace; `newWorker(queue)` selects the
queue it polls. Both ends must agree. The service sends tasks over the worker's existing
polling connection; it does not connect to a Kubernetes Service for that worker.
[Java client](https://docs.temporal.io/develop/java/client/temporal-client),
[Java workers](https://docs.temporal.io/develop/java/core-application).

In this project, both task kinds use `tenant-a-work` for A and `tenant-b-work` for B. Activities
explicitly copy the workflow's queue. The workflow cannot choose another Temporal namespace
through activity options. An activity could create another client in its own code, so its
credential must also prevent unauthorized access.

The task routing address is the Temporal namespace, task-queue name, and task kind. A queue
name by itself does not identify a tenant globally. Workflow identifiers are helpful for
auditing, but their spelling does not enforce tenant isolation.

## 3. Three different meanings of affinity

| Mechanism | Meaning | What it guarantees |
| --- | --- | --- |
| Tenant-specific routing | A's tasks use A's namespace and queues; A's workers poll those queues. | Correctly configured workers receive only their assigned routes. Server permissions must reject unauthorized polling. |
| Temporal sticky execution | Temporal tries to send later workflow tasks to the same worker because it has workflow state cached in memory. | A performance preference with fallback. It gives no lasting ownership of a workflow to a pod. |
| Kubernetes node affinity | Rules that choose which nodes may run a pod. | Required rules constrain scheduling; preferred rules express a preference. They do not choose Temporal tasks. |
| Kubernetes pod affinity or anti-affinity | Rules to place pods near, or apart from, other pods. | Placement constraints or preferences, not Temporal access permissions. |

Sticky execution applies to workflow tasks. If the preferred worker disappears or the cached
state is unavailable, another eligible worker can rebuild state from history. This is called
replay. The sticky
queue's schedule-to-start timeout controls fallback; the documented default is five seconds.
This does not redirect work to a different tenant's normal queue.
[Sticky execution](https://docs.temporal.io/sticky-execution).

```text
Normal A queue -> A pod 1 -> cached workflow state
                     X      pod crashes
Normal A queue -> A pod 2 -> replay history -> continue
```

Kubernetes affinity can place A on dedicated nodes or spread A's replicas across nodes.
Required scheduling rules are stronger than preferences, but the usual
`IgnoredDuringExecution` rules do not continuously evict a running pod after labels change.
Dedicated nodes also require protected labels and controls that stop other tenants scheduling
there. The supplied single-node minikube example uses no affinity rules.
[Assigning pods to nodes](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/).

Your requirement needs tenant routing, namespace-specific credentials, and protection of those
credentials. Sticky execution helps performance. Affinity may help machine placement or
availability once you have multiple nodes.

## 4. Compare the two designs

```text
Shared Temporal namespace                Separate Temporal namespaces
+---------------------------+            +------------+ +------------+
| namespace shared          |            | tenant-a   | | tenant-b   |
| A queue       B queue     |            | A queue    | | B queue    |
+----^-------------^--------+            +-----^------+ +-----^------+
     |             |                          |              |
  A workers     B workers                  A workers      B workers
```

| Concern | One namespace, tenant queues | Namespace per tenant |
| --- | --- | --- |
| Routing | Separate queue names route both task kinds. | Separate namespace and queue settings route both task kinds. |
| Security | Standard namespace-wide permissions cover both queues. Queue naming is a convention. | Namespace permissions can deny B access to A, including queues and history. |
| Credentials | Different keys improve rotation and auditing, but keys with the same namespace permission still have the same reach. | Each worker key belongs only to its own namespace. |
| Operations | Fewer namespace settings and connections to manage. Still maintain per-tenant worker deployments. | More namespaces, accounts, connections, monitoring, retention settings, and onboarding work. |
| Worker scaling | Increase A replicas independently; B has its own queue and workers. | Also scale independently; namespace capacity can be managed separately. |
| Busy tenant | Separate workers prevent A consuming B's worker slots. Namespace-wide limits and shared server capacity can still affect B. | Cloud provides per-namespace limits and capacity controls. Shared infrastructure, account dependencies, and the shared application still need capacity planning. |
| Visibility | Workflows share a namespace; application filters alone are insufficient access restrictions. | Credentials can separate access to each namespace's history and visibility. |

For two tenants with this security requirement, start with one Temporal namespace per tenant.
This is a recommendation for your constraints. A shared queue with fairness can suit other
applications, but it allows shared workers and does not meet your dedicated-worker requirement.
[Temporal multi-tenant patterns](https://docs.temporal.io/best-practices/multi-tenant-patterns),
[Cloud security](https://docs.temporal.io/evaluate/cloud/security).

Temporal Cloud's built-in `Write` permission permits both starting workflows and running
workers throughout the assigned namespace. It is not a queue-specific permission. Current
custom-role documentation describes narrower actions on resources such as namespaces and
marks the feature pre-release; it does not make our queue names access restrictions.
[Roles and permissions](https://docs.temporal.io/cloud/manage-access/roles-and-permissions),
[custom roles](https://docs.temporal.io/cloud/manage-access/custom-roles).

On a self-hosted service, separate namespaces need an authorization policy. The default
authorizer allows requests. A custom policy is possible, but enforcing queue-specific access
must cover all operations, including history reads and task completion, not only polling.
The supplied project uses namespace separation. [Self-hosted security](self-hosted.md).

## 5. Concrete configuration

Assumptions: you control the Spring application and deployment configuration; cluster and
Temporal administrators are trusted; workers use compatible workflow code; tenants cannot
create arbitrary pods in each other's namespaces. The secured example uses Temporal Cloud
credentials. Local mode deliberately removes Temporal authorization for a quick learning run.

```text
Kubernetes cluster                         Shared Temporal Cloud service
+--------------------------------+         +---------------------------------+
| application                    |         | tenant-a.acme123                |
| Spring Boot deployment         |-------->| tenant-a-work                   |
| A client + B client            |         | workflow tasks + activity tasks |
| separate A/B application keys  |---+     +----------------^----------------+
+--------------------------------+   |                      |
                                     |                  polls with A key
+--------------------------------+   |                      |
| tenant-a                       |--------------------------+
| temporal-worker deployment x2  |   |
| A worker key only              |   |
+--------------------------------+   |     +---------------------------------+
                                     +---->| tenant-b.acme123                |
+--------------------------------+         | tenant-b-work                   |
| tenant-b                       |-------->| workflow tasks + activity tasks |
| temporal-worker deployment x2  | polls   +---------------------------------+
| B worker key only              | with B key
+--------------------------------+
```

`acme123` is an example account identifier. Use the complete namespace and endpoint shown by
your Cloud account. In local mode, drop the `.acme123` suffix and use the shared development
server address `temporal.temporal-system.svc.cluster.local:7233`.

The authenticated identity determines the route:

```text
verified Alice login -> server authority tenant:tenant-a -> fixed A client and A queue
verified Bob login   -> server authority tenant:tenant-b -> fixed B client and B queue
request X-Tenant-Id  -> ignored
request JSON        -> jobId; routing fields are never consulted
```

This example has two password-authenticated demonstration users. A production login system
should verify its signed login token, its issuer, intended recipient, and tenant membership
before constructing the same trusted authority. If a user belongs to several tenants, validate
their selected tenant against that membership. Do not concatenate a request header into a
queue name.

The Spring configuration uses ordinary beans and the Temporal Java library, so the choices
are visible. The official Spring Boot integration also exists; this project does not combine
automatic worker registration with these explicit beans.
[Spring Boot integration](https://docs.temporal.io/develop/java/integrations/spring-boot-integration).

Illustrative extracts, matching the complete source:

```java
// AppConfiguration: a client is fixed to one Temporal namespace.
WorkflowClient a = WorkflowClient.newInstance(aConnection,
    WorkflowClientOptions.newBuilder().setNamespace("tenant-a.acme123").build());

// JobService: the server's trusted tenant mapping chooses this queue.
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setWorkflowId("tenant-a/job/example-1")
    .setTaskQueue("tenant-a-work")
    .build();

// JobWorkflowImpl: both task kinds stay on the same tenant route.
ActivityOptions activityOptions = ActivityOptions.newBuilder()
    .setTaskQueue(Workflow.getInfo().getTaskQueue())
    .setStartToCloseTimeout(Duration.ofSeconds(30))
    .setHeartbeatTimeout(Duration.ofSeconds(5))
    .setDisableEagerExecution(true)
    .build();

// WorkerConfiguration: this worker polls only this client's namespace and queue.
WorkerFactory factory = WorkerFactory.newInstance(a);
Worker worker = factory.newWorker("tenant-a-work");
worker.registerWorkflowImplementationTypes(JobWorkflowImpl.class);
worker.registerActivitiesImplementations(activities);
factory.start();
```

Cloud connections use `setEnableHttps(true)` and `addApiKey(() -> key)` on
`WorkflowServiceStubsOptions`. The key identifies the caller; the namespace option selects
the requested namespace. Both must be correct. Local mode refuses a nonempty key so it cannot
send Cloud credentials over its unencrypted connection.
[Java connection configuration](https://docs.temporal.io/develop/java/client/temporal-client).

The worker Deployment passes the matching settings:

```yaml
metadata:
  name: temporal-worker
  namespace: tenant-a
# Inside the worker container's env list:
env:
  - name: TENANT_ID
    value: tenant-a
  - name: TEMPORAL_NAMESPACE
    value: tenant-a.acme123
  - name: TEMPORAL_TASK_QUEUE
    value: tenant-a-work
  - name: TEMPORAL_TARGET
    value: tenant-a.acme123.tmprl.cloud:7233
  - name: TEMPORAL_API_KEY
    valueFrom:
      secretKeyRef:
        name: temporal-worker-credentials
        key: api-key
  - name: POD_NAMESPACE
    valueFrom:
      fieldRef:
        fieldPath: metadata.namespace
```

That is an extract, not a complete Deployment. `k8s/worker.yaml.tpl` is the complete template.
The scripts render it once for A and once for B. Kubernetes supplies pod name and namespace
from pod metadata, which the worker checks and records.
[Pod metadata in environment variables](https://kubernetes.io/docs/tasks/inject-data-application/environment-variable-expose-pod-information/).

## 6. Crashes, replacement, and isolation

Replay means rerunning workflow code against its recorded events to reconstruct where it had
reached. Completed activity results come from history. Replay does not call those completed
activities again. Workflow code must be deterministic: the same recorded events must lead to
the same commands. Use Temporal's timers and workflow methods instead of ordinary sleeping,
network calls, or random values in workflow code.
[Workflow definitions](https://docs.temporal.io/workflow-definition),
[event history](https://docs.temporal.io/workflow-execution/event).

| Event | Expected behavior |
| --- | --- |
| One A worker crashes during a workflow task | A task is retried; another eligible A worker replays history and continues. Sticky state is expendable. |
| A worker crashes during an activity | Temporal detects missed heartbeats or an attempt timeout and retries according to the activity policy. |
| An activity finishes its external effect but its completion is lost | The activity may run again. Use a stable operation identifier so the external system can reject duplicate effects. |
| Every A worker stops | A's work waits. B's correctly configured workers keep polling B. Timeouts and retry limits still apply. |
| Replacement A workers start | They poll the same namespace and queue, register the same types, replay compatible workflow history, and continue. No pod-local workflow backup is required. |

A heartbeat is an activity's periodic report that it is still working. This example sends one
each second during its simulated ten-second activity. It allows five seconds without a
heartbeat, thirty seconds per attempt, at most twenty attempts, and ten minutes overall for
each activity. A longer outage can therefore fail an activity. Recovery is governed by the
configured limits, rather than a promise to retry forever.
[Activity failure detection](https://docs.temporal.io/encyclopedia/detecting-activity-failures).

Any compatible, authorized A worker can continue. Compatibility includes understanding the
workflow's recorded history and registering the required workflow/activity types. The example
does not use worker versioning. Real deployments need a code compatibility strategy before
changing long-running workflows.

The included activities only log and wait; there is no payment or database write. Disabling
eager activity execution makes the ordinary activity-polling path visible. No local activities
are used. Local activities would run in the workflow worker process and need separate
consideration when designing worker placement.

A wrongly configured worker is stopped by our startup checks. A malicious worker can remove
those checks. The enforceable restriction is its credential: Temporal must deny B's credential
in A's namespace. Kubernetes must stop B reading A's Secret or creating a pod that mounts it.
A stolen A credential can be used outside A's Kubernetes namespace unless additional controls
prevent that. Temporal credentials alone do not prove physical pod location.
[Isolation controls](security.md).

## 7. Prove the behavior

```text
Start A and B -> kill busy A pod -> A retries on another A pod
                      |                     |
                      +----- B continues ---+

Stop all A -> start A and B jobs -> B completes; A remains waiting
                                      |
Restart A ----------------------------+-> original A job completes

B credential -> A workflow poll  -> denied
             -> A activity poll  -> denied
             -> A history read   -> denied
```

Run `scripts/test-minikube.sh` after deploying the chosen mode. It checks workflow-task worker
identities in Temporal history, activity location records, and Kubernetes pod records. The
access probe first proves B's credential works for B; connection failure or timeout against A
does not count as a security pass. Local mode instead confirms the expected lack of Temporal
authorization. See [test steps and evidence](testing.md) and the [Windows/Linux setup](wsl-minikube.md).

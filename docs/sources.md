# Official sources and version choices

Documentation checked on 15 November 2026. The explanations in this archive are original;
links below lead to the official documentation. No full copies of third-party manuals are bundled.
Recommendations about this project's design are identified as recommendations in the guide.

## Temporal

| Subject | Official reference |
| --- | --- |
| Namespace scope | [Namespaces](https://docs.temporal.io/namespaces) |
| Task routing and polling | [Task queues](https://docs.temporal.io/task-queue), [tasks](https://docs.temporal.io/tasks) |
| Workflow execution and history | [Workflow execution](https://docs.temporal.io/workflow-execution), [events](https://docs.temporal.io/workflow-execution/event) |
| Replay and code requirements | [Workflow definition](https://docs.temporal.io/workflow-definition) |
| Activity retries and effects | [Activity definition](https://docs.temporal.io/activity-definition) |
| Heartbeats and timeouts | [Detecting activity failures](https://docs.temporal.io/encyclopedia/detecting-activity-failures) |
| Sticky workflow tasks | [Sticky execution](https://docs.temporal.io/sticky-execution) |
| Tenant architecture | [Multi-tenant patterns](https://docs.temporal.io/best-practices/multi-tenant-patterns) |
| Worker capacity | [Worker deployment](https://docs.temporal.io/best-practices/worker) |
| Java connections | [Temporal client](https://docs.temporal.io/develop/java/client/temporal-client) |
| Java worker registration | [Core application](https://docs.temporal.io/develop/java/core-application) |
| Spring integration | [Spring Boot integration](https://docs.temporal.io/develop/java/integrations/spring-boot-integration) |
| Cloud identities | [Service Accounts](https://docs.temporal.io/cloud/manage-access/service-accounts) |
| Cloud grants | [Roles and permissions](https://docs.temporal.io/cloud/manage-access/roles-and-permissions) |
| Current custom-role scope | [Custom roles](https://docs.temporal.io/cloud/manage-access/custom-roles) |
| Cloud permission details | [Permissions reference](https://docs.temporal.io/cloud/manage-access/permissions-reference) |
| Cloud credential configuration | [Keys](https://docs.temporal.io/cloud/api-keys) |
| Cloud isolation | [Security](https://docs.temporal.io/evaluate/cloud/security), [access control](https://docs.temporal.io/best-practices/cloud-access-control) |
| Self-hosted authorization | [Security guide](https://docs.temporal.io/self-hosted-guide/security) |
| Self-hosted limits | [Service configuration](https://docs.temporal.io/references/service-configuration) |
| Development server flags | [Server command reference](https://docs.temporal.io/cli/command-reference/server) |

## Kubernetes, Windows/Linux, and minikube

| Subject | Official reference |
| --- | --- |
| Namespace scope | [Namespaces](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/) |
| Tenant security | [Multi-tenancy](https://kubernetes.io/docs/concepts/security/multi-tenancy/) |
| Permissions and indirect Secret access | [Permission practices](https://kubernetes.io/docs/concepts/security/rbac-good-practices/) |
| Secret handling | [Secret practices](https://kubernetes.io/docs/concepts/security/secrets-good-practices/) |
| Container restrictions | [Pod security standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/) |
| Network controls | [Network policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/) |
| Affinity and placement | [Assigning pods to nodes](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/) |
| Worker location metadata | [Pod environment](https://kubernetes.io/docs/tasks/inject-data-application/environment-variable-expose-pod-information/) |
| Forced termination limits | [Pod lifecycle](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/) |
| Windows/Linux setup | [Minikube on Windows Subsystem for Linux 2](https://minikube.sigs.k8s.io/docs/tutorials/wsl_docker_driver/) |
| Docker driver | [Docker driver](https://minikube.sigs.k8s.io/docs/drivers/docker/) |
| Build images inside minikube | [Pushing images](https://minikube.sigs.k8s.io/docs/handbook/pushing/) |
| Enforce network policies locally | [Minikube network policies](https://minikube.sigs.k8s.io/docs/handbook/network_policy/) |
| Linux filesystem location | [Microsoft environment setup](https://learn.microsoft.com/en-us/windows/wsl/setup/environment) |

## Pinned project versions

| Component | Version | Verification source |
| --- | --- | --- |
| Java | 21; delivery compiled with Temurin 21.0.13+9 | [Temurin 21 releases](https://github.com/adoptium/temurin21-binaries/releases) |
| Spring Boot | 4.1.2 | [Official release](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/) |
| Temporal Java | 1.40.0 | [Official releases](https://github.com/temporalio/sdk-java/releases) |
| Temporal development-server command tool | 1.10.1 | [Official releases](https://github.com/temporalio/cli/releases) |
| minikube | 1.39.0 | [Official releases](https://github.com/kubernetes/minikube/releases) |
| Kubernetes | 1.37.2 | [Stable release marker](https://dl.k8s.io/release/stable.txt) |
| Maven used for verification | 3.9.9 | [Maven release history](https://maven.apache.org/docs/history.html) |
| Maven Enforcer plugin | 3.6.2 | Pinned in `pom.xml`; fetched and executed in verification |

Java 21 is the user's required version. Spring Boot and Temporal versions were selected from
stable releases available at the check date. The Spring Boot parent fixes its managed
transitive dependencies and build plugins. Direct project dependencies have explicit versions;
the security-test version resolves from the pinned parent. No floating snapshots are used.

The Java runtime and Temporal server images include exact manifest digests in `Dockerfile`
and `k8s/local-temporal.yaml`. Those image manifests were checked for Linux amd64 and arm64
variants. Kubernetes cluster images and Calico components are selected by the pinned minikube
and Kubernetes versions rather than separately locked by digest in this project. Revisit
dependency and image patches before using the example as a real application.

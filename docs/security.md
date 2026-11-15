# What keeps the tenants separate

```text
verified login -> fixed tenant route -> Temporal namespace permission
                                               ^
                                               |
pod placement -> tenant's private Secret -> tenant's credential
```

Routing decides where work is offered. Authorization decides whether a caller may request it.
Authentication proves which caller is asking. All three are needed for this design.

## Controls in the example

| Control | Purpose | Limit |
| --- | --- | --- |
| Server-side tenant mapping | Ignore a forged tenant header and select a configured route. | A compromised application has both application credentials. |
| Route and pod-namespace checks | Stop accidental deployment with A settings in B. | Malicious code can remove checks or lie about its identity. |
| Namespace-scoped Cloud credentials | Reject B's calls to A's namespace. | A stolen A credential can be used wherever the endpoint is reachable. |
| Secret in each Kubernetes namespace | Mount only that tenant's worker key. | Anyone allowed to create pods in A may be able to mount A's Secrets. |
| No worker permission bindings; no mounted Kubernetes token | Workers have no granted Kubernetes management powers. | Cluster administrators and host administrators remain trusted. |
| Restricted pod policy | Reject privileged workloads and enforce selected container restrictions. | A shared kernel and shared cluster still have security risks. |
| NetworkPolicy | Restrict traffic paths to required connections. | It cannot distinguish two Temporal namespaces served by the same endpoint. |
| ResourceQuota and container limits | Bound each tenant's requested resources and container usage. | Single-node contention and shared Temporal/application capacity remain. |

Kubernetes permissions are called role-based access control. They decide which identities may
read or change resources. This project grants its worker Service Accounts no extra roles and
sets `automountServiceAccountToken: false`. That disables the usual automatic Kubernetes
credential mount; it does not affect the separately mounted Temporal credential.
[Permission practices](https://kubernetes.io/docs/concepts/security/rbac-good-practices/).

Do not give Tenant B permission to get, list, or watch A's Secrets. Also deny creation or
modification of A's pods, Deployments, and other workload controllers, entry into A's running
containers, and changes to permission bindings. Pod creation can grant indirect access to a
Secret even when direct Secret reads are forbidden. Namespace labels used by network policies
must be protected too.
[Workload creation risks](https://kubernetes.io/docs/concepts/security/rbac-good-practices/#workload-creation).

The deployments run as a non-root user, use a read-only container filesystem, drop extra
Linux capabilities, and disable privilege escalation. The application and workers have a
writable temporary directory. Kubernetes Secrets require proper administrative protection and,
for a real installation, encryption at rest and a reviewed secret-delivery process. Encoding
a Secret value is not encryption.
[Kubernetes Secret practices](https://kubernetes.io/docs/concepts/security/secrets-good-practices/),
[pod security standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/).

Network policies only work with a network component that enforces them. The scripts choose
Calico. Policies are additive: another broad allow policy can weaken your intended restrictions.
The application has no public ingress; the exercise reaches it through authenticated local
port forwarding. Public Cloud traffic is restricted to port 7233, but its destination rule
covers public addresses rather than a particular domain name.
[NetworkPolicy behavior](https://kubernetes.io/docs/concepts/services-networking/network-policies/).

## A compromised worker

If B's worker is compromised, assume its B credential is stolen. Proper namespace permissions
still deny requests to A. B's own work can be damaged, and a worker might falsely report its
own execution location. Worker identity strings and returned location records are audit clues;
they are not cryptographic proof of physical placement.

For A's work to run only inside Kubernetes `tenant-a`, control who can obtain A credentials,
who can deploy A workloads, which images run there, and which machines can reach Temporal.
Protect deployment administration as well as worker runtime identities. If hostile tenants
control their own hosts or require stronger machine isolation, consider separate clusters or
other stronger execution boundaries. Namespace names cannot supply that guarantee.
[Kubernetes tenant isolation](https://kubernetes.io/docs/concepts/security/multi-tenancy/),
[Cloud access controls](https://docs.temporal.io/best-practices/cloud-access-control).

Rotate each tenant's keys independently and verify revocation at the service. Do not put
administrator credentials, the other tenant's keys, or the application's two keys into worker
pods. The shared application is trusted across both tenants, so protect and audit it accordingly.

## What the supplied check can establish

The test checks representative Kubernetes permissions using administrator impersonation of
B's Service Account. It also sends real Temporal requests from a pod holding only B's key.
It catches incorrect namespace grants and obvious credential mixups. It does not test every
possible Kubernetes permission, container escape, stolen administrator credential, or host
attack. The local unauthenticated Temporal server intentionally accepts cross-tenant calls.

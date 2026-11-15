# Using a self-hosted Temporal service

A self-hosted service is Temporal operated by your own team. The same namespace-and-queue
routing works, but you are responsible for service authentication, authorization, encryption,
availability, and capacity.

The development server included here is intentionally unauthenticated. Its two namespaces
organize work without restricting who may access it. Adding a namespace or inventing a
different queue name does not change that.

Temporal's self-hosted security configuration separates two jobs:

* A `ClaimMapper` reads a validated identity and its permissions from the request's credentials.
* An `Authorizer` decides whether those permissions allow the requested operation.

The default no-op authorizer permits calls. Configure actual verification and authorization
before treating namespaces as security boundaries. The standard authorization model includes
namespace roles such as reader, writer, worker, and administrator. A namespace-scoped worker
role can distinguish worker operations from starting workflows more narrowly than the Cloud
`Write` role used in this example.
[Temporal self-hosted security](https://docs.temporal.io/self-hosted-guide/security).

```text
caller credential -> verified identity -> namespace permissions -> operation allowed or denied
```

Encrypted connections protect data in transit. Client-certificate authentication can identify
a caller, but its certificate still needs an authorization policy. Network rules that allow
both tenants to reach the same Temporal address cannot inspect the namespace requested inside
that encrypted connection.

Separate self-hosted namespaces still share whatever server processes, storage capacity, and
networking you assign them. Apply appropriate per-namespace request limits and measure the
shared service under load. Kubernetes worker quotas alone do not constrain service-side usage.
[Temporal service configuration](https://docs.temporal.io/references/service-configuration).

This archive does not include a production self-hosted service, an identity issuer, or a custom
authorizer. Its secured connection code uses Cloud keys and public server certificates. For a
self-hosted installation, adapt `Connections.java` to the credentials and trusted certificates
provided by your platform team, then repeat the same positive B-access and negative A-access
tests. The exact credential format must come from that service's configuration.

If you keep tenants in one Temporal namespace, a custom queue-aware authorization design must
also address operations without a queue name, such as reading workflow history and completing
tasks using task tokens. A queue-prefix check on polling alone does not establish tenant data
isolation. Separate namespaces avoid that additional policy design in this starting example.

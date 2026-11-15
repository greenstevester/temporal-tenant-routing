# Test access restrictions with Temporal Cloud

The workers and application run inside minikube. Temporal runs in your Cloud account.
This exercise needs two disposable Temporal namespaces and four separate machine identities.
A machine identity is an account used by a program, called a Service Account in Temporal Cloud.

Use full namespace names such as `tenant-a.acme123` and `tenant-b.acme123`. Replace `acme123`
with your real account identifier. Namespace names and endpoints come from your account;
this project does not create paid Cloud resources or invent credentials.

## Give each process only its required access

Create namespace-scoped Service Accounts with these permissions:

| Identity | Namespace permission | Used in |
| --- | --- | --- |
| `tenant-a-worker` | `Write` on A only | A's worker Secret |
| `tenant-b-worker` | `Write` on B only | B's worker Secret |
| `tenant-a-application` | `Write` on A only | Application Secret |
| `tenant-b-application` | `Write` on B only | Application Secret |

Namespace-scoped accounts have the `Read` account role and one namespace permission. Account
metadata access and workflow-history access are different permissions. Avoid Global Admin,
Account Owner, and extra grants. Use separate accounts for separate trust boundaries; two keys
on one broadly authorized account do not restrict each other.
[Service Accounts](https://docs.temporal.io/cloud/manage-access/service-accounts).

The built-in `Write` permission includes worker polling and task completion as well as workflow
operations. It does not offer a worker-only restriction within that namespace. Granting it only
on A gives this example the required cross-tenant boundary.
[Namespace permissions](https://docs.temporal.io/cloud/manage-access/roles-and-permissions).

Create a key for each account and verify that A's accounts have no access to B and vice versa.
Do not paste keys into source files. New or changed keys may take time to become usable at the
namespace endpoint; check access before interpreting a denied request as evidence of isolation.
[Cloud keys](https://docs.temporal.io/cloud/api-keys).

## Run the secured exercise

Prepare the Linux tools and build the image as described in [the minikube guide](wsl-minikube.md):

```bash
./scripts/start-minikube.sh
./scripts/build-minikube.sh
```

In that Linux terminal, set the real names. The default endpoint is derived from each full
name as shown below; you can instead export the exact `TENANT_A_TARGET` and `TENANT_B_TARGET`
given by your Cloud account.

```bash
export TENANT_A_NAMESPACE=tenant-a.acme123
export TENANT_B_NAMESPACE=tenant-b.acme123

read -r -s -p 'A application key: ' TENANT_A_KEY; printf '\n'
read -r -s -p 'B application key: ' TENANT_B_KEY; printf '\n'
read -r -s -p 'A worker key: ' TENANT_A_WORKER_KEY; printf '\n'
read -r -s -p 'B worker key: ' TENANT_B_WORKER_KEY; printf '\n'
export TENANT_A_KEY TENANT_B_KEY TENANT_A_WORKER_KEY TENANT_B_WORKER_KEY

./scripts/deploy-minikube.sh cloud
./scripts/test-minikube.sh cloud
```

These commands use the existing disposable Cloud namespaces. They do not copy local workflow
history into Cloud. Workflow jobs get unique identifiers on each test run.

Connections are encrypted and check the server certificate. Each worker receives only its own
key. The application receives its two separate keys because it starts work for both tenants.
Deployment restarts the pods so that updated Secret values take effect in their environments.

The final test message must say:

```text
Routing, recovery and Temporal permission denial passed.
```

The probe runs in Kubernetes `tenant-b`, mounts B's worker Secret, and first accesses B. It then
requests A's workflow tasks, A's activity tasks, and A's history. All three A calls must receive
an explicit authentication or permission rejection. A timeout, missing workflow, broken name
lookup, or network error fails the test. It never mounts A's Secret.

This configuration assumes public Cloud endpoints reachable on port 7233. The demonstration
network policy permits public IPv4 destinations on that port and blocks private address ranges.
That is broader than allowing only Temporal. Private endpoints, company proxies, and IPv6-only
networks need reviewed network rules; those variants are not included.

After testing, unset credentials from your shell and revoke the disposable keys when no longer
needed. Key values also remain in `.local/` and Kubernetes Secrets until you remove them.

```bash
unset TENANT_A_KEY TENANT_B_KEY TENANT_A_WORKER_KEY TENANT_B_WORKER_KEY
```

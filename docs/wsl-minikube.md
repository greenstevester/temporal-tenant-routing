# Run with minikube on Windows Subsystem for Linux 2

Run the project commands inside your Linux terminal. The scripts use Bash, Linux paths, and
Linux container images. Windows Subsystem for Linux 2 runs that Linux environment on Windows.

## Prepare the tools

Use a Linux distribution such as Ubuntu with either Docker Engine installed inside it or
Docker Desktop's integration enabled for that distribution. Both are supported by minikube.
Confirm `docker info` works as your regular Linux user before starting minikube.
[Official Windows/Linux setup](https://minikube.sigs.k8s.io/docs/tutorials/wsl_docker_driver/).

Install these tools in the Linux environment:

| Tool | Project baseline | Check |
| --- | --- | --- |
| Java development kit | Temurin 21.0.13+9; Java major version 21 is required | `java -version` |
| Maven | 3.9.9 or later; delivery tested with 3.9.9 | `mvn -version` |
| minikube | 1.39.0 | `minikube version` |
| kubectl | 1.37.2 | `kubectl version --client` |
| Docker | Working Linux container engine | `docker info` |
| Shell utilities | Bash, curl, jq, openssl, shasum, unzip | `command -v bash curl jq openssl shasum unzip` |
| ShellCheck, optional | Checks scripts for common mistakes | `shellcheck --version` |

Use the official [Temurin downloads](https://adoptium.net/temurin/releases/?version=21),
[Maven downloads](https://maven.apache.org/download.cgi),
[minikube releases](https://github.com/kubernetes/minikube/releases), and
[kubectl installation guide](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/).
For a reproducible first run, choose the pinned tool versions above. Package-manager Java
installations must still resolve to Java 21. Maven prints the Java installation it uses; check
that line as well as `java -version`. No Maven wrapper or tool installer is bundled.

The start script requests four processor cores and six gigabytes of memory for minikube.
Leave memory for Windows, Docker, and Maven too. Use a machine with roughly twelve gigabytes
available to this work. Downloading dependencies and images requires network access.

Keep the extracted project under your Linux home directory, for example
`~/projects/temporal-tenant-routing`. Working under `/mnt/c` can make Linux builds slower.
[Microsoft's filesystem guidance](https://learn.microsoft.com/en-us/windows/wsl/setup/environment).

If your extraction tool removed executable permissions, restore them:

```bash
chmod +x scripts/*.sh
```

## Start the local demonstration

From the project directory:

```bash
./scripts/start-minikube.sh
./scripts/build-minikube.sh
./scripts/deploy-minikube.sh local
./scripts/test-minikube.sh local
```

The start script creates profile `temporal-tenants` with Kubernetes 1.37.2, the Docker driver,
the containerd container runtime, and Calico networking. A container runtime starts containers.
Calico is the networking component that enforces the supplied NetworkPolicy rules. Minikube's
default networking does not necessarily enforce those rules, so use this profile from the
start. [Minikube network policies](https://minikube.sigs.k8s.io/docs/handbook/network_policy/).

The build script runs `mvn verify`, then `minikube image build` inside this profile. It gives
the image a tag derived from the packaged Java file's checksum. Deployments use
`imagePullPolicy: Never`, so Kubernetes uses that exact local image. You do not need a registry,
Docker login, or the `minikube docker-env` setup.
[Minikube image building](https://minikube.sigs.k8s.io/docs/handbook/pushing/).

The deployment creates `application`, `tenant-a`, `tenant-b`, and `temporal-system` namespaces.
The last contains the Temporal development server. It has no authorization and keeps state
only in memory. Killing workers preserves progress; killing that server loses the local test
history. This is a worker-recovery exercise, not a durable service installation.
[Temporal development server](https://docs.temporal.io/cli/command-reference/server).

The test normally takes several minutes. Its last local-mode line must say:

```text
Routing and recovery passed. Local Temporal authorization is NOT enforced.
```

It also reports the directory containing its evidence. Seeing cross-tenant access accepted
in local mode is an expected result. The [Cloud exercise](cloud.md) requires that access to
be rejected.

## Inspect a running demonstration

List the two worker deployments:

```bash
kubectl --context temporal-tenants -n tenant-a get pods
kubectl --context temporal-tenants -n tenant-b get pods
kubectl --context temporal-tenants -n tenant-a logs deployment/temporal-worker --tail=40
```

Open the local Temporal browser interface by leaving this command running:

```bash
kubectl --context temporal-tenants -n temporal-system port-forward \
  --address 127.0.0.1 service/temporal 8233:8233
```

Use `http://localhost:8233` in your browser. If Windows localhost forwarding is disabled in
your environment, inspect it from inside the Linux environment or enable that forwarding.
Do not rely on the address from `minikube ip` being reachable from Windows.
[Windows/Linux minikube networking](https://minikube.sigs.k8s.io/docs/tutorials/wsl_docker_driver/).

To call the application manually, first stop any test using port 18080, then leave this running:

```bash
kubectl --context temporal-tenants -n application port-forward \
  --address 127.0.0.1 service/tenant-application 18080:8080
```

In another Linux terminal, from the same project directory:

```bash
umask 077
mkdir -p .local/manual
printf 'user = "alice:%s"\n' "$(cat .local/alice-password)" > .local/manual/alice.curl
curl -fsS --config .local/manual/alice.curl --cookie-jar .local/manual/cookies \
  http://127.0.0.1:18080/csrf > .local/manual/token.json
token=$(jq -r .token .local/manual/token.json)
header=$(jq -r .headerName .local/manual/token.json)
curl -fsS --config .local/manual/alice.curl --cookie .local/manual/cookies \
  -H "$header: $token" -H 'Content-Type: application/json' \
  --data '{"jobId":"manual-1"}' http://127.0.0.1:18080/jobs
curl -fsS --config .local/manual/alice.curl http://127.0.0.1:18080/jobs/manual-1/result
```

The first response accepts the job. The result endpoint returns a waiting response until the
job completes, usually after about thirty seconds. The `/csrf` endpoint supplies a browser
request-forgery token: send it with its session cookie when starting a job. Authentication is
still required on every protected request. Use a new job identifier for each new job.

Passwords are generated during deployment. Alice belongs to A and Bob to B. For Bob, use
`.local/bob-password` and username `bob`. Do not expose this password-based demo application
to a public network. Production needs an encrypted front connection and your real login system.

## Troubleshooting and cleanup

| Symptom | Check |
| --- | --- |
| Maven rejects Java | `mvn -version` must show Java 21. Set `JAVA_HOME` to your Linux Java 21 installation. |
| Docker connection fails | Fix Docker Engine access or Docker Desktop integration before starting minikube. |
| `ErrImageNeverPull` | Run the build script for profile `temporal-tenants`, then the deployment script. |
| Pod stays pending | Read `kubectl --context temporal-tenants -n tenant-a describe pod <pod-name>` for resource or policy errors. |
| Cloud worker has permission errors | Check its own namespace, endpoint, credential grant, and key activation delay. |
| Port forwarding fails | Stop another process using 18080. The test manages this port itself. |
| Test stops after a worker failure | Read its `test-evidence/` directory and worker logs before retrying. |
| Network-policy behavior differs | Confirm you created this profile with Calico and have no extra allow policies. |

The test's exit handler attempts to restore A to two replicas even after failure. If interrupted
outside normal shell handling, restore explicitly:

```bash
kubectl --context temporal-tenants -n tenant-a scale deployment temporal-worker --replicas=2
```

When finished, this command deletes the entire disposable minikube profile and its local data:

```bash
minikube delete -p temporal-tenants
```

It does not remove Cloud namespaces or revoke their credentials. Manage those separately in
your Cloud account. Private credential files remain under `.local/` until you remove them.

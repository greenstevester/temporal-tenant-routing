#!/usr/bin/env bash
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/common.sh"
require_tools kubectl minikube openssl shasum
load_mode "${1:-local}"
image_name
minikube -p "$CLUSTER_NAME" status >/dev/null
umask 077
mkdir -p .local/rendered
if [[ ! -f .local/alice-password ]]; then openssl rand -hex 24 > .local/alice-password; fi
if [[ ! -f .local/bob-password ]]; then openssl rand -hex 24 > .local/bob-password; fi
TENANT_A_PASSWORD=$(cat .local/alice-password)
TENANT_B_PASSWORD=$(cat .local/bob-password)
# Values are written only to private local files, never to the rendered manifests or terminal.
printf 'TENANT_A_PASSWORD=%s\nTENANT_B_PASSWORD=%s\nTENANT_A_KEY=%s\nTENANT_B_KEY=%s\n' \
  "$TENANT_A_PASSWORD" "$TENANT_B_PASSWORD" "$TENANT_A_KEY" "$TENANT_B_KEY" > .local/app-secrets.env
printf 'api-key=%s\n' "$TENANT_A_WORKER_KEY" > .local/a-secrets.env
printf 'api-key=%s\n' "$TENANT_B_WORKER_KEY" > .local/b-secrets.env
kube apply -f k8s/namespaces.yaml
kube -n application create secret generic application-credentials --from-env-file=.local/app-secrets.env \
  --dry-run=client -o yaml | kube apply -f -
kube -n tenant-a create secret generic temporal-worker-credentials --from-env-file=.local/a-secrets.env \
  --dry-run=client -o yaml | kube apply -f -
kube -n tenant-b create secret generic temporal-worker-credentials --from-env-file=.local/b-secrets.env \
  --dry-run=client -o yaml | kube apply -f -
./scripts/render-policies.sh "$DEMO_MODE" > .local/rendered/policies.yaml
kube apply -f .local/rendered/policies.yaml
if [[ "$DEMO_MODE" == local ]]; then
  kube apply -f k8s/local-temporal.yaml
  kube -n temporal-system rollout status deployment/temporal --timeout=240s
fi
render_app > .local/rendered/application.yaml
render_worker tenant-a "$TENANT_A_NAMESPACE" "$TENANT_A_TARGET" > .local/rendered/tenant-a.yaml
render_worker tenant-b "$TENANT_B_NAMESPACE" "$TENANT_B_TARGET" > .local/rendered/tenant-b.yaml
kube apply -f .local/rendered/application.yaml -f .local/rendered/tenant-a.yaml -f .local/rendered/tenant-b.yaml
for namespace in tenant-a tenant-b; do
  kube -n "$namespace" rollout restart deployment/temporal-worker
  kube -n "$namespace" rollout status deployment/temporal-worker --timeout=240s
done
kube -n application rollout restart deployment/tenant-application
kube -n application rollout status deployment/tenant-application --timeout=240s
printf '%s\n' "$DEMO_MODE" > .local/deployed-mode
echo "Deployed $DEMO_MODE mode into the disposable $CLUSTER_NAME cluster."

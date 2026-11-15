#!/usr/bin/env bash
# Shared by the explicitly invoked demonstration scripts.
set -euo pipefail
PROJECT_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CLUSTER_NAME=temporal-tenants
cd "$PROJECT_ROOT"

kube() {
  kubectl --context "$CLUSTER_NAME" "$@"
}

require_tools() {
  local tool
  for tool in "$@"; do
    command -v "$tool" >/dev/null || { echo "Required tool missing: $tool" >&2; exit 1; }
  done
}

load_mode() {
  DEMO_MODE=${1:-local}
  if [[ "$DEMO_MODE" == local ]]; then
    LOCAL_TEMPORAL=true
    TENANT_A_NAMESPACE='tenant-a'
    TENANT_B_NAMESPACE='tenant-b'
    TENANT_A_TARGET=temporal.temporal-system.svc.cluster.local:7233
    TENANT_B_TARGET=$TENANT_A_TARGET
    TENANT_A_KEY=''
    TENANT_B_KEY=''
    TENANT_A_WORKER_KEY=''
    TENANT_B_WORKER_KEY=''
  elif [[ "$DEMO_MODE" == cloud ]]; then
    LOCAL_TEMPORAL=false
    : "${TENANT_A_NAMESPACE:?Export TENANT_A_NAMESPACE}"
    : "${TENANT_B_NAMESPACE:?Export TENANT_B_NAMESPACE}"
    : "${TENANT_A_KEY:?Export TENANT_A_KEY for the application}"
    : "${TENANT_B_KEY:?Export TENANT_B_KEY for the application}"
    : "${TENANT_A_WORKER_KEY:?Export TENANT_A_WORKER_KEY}"
    : "${TENANT_B_WORKER_KEY:?Export TENANT_B_WORKER_KEY}"
    TENANT_A_TARGET=${TENANT_A_TARGET:-${TENANT_A_NAMESPACE}.tmprl.cloud:7233}
    TENANT_B_TARGET=${TENANT_B_TARGET:-${TENANT_B_NAMESPACE}.tmprl.cloud:7233}
  else
    echo 'Mode must be local or cloud.' >&2
    exit 1
  fi
  [[ "$TENANT_A_NAMESPACE" =~ ^tenant-a(\.[a-zA-Z0-9-]+)?$ ]]
  [[ "$TENANT_B_NAMESPACE" =~ ^tenant-b(\.[a-zA-Z0-9-]+)?$ ]]
  [[ "$TENANT_A_TARGET" =~ ^[a-zA-Z0-9.-]+:[0-9]+$ ]]
  [[ "$TENANT_B_TARGET" =~ ^[a-zA-Z0-9.-]+:[0-9]+$ ]]
}

image_name() {
  if [[ ! -f target/temporal-tenant-routing-1.0.0.jar ]]; then
    echo 'Build first: mvn verify' >&2
    exit 1
  fi
  local hash
  hash=$(shasum -a 256 target/temporal-tenant-routing-1.0.0.jar | cut -c1-16)
  DEMO_IMAGE="temporal-tenant-routing:build-$hash"
}

render_app() {
  sed -e "s|__IMAGE__|$DEMO_IMAGE|g" -e "s|__LOCAL__|$LOCAL_TEMPORAL|g" \
    -e "s|__TARGET_A__|$TENANT_A_TARGET|g" -e "s|__TARGET_B__|$TENANT_B_TARGET|g" \
    -e "s|__NAMESPACE_A__|$TENANT_A_NAMESPACE|g" -e "s|__NAMESPACE_B__|$TENANT_B_NAMESPACE|g" \
    k8s/application.yaml.tpl
}

render_worker() {
  local tenant=$1 namespace=$2 target=$3
  sed -e "s|__TENANT__|$tenant|g" -e "s|__NAMESPACE__|$namespace|g" \
    -e "s|__TARGET__|$target|g" -e "s|__LOCAL__|$LOCAL_TEMPORAL|g" \
    -e "s|__IMAGE__|$DEMO_IMAGE|g" k8s/worker.yaml.tpl
}

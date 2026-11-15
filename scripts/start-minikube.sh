#!/usr/bin/env bash
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/common.sh"
require_tools minikube docker
docker info >/dev/null
minikube start -p "$CLUSTER_NAME" --driver=docker --container-runtime=containerd \
  --cni=calico --kubernetes-version=v1.37.2 --cpus=4 --memory=6144

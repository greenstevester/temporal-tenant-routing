#!/usr/bin/env bash
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/common.sh"
require_tools java mvn minikube shasum
mvn -B -ntp verify
image_name
minikube -p "$CLUSTER_NAME" image build -t "$DEMO_IMAGE" .
echo "Built $DEMO_IMAGE inside minikube."

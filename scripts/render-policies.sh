#!/usr/bin/env bash
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/common.sh"
load_mode "${1:-local}"
for namespace in application tenant-a tenant-b; do
  cat <<YAML
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: required-connections-only
  namespace: $namespace
spec:
  podSelector: {}
  policyTypes: [Ingress, Egress]
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: kube-system
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - {protocol: UDP, port: 53}
        - {protocol: TCP, port: 53}
YAML
  if [[ "$DEMO_MODE" == local ]]; then
    cat <<'YAML'
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: temporal-system
          podSelector:
            matchLabels:
              app: temporal
      ports:
        - {protocol: TCP, port: 7233}
YAML
  else
    cat <<'YAML'
    - to:
        - ipBlock:
            cidr: 0.0.0.0/0
            except: [10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, 127.0.0.0/8]
      ports:
        - {protocol: TCP, port: 7233}
YAML
  fi
  if [[ "$namespace" != application ]]; then
    cat <<YAML
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: tenant-budget
  namespace: $namespace
spec:
  hard:
    requests.cpu: "1"
    requests.memory: "2Gi"
    limits.cpu: "6"
    limits.memory: "3Gi"
    pods: "6"
YAML
  fi
done

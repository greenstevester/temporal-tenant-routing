#!/usr/bin/env bash
set -euo pipefail
# shellcheck source-path=SCRIPTDIR
source "$(dirname "$0")/common.sh"
require_tools kubectl curl jq shasum
load_mode "${1:-local}"
image_name
[[ $(cat .local/deployed-mode) == "$DEMO_MODE" ]] || { echo 'Deploy this mode first.' >&2; exit 1; }
[[ $(kube -n application get deployment tenant-application -o jsonpath='{.spec.template.spec.containers[0].image}') \
  == "$DEMO_IMAGE" ]] || { echo 'Built image differs from deployment. Deploy again.' >&2; exit 1; }
[[ $(kube -n tenant-a get deployment temporal-worker -o jsonpath='{.spec.replicas}') == 2 ]] || exit 1
RUN_ID="$(date +%s)-$$"
EVIDENCE="test-evidence/$RUN_ID"
PRIVATE=".local/test-$RUN_ID"
mkdir -p "$EVIDENCE"
umask 077
mkdir -p "$PRIVATE"
printf 'user = "alice:%s"\n' "$(cat .local/alice-password)" > "$PRIVATE/alice.curl"
printf 'user = "bob:%s"\n' "$(cat .local/bob-password)" > "$PRIVATE/bob.curl"
BASE=http://127.0.0.1:18080
kube -n application port-forward --address 127.0.0.1 service/tenant-application 18080:8080 \
  > "$EVIDENCE/port-forward.log" 2>&1 &
FORWARD_PID=$!
restore() {
  local result=$?
  trap - EXIT
  kube -n tenant-a scale deployment temporal-worker --replicas=2 >/dev/null || \
    echo 'Could not restore A workers; check the disposable cluster.' >&2
  kill "$FORWARD_PID" 2>/dev/null || true
  exit "$result"
}
trap restore EXIT
for _attempt in $(seq 1 60); do
  kill -0 "$FORWARD_PID" 2>/dev/null || { cat "$EVIDENCE/port-forward.log" >&2; exit 1; }
  if grep -q 'Forwarding from 127.0.0.1:18080' "$EVIDENCE/port-forward.log" &&
    curl -fsS --max-time 2 "$BASE/health" >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS "$BASE/health" >/dev/null

request() {
  local user=$1 path=$2
  curl -fsS --max-time 15 --config "$PRIVATE/$user.curl" "$BASE$path"
}

start_job() {
  local user=$1 job=$2 token header
  local token_json
  token_json=$(curl -fsS --config "$PRIVATE/$user.curl" --cookie-jar "$PRIVATE/$user.cookies" "$BASE/csrf")
  token=$(jq -er .token <<< "$token_json")
  header=$(jq -er .headerName <<< "$token_json")
  curl -fsS --max-time 15 --config "$PRIVATE/$user.curl" --cookie "$PRIVATE/$user.cookies" \
    -H "$header: $token" -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-a' \
    --data "{\"jobId\":\"$job\"}" "$BASE/jobs" > "$EVIDENCE/$user-$job-start.json"
}

wait_result() {
  local user=$1 job=$2 output="$EVIDENCE/$1-$2-result.json" code
  for _attempt in $(seq 1 150); do
    code=$(curl -sS --max-time 15 --config "$PRIVATE/$user.curl" -o "$output" \
      -w '%{http_code}' "$BASE/jobs/$job/result")
    case "$code" in
      200) jq -e '.executions | type == "array"' "$output" >/dev/null && return ;;
      202) ;;
      *)
        echo "Job $user/$job failed: HTTP $code $(cat "$output"); evidence: $EVIDENCE" >&2
        return 1 ;;
    esac
    sleep 1
  done
  echo "Timed out waiting for $user/$job; evidence: $EVIDENCE" >&2
  return 1
}

snapshot() {
  local label=$1 namespace
  for namespace in tenant-a tenant-b; do
    kube -n "$namespace" get pods -o json > "$EVIDENCE/$namespace-$label-pods.json"
    kube -n "$namespace" logs -l app=temporal-worker --prefix --tail=-1 --max-log-requests=6 \
      > "$EVIDENCE/$namespace-$label.log"
  done
}

echo '1. Start both tenants; capture their actual worker locations.'
snapshot before
CRASH_JOB="crash-$RUN_ID"
start_job alice "$CRASH_JOB"
start_job bob "$CRASH_JOB"
BUSY_POD=''
for _attempt in $(seq 1 90); do
  kube -n tenant-a logs -l app=temporal-worker --prefix --tail=-1 --max-log-requests=6 \
    > "$EVIDENCE/tenant-a-before-crash.log"
  BUSY_POD=$(awk -v job="workflow=tenant-a/job/$CRASH_JOB" '
    index($0, job) && /step=processing / {
      for (i = 1; i <= NF; i++) if ($i ~ /^pod=/) { sub(/^pod=/, "", $i); print $i; exit }
    }' "$EVIDENCE/tenant-a-before-crash.log")
  if [[ -n "$BUSY_POD" ]]; then break; fi
  sleep 1
done
[[ -n "$BUSY_POD" ]] || { echo 'No running A activity found.' >&2; exit 1; }
echo "2. Force-delete the busy A pod: $BUSY_POD"
kube -n tenant-a delete pod "$BUSY_POD" --grace-period=0 --force
wait_result alice "$CRASH_JOB"
wait_result bob "$CRASH_JOB"
jq -e '[.executions[] | select(.step == "processed") | .attempt] | length == 1 and all(. > 1)' \
  "$EVIDENCE/alice-$CRASH_JOB-result.json" >/dev/null
kube -n tenant-a rollout status deployment/temporal-worker --timeout=180s
snapshot after-crash

echo '3. Stop every A worker and submit more work for both tenants.'
kube -n tenant-a scale deployment temporal-worker --replicas=0
for _attempt in $(seq 1 90); do
  if [[ $(kube -n tenant-a get pods -l app=temporal-worker -o json | jq '.items | length') == 0 ]]; then
    break
  fi
  sleep 1
done
[[ $(kube -n tenant-a get pods -l app=temporal-worker -o json | jq '.items | length') == 0 ]]
OUTAGE_JOB="outage-$RUN_ID"
start_job alice "$OUTAGE_JOB"
start_job bob "$OUTAGE_JOB"
wait_result bob "$OUTAGE_JOB"
request alice "/jobs/$OUTAGE_JOB" > "$EVIDENCE/a-while-stopped.json"
jq -e '.status == "WORKFLOW_EXECUTION_STATUS_RUNNING"' "$EVIDENCE/a-while-stopped.json" >/dev/null
[[ $(kube -n tenant-a get pods -l app=temporal-worker -o json | jq '.items | length') == 0 ]]

echo '4. Start replacement A workers; complete the original waiting workflow.'
kube -n tenant-a scale deployment temporal-worker --replicas=2
kube -n tenant-a rollout status deployment/temporal-worker --timeout=180s
wait_result alice "$OUTAGE_JOB"
snapshot after-restart

echo '5. Check workflow-task history and activity locations against pod records.'
for user in alice bob; do
  if [[ "$user" == alice ]]; then tenant='tenant-a'; namespace=$TENANT_A_NAMESPACE
  else tenant='tenant-b'; namespace=$TENANT_B_NAMESPACE; fi
  jq -s '[.[].items[].metadata.name] | unique' "$EVIDENCE/$tenant-"*-pods.json \
    > "$EVIDENCE/$tenant-known-pods.json"
  for job in "$CRASH_JOB" "$OUTAGE_JOB"; do
    jq -e --arg tenant "$tenant" --arg namespace "$namespace" \
      --slurpfile pods "$EVIDENCE/$tenant-known-pods.json" \
      '.tenantId == $tenant and (.executions | length == 3) and
       all(.executions[]; .tenantId == $tenant and .podNamespace == $tenant and
           .temporalNamespace == $namespace and .taskQueue == ($tenant + "-work") and
           (.podName as $pod | $pods[0] | index($pod) != null))' \
      "$EVIDENCE/$user-$job-result.json" >/dev/null
    request "$user" "/jobs/$job/workers" > "$EVIDENCE/$user-$job-workers.json"
    jq -e --arg tenant "$tenant" --slurpfile pods "$EVIDENCE/$tenant-known-pods.json" \
      '(.workflowWorkers | length > 0) and
       all(.workflowWorkers[]; startswith($tenant + "/") and
           (split("/")[1] as $pod | $pods[0] | index($pod) != null))' \
      "$EVIDENCE/$user-$job-workers.json" >/dev/null
  done
done

echo '6. Verify B cannot read A secrets, create A workloads or enter A pods.'
for action in 'get secrets' 'list secrets' 'create pods' 'create deployments.apps' 'create pods/exec'; do
  read -r verb resource <<< "$action"
  answer=$(kube auth can-i "$verb" "$resource" -n tenant-a \
    --as=system:serviceaccount:tenant-b:temporal-worker || true)
  [[ "$answer" == no ]] || { echo "Unexpected permission: $action ($answer)" >&2; exit 1; }
done

echo '7. Run B-credential access probe against A.'
if [[ "$DEMO_MODE" == cloud ]]; then denied=true; else denied=false; fi
sed -e "s|__IMAGE__|$DEMO_IMAGE|g" -e "s|__RUN__|$RUN_ID|g" -e "s|__JOB__|$CRASH_JOB|g" \
  -e "s|__LOCAL__|$LOCAL_TEMPORAL|g" -e "s|__DENIED__|$denied|g" \
  -e "s|__TARGET_A__|$TENANT_A_TARGET|g" -e "s|__TARGET_B__|$TENANT_B_TARGET|g" \
  -e "s|__NAMESPACE_A__|$TENANT_A_NAMESPACE|g" -e "s|__NAMESPACE_B__|$TENANT_B_NAMESPACE|g" \
  k8s/access-probe.yaml.tpl > "$EVIDENCE/access-probe.yaml"
kube apply -f "$EVIDENCE/access-probe.yaml"
if ! kube -n tenant-b wait --for=condition=complete "job/access-probe-$RUN_ID" --timeout=110s; then
  kube -n tenant-b logs "job/access-probe-$RUN_ID" >&2
  exit 1
fi
kube -n tenant-b logs "job/access-probe-$RUN_ID" > "$EVIDENCE/access-probe.log"
if [[ "$DEMO_MODE" == cloud ]]; then
  grep -q SECURITY_DENIAL_PASSED "$EVIDENCE/access-probe.log"
  echo "Routing, recovery and Temporal permission denial passed. Evidence: $EVIDENCE"
else
  grep -q LOCAL_SERVER_ACCEPTED_CROSS_TENANT_ACCESS "$EVIDENCE/access-probe.log"
  echo "Routing and recovery passed. Local Temporal authorization is NOT enforced. Evidence: $EVIDENCE"
fi

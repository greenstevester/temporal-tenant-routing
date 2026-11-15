#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B -ntp verify
for script in scripts/*.sh; do bash -n "$script"; done
if command -v shellcheck >/dev/null; then
  shellcheck -x scripts/*.sh
else
  echo 'ShellCheck not installed; only Bash syntax was checked.' >&2
fi

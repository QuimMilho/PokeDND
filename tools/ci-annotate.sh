#!/usr/bin/env bash
# Runs a command and, if it fails, copies the end of its output into a GitHub annotation.
#
# The logs of a workflow run need a GitHub login, but the annotations of a job are public (api.github.com/repos/.../check-runs/<job id>/annotations).
# Wrapping a test command in this script makes a failure readable without logging in. The output also goes to the log as usual.
#
# Usage: bash tools/ci-annotate.sh "Title of the step" command arg...
title="$1"
shift
log="$(mktemp)"
"$@" 2>&1 | tee "$log"
code=${PIPESTATUS[0]}
if [ "$code" -ne 0 ]; then
  # no colour codes, no progress lines, the last 60 lines at most; % and line breaks are escaped as GitHub wants
  text="$(sed 's/\x1b\[[0-9;]*[A-Za-z]//g' "$log" | grep -v 'Executed [0-9]* of' | grep -v 'WARN \[web-server\]' | tail -n 60 | cut -c1-300 | sed -e 's/\r//g' -e 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
  echo "::error title=${title} failed (exit ${code})::${text}"
fi
rm -f "$log"
exit "$code"

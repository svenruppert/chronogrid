#!/usr/bin/env bash
# Start the in-process caldav-testbench on port 5232 seeded with a
# "personal" collection. Parks until you hit Ctrl+C.
#
# After Phase 2 of the calendar extraction the launcher class lives
# in the chronogrid-core module under the published add-on namespace
# (com.svenruppert.vaadin.calendar.*), so the exec:java invocation
# needs `-pl chronogrid-core` to find both the test-scope dep
# (caldav-testbench) and the dev launcher class itself.
#
# Companion to README.md → "Running with a CalDAV backend — Option A".

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT_DIR}"

exec ./mvnw -pl chronogrid-core exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=junit.com.svenruppert.chronogrid.CalDavDevServer \
  "$@"

#!/usr/bin/env bash
# Start the Vaadin demo (CalDavDemo) so the Calendar view is pre-wired
# to the local caldav-testbench started by ./start-caldav-dev-server.sh.
#
# Uses -Dexec.classpathScope=test because jakarta.servlet-api is
# declared with scope=provided in pom.xml (so it does not leak into the
# WAR). The default exec:java classpath (runtime) excludes provided
# deps, so without this scope the embedded Jetty fails with
# NoClassDefFoundError: jakarta/servlet/Servlet.
#
# Overrides are forwarded verbatim, e.g.:
#
#   ./start-vaadin-demo.sh -Dapp.caldav.baseUri=https://nextcloud/.../personal/
#   ./start-vaadin-demo.sh -Dapp.port=9090
#
# Companion to README.md → "Running with a CalDAV backend — Option A".

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# After Phase 2 of the calendar extraction the demo lives in the
# `demo/` submodule, so the reactor build needs `-pl chronogrid-demo` to scope
# exec:java to it. The class FQN itself is unchanged.
exec ./mvnw -pl chronogrid-demo exec:java \
    -Dexec.classpathScope=test \
    -Dexec.mainClass=com.svenruppert.flow.CalDavDemo \
    "$@"

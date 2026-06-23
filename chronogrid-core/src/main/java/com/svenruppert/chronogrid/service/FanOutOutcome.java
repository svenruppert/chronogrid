/*
 * Copyright © 2013 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package com.svenruppert.chronogrid.service;

import com.svenruppert.chronogrid.client.CalDavError;
import org.vaadin.stefan.fullcalendar.Entry;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Outcome of a partial-failure-isolated fan-out across multiple
 * {@code CalDavClient}s. Carries every successful client's entries
 * <strong>and</strong> a per-failed-client {@link CalDavError} so
 * the caller can surface graceful degradation (e.g. status-aware
 * notifications naming the affected server) instead of aborting the
 * entire refresh on a single timeout.
 *
 * <p>Empty {@link #failures()} means every client succeeded — the
 * outcome is semantically identical to the legacy
 * {@code findInRange} return value. A non-empty {@link #failures()}
 * means some clients failed; {@link #entries()} still contains
 * everything the surviving clients returned. The two parts are
 * independent — the caller decides whether a partial result is
 * acceptable for the current operation.
 *
 * <p>Both members are defensively copied / unmodifiable on
 * construction so the outcome is a true value object, safe to pass
 * across UI threads.
 *
 * @param entries  all entries from clients that completed
 *                 successfully (already colour-stamped, ordered by
 *                 client + per-client insertion order — the same
 *                 ordering the legacy {@code findInRange} produced)
 * @param failures keyed by the client's {@code collectionUri()};
 *                 the value carries the classified error + the
 *                 original cause for logging
 */
public record FanOutOutcome(List<Entry> entries,
                            Map<URI, CalDavError> failures)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  public FanOutOutcome {
    entries = entries == null ? List.of() : List.copyOf(entries);
    failures = failures == null ? Map.of() : Map.copyOf(failures);
  }

  /** Convenience: true when at least one client failed. */
  public boolean hasFailures() {
    return !failures.isEmpty();
  }
}

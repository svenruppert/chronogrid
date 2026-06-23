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

/*-
 * #%L
 * Calendar — CalDAV headless
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2013 - 2026 Sven Ruppert
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.chronogrid.client.CalDavClient;
import com.svenruppert.chronogrid.client.CalDavError;
import com.svenruppert.chronogrid.client.RemoteEvent;
import com.svenruppert.chronogrid.mapping.EntryMapper;
import com.svenruppert.functional.result.Result;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.vaadin.stefan.fullcalendar.Entry;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Application-facing façade over the CalDAV wire. The read and write
 * methods are intentionally split — that is the seam the concept
 * documents call out for the jSentinel permission gate, so a
 * read-only role can be granted {@code calendar:read} while
 * {@code calendar:write} stays admin-bound.
 *
 * <p>Multi-calendar mode: when {@code fromConfig} sees
 * {@code additionalCollections}, the service spawns one extra
 * {@link CalDavClient} per URI (same credentials) and
 * {@link #findInRange} fans out across all of them, colour-stamping
 * each Entry by source. Writes still target the primary collection
 * for new entries; existing entries write back to whichever URI
 * their {@code caldavHref} points at — so dragging an Apple Work
 * event keeps it in the Work calendar.
 */
public final class CalendarService implements HasLogger {

  private static final String[] PALETTE = {
      "#1F77B4", "#FF7F0E", "#2CA02C", "#D62728",
      "#9467BD", "#8C564B", "#E377C2", "#7F7F7F"
  };

  /**
   * The owning calendar's colour, stamped on every Entry during
   * {@link #fanOut} so the renderer can paint a provenance border /
   * stripe even when the entry carries its own user-set fill colour
   * (see {@link EntryMapper#CUSTOM_ENTRY_COLOR}).
   */
  public static final String CUSTOM_CALENDAR_COLOR = "caldavCalendarColor";

  private final CalDavClient primary;
  private final List<CalDavClient> readClients;
  private final EntryMapper mapper;
  private final ZoneId displayZone;

  public CalendarService(URI collectionUri) {
    this(new CalDavClient(collectionUri), ZoneId.systemDefault());
  }

  public static CalendarService fromConfig(CalDavConnectionConfig config,
                                           ZoneId displayZone) {
    CalDavConnectionConfig n = config.normalised();
    CalDavClient primary = buildClient(n.collectionUri(), n);
    List<CalDavClient> read = new ArrayList<>();
    read.add(primary);
    for (URI extra : n.additionalCollections()) {
      if (extra != null && !extra.equals(n.collectionUri())) {
        read.add(buildClient(extra, n));
      }
    }
    return new CalendarService(primary, read, displayZone);
  }

  /**
   * Multi-server factory. Each {@link CalendarSubscription} is
   * paired with its owning {@link CalDavServerConnection} (matched
   * by {@code serverId}); the resulting {@link CalDavClient} uses
   * that server's credentials. Subscriptions without a known server
   * fall back to anonymous (testbench-style).
   */
  public static CalendarService fromConnections(
      List<CalDavServerConnection> servers,
      List<CalendarSubscription> subscriptions,
      ZoneId displayZone) {
    Map<String, CalDavServerConnection> byId = new HashMap<>();
    for (CalDavServerConnection s : servers) byId.put(s.id(), s);

    if (subscriptions.isEmpty()) {
      throw new IllegalArgumentException(
          "fromConnections requires at least one subscription");
    }

    List<CalDavClient> clients = new ArrayList<>();
    CalDavClient primary = null;
    for (CalendarSubscription sub : subscriptions) {
      CalDavServerConnection server = sub.serverId() == null
          ? null : byId.get(sub.serverId());
      CalDavClient client = server != null && server.hasAuth()
          ? new CalDavClient(sub.uri(), server.username(), server.password())
          : new CalDavClient(sub.uri());
      clients.add(client);
      if (primary == null) primary = client;
    }
    return new CalendarService(primary, clients, displayZone);
  }

  private static CalDavClient buildClient(URI uri, CalDavConnectionConfig auth) {
    return auth.hasAuth()
        ? new CalDavClient(uri, auth.username(), auth.password())
        : new CalDavClient(uri);
  }

  public CalendarService(CalDavClient client, ZoneId displayZone) {
    this(client, List.of(client), displayZone);
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "CalDavClients are injected, thread-safe HTTP adapters; "
          + "the service intentionally keeps the caller-owned references so "
          + "tests can swap them for fixture-backed instances.")
  public CalendarService(CalDavClient primary, List<CalDavClient> readClients,
                         ZoneId displayZone) {
    this.primary = primary;
    this.readClients = List.copyOf(readClients);
    this.displayZone = displayZone;
    this.mapper = new EntryMapper(displayZone);
    logger().info("CalendarService ready: primary={} readClients={} zone={}",
        primary.collectionUri(), this.readClients.size(), displayZone);
  }

  // ── read side ──────────────────────────────────────────────────

  public Stream<Entry> findInRange(LocalDateTime from, LocalDateTime to) {
    Instant fromUtc = from.atZone(displayZone).toInstant();
    Instant toUtc = to.atZone(displayZone).toInstant();
    return fanOut(client -> client.findInRange(fromUtc, toUtc));
  }

  public Stream<Entry> findTodosInRange(LocalDateTime from, LocalDateTime to) {
    Instant fromUtc = from.atZone(displayZone).toInstant();
    Instant toUtc = to.atZone(displayZone).toInstant();
    return fanOut(client -> client.findTodosInRange(fromUtc, toUtc));
  }

  /**
   * Planning-Feature #8 — parallel fan-out across all read clients.
   *
   * <p>Pre-Feature-#8 this loop was strictly sequential: 5 servers
   * each taking ~800&nbsp;ms REPORT meant ~4&nbsp;s before the first
   * entry surfaced, and one hanging server blocked every other. The
   * parallel variant collapses wall-clock to the slowest-single-
   * client time and isolates failures.
   *
   * <p>{@link CompletableFuture#supplyAsync(java.util.function.Supplier)}
   * uses {@link java.util.concurrent.ForkJoinPool#commonPool()},
   * which is appropriate for short-lived HTTP I/O bursts (the FJP
   * default parallelism = available cores). Each per-client task
   * builds a private {@code List<Entry>}; only the final flattening
   * touches a shared {@link Stream.Builder}, single-threaded, so
   * no synchronisation is needed inside the workers.
   *
   * <p><b>Failure semantics:</b> matches the pre-Feature-#8 contract.
   * If any client throws, {@code allOf().join()} re-throws the
   * underlying exception wrapped in {@link java.util.concurrent.CompletionException}
   * — unwrapped here so callers still catch the original
   * {@link RuntimeException} they used to catch. Future work
   * (graceful per-client partial-failure surfacing) is tracked in
   * Planning #7 Schicht 4 (already shipped, status-aware
   * notifications). Until then, "any client fails → fan-out fails"
   * stays the contract.
   */
  private Stream<Entry> fanOut(
      java.util.function.Function<CalDavClient, List<RemoteEvent>> op) {
    Map<URI, String> colourByUri = new HashMap<>(readClients.size());
    for (CalDavClient c : readClients) {
      colourByUri.put(c.collectionUri(),
          PALETTE[Math.floorMod(c.collectionUri().hashCode(), PALETTE.length)]);
    }

    List<java.util.concurrent.CompletableFuture<List<Entry>>> futures =
        new ArrayList<>(readClients.size());
    for (CalDavClient client : readClients) {
      futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
        List<Entry> perClient = new ArrayList<>();
        String calendarColor = colourByUri.get(client.collectionUri());
        for (RemoteEvent remote : op.apply(client)) {
          Entry entry = mapper.toEntry(remote);
          applyColours(entry, calendarColor);
          perClient.add(entry);
        }
        return perClient;
      }));
    }

    try {
      java.util.concurrent.CompletableFuture
          .allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
          .join();
    } catch (java.util.concurrent.CompletionException ce) {
      Throwable cause = ce.getCause();
      if (cause instanceof RuntimeException re) throw re;
      throw ce;
    }

    Stream.Builder<Entry> all = Stream.builder();
    int total = 0;
    for (java.util.concurrent.CompletableFuture<List<Entry>> f : futures) {
      List<Entry> entries = f.getNow(List.of());
      for (Entry e : entries) all.accept(e);
      total += entries.size();
    }
    logger().info("fanOut across {} client(s) produced {} entries total "
        + "(parallel, ForkJoinPool.commonPool)",
        readClients.size(), total);
    return all.build();
  }

  /**
   * Colour-stamp an entry with its owning calendar's colour for
   * provenance plus, if the user has set an individual VEVENT
   * COLOR property, paint the fill in that user colour while the
   * border keeps the calendar colour (Google-Calendar-style:
   * fill = own colour, border = calendar source).
   *
   * <p>The {@link #CUSTOM_CALENDAR_COLOR} custom property always
   * carries the calendar colour after this method so downstream
   * renderers (CSS hooks) can read it independently of FullCalendar's
   * own border/background slots.
   */
  public static void applyColours(Entry entry, String calendarColor) {
    entry.setCustomProperty(CUSTOM_CALENDAR_COLOR, calendarColor);
    String individualColor = entry.getCustomProperty(EntryMapper.CUSTOM_ENTRY_COLOR);
    String effectiveBackground;
    if (individualColor != null && !individualColor.isBlank()) {
      entry.setBackgroundColor(individualColor);
      entry.setBorderColor(calendarColor);
      effectiveBackground = individualColor;
    } else {
      // No user-set colour: fall back to the calendar colour for both
      // (uniform look — same as the pre-v01.00.00 behaviour).
      entry.setColor(calendarColor);
      effectiveBackground = calendarColor;
    }
    // BUG #15: FullCalendar defaults the event text to white,
    // which becomes unreadable when the user picks a pale fill
    // (lightyellow, #ffffe0, etc.). Compute the readable colour
    // from the effective background via WCAG contrast.
    entry.setTextColor(
        com.svenruppert.chronogrid.mapping.ContrastTextColor.pickFor(effectiveBackground));
  }

  /**
   * Applies a user-chosen <strong>subscription colour</strong> on top
   * of the calendar's CalDAV-side default. Walks {@code subColours}
   * to find a URI prefix that matches the entry's
   * {@code CUSTOM_HREF}; on a hit, the matched colour is fed through
   * {@link #applyColours(Entry, String)} so the per-event colour
   * layering (fill = own colour when set, border = calendar) is
   * preserved.
   *
   * <p>BUG #1 fix: before this method existed, the call site in
   * {@code ChronoGrid.rangeWithStatus} ran a one-line
   * {@code entry.setColor(...)} that overwrote both fill <em>and</em>
   * border with the subscription colour — silently undoing any
   * per-event colour the user had picked. The delegation to
   * {@link #applyColours} re-introduces the split-colour rule for
   * the subscription-override branch too.
   *
   * <p>No-op if the entry has no {@code CUSTOM_HREF} or no URI in
   * {@code subColours} matches as a prefix; in those cases the entry
   * keeps whatever colours the upstream {@code fanOut} already set.
   */
  public static void applySubscriptionOverride(
      Entry entry,
      java.util.Map<URI, String> subColours) {
    if (entry == null || subColours == null || subColours.isEmpty()) return;
    java.util.Optional<URI> href = EntryMapper.readHref(entry);
    if (href.isEmpty()) return;
    String src = href.get().toString();
    for (java.util.Map.Entry<URI, String> e : subColours.entrySet()) {
      if (src.startsWith(e.getKey().toString())) {
        applyColours(entry, e.getValue());
        return;
      }
    }
  }

  public Optional<Entry> findById(String uid) {
    try {
      RemoteEvent remote = primary.get(uid);
      return Optional.of(mapper.toEntry(remote));
    } catch (NoSuchElementException nse) {
      return Optional.empty();
    }
  }

  // ── write side ─────────────────────────────────────────────────

  /**
   * Persists an Entry to the server. New entries (no ETag custom
   * property) go through {@code PUT If-None-Match: *} against the
   * <b>primary</b> collection; existing entries write back to
   * whichever URI their {@code caldavHref} points at — keeping a
   * cross-calendar drag (Work → Family) intentionally out of scope
   * for this iteration.
   */
  public Entry save(Entry entry) {
    return save(entry, null);
  }

  /**
   * Persists an entry, optionally routed to a specific target
   * collection. When {@code targetCollection} is non-null AND the
   * entry has no existing href, the new event is PUT into that
   * collection's client (a subscribed calendar the user chose in
   * the editor). Otherwise the existing href dictates the target
   * (so editing a Work event keeps it in the Work calendar).
   */
  public Entry save(Entry entry, URI targetCollection) {
    String uid = entry.getId();
    Optional<URI> existingHref = EntryMapper.readHref(entry);
    CalDavClient targetClient = pickClient(existingHref.orElse(targetCollection));
    URI target = existingHref.orElseGet(
        () -> targetClient.eventUri(uid));
    boolean isUpdate = EntryMapper.readEtag(entry).isPresent();
    logger().info("save entry uid={} kind={} mode={} target={}",
        uid, EntryMapper.isTodo(entry) ? "VTODO" : "VEVENT",
        isUpdate ? "update" : "new", target);
    // BUG #7: emit the DESCRIPTION-suffix colour sidechannel marker
    // ONLY when the target is an Apple/iCloud provider. Other
    // providers preserve RFC-7986 COLOR through user-edit-rewrite,
    // so the marker would show up as visible noise in their UI.
    //
    // BUG #12 part-2: non-Apple providers (Nextcloud in particular)
    // only render colour pills in their UI when COLOR carries a
    // CSS3 named token, not arbitrary hex. Prefer named colours
    // for non-Apple targets when a canonical equivalent exists;
    // fall back to hex when not. Apple targets keep hex because
    // iCloud's own UI doesn't show per-event colours anyway and
    // hex preserves precision for the DESCRIPTION-marker fallback.
    // Per-provider serialisation quirks (BUG #2, #7, #12) now live
    // in the chronogrid-core/provider package. ProviderRegistry
    // walks the URI through the known sniffers and falls back to
    // GenericProvider for unrecognised servers.
    com.svenruppert.chronogrid.provider.CalDavProviderProfile provider =
        com.svenruppert.chronogrid.provider.ProviderRegistry.forUri(target);
    String body = EntryMapper.isTodo(entry)
        ? mapper.toICalendarTodoText(entry)
        : mapper.toICalendarText(entry, provider);

    String newEtag = EntryMapper.readEtag(entry)
        .map(etag -> targetClient.putUpdate(target, body, etag))
        .orElseGet(() -> targetClient.putNew(target, body));

    EntryMapper.writeEtag(entry, newEtag);
    EntryMapper.writeHref(entry, target);
    return entry;
  }

  /**
   * Picks the {@link CalDavClient} whose collection URI prefixes
   * {@code uri} — falls back to {@link #primary} when nothing
   * matches (e.g. a brand-new event with no explicit target).
   */
  private CalDavClient pickClient(URI uri) {
    if (uri == null) return primary;
    for (CalDavClient c : readClients) {
      String collection = c.collectionUri().toString();
      if (uri.toString().startsWith(collection)) return c;
    }
    return primary;
  }

  public void delete(Entry entry) {
    URI target = EntryMapper.readHref(entry)
        .orElseGet(() -> primary.eventUri(entry.getId()));
    String etag = EntryMapper.readEtag(entry).orElse(null);
    logger().info("delete entry uid={} target={} etag={}",
        entry.getId(), target, etag);
    pickClient(target).delete(target, etag);
  }

  // ── Result-bearing variants (preferred for new callers) ───────

  /**
   * Result-wrapped {@link #findInRange}. The underlying CalDavClient
   * still throws on I/O / HTTP failures; we capture those at the
   * service boundary and yield a {@code Result.failure(CalDavError)}.
   * Tests prefer this variant; the View prefers it for chained
   * {@code peek}/{@code peekFailure}.
   */
  public Result<Stream<Entry>, CalDavError> findInRangeAsResult(
      LocalDateTime from, LocalDateTime to) {
    return safe(() -> findInRange(from, to));
  }

  public Result<Stream<Entry>, CalDavError> findTodosInRangeAsResult(
      LocalDateTime from, LocalDateTime to) {
    return safe(() -> findTodosInRange(from, to));
  }

  public Result<Optional<Entry>, CalDavError> findByIdAsResult(String uid) {
    return safe(() -> findById(uid));
  }

  public Result<Entry, CalDavError> saveAsResult(Entry entry) {
    return safe(() -> save(entry));
  }

  public Result<Void, CalDavError> deleteAsResult(Entry entry) {
    return safe(() -> {
      delete(entry);
      return null;
    });
  }

  private static <T> Result<T, CalDavError> safe(
      java.util.concurrent.Callable<T> body) {
    try {
      return Result.success(body.call());
    } catch (Exception ex) {
      return Result.failure(CalDavError.of(ex));
    }
  }

  // ── introspection (for tests / status views) ───────────────────

  public URI collectionUri() {
    return primary.collectionUri();
  }

  public List<URI> allCollectionUris() {
    List<URI> uris = new ArrayList<>(readClients.size());
    for (CalDavClient c : readClients) uris.add(c.collectionUri());
    return List.copyOf(uris);
  }
}

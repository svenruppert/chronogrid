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

package com.svenruppert.flow.calendar.service;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.calendar.client.CalDavClient;
import com.svenruppert.flow.calendar.client.CalDavError;
import com.svenruppert.flow.calendar.client.RemoteEvent;
import com.svenruppert.flow.calendar.mapping.EntryMapper;
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

  private Stream<Entry> fanOut(
      java.util.function.Function<CalDavClient, List<RemoteEvent>> op) {
    Stream.Builder<Entry> all = Stream.builder();
    int total = 0;
    for (int i = 0; i < readClients.size(); i++) {
      CalDavClient client = readClients.get(i);
      String color = PALETTE[Math.floorMod(client.collectionUri().hashCode(),
          PALETTE.length)];
      int perClient = 0;
      for (RemoteEvent remote : op.apply(client)) {
        Entry entry = mapper.toEntry(remote);
        if (entry.getColor() == null) entry.setColor(color);
        all.accept(entry);
        perClient++;
      }
      total += perClient;
    }
    logger().info("fanOut across {} client(s) produced {} entries total",
        readClients.size(), total);
    return all.build();
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
    String body = EntryMapper.isTodo(entry)
        ? mapper.toICalendarTodoText(entry)
        : mapper.toICalendarText(entry);

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

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

package com.svenruppert.chronogrid.mapping;

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

import biweekly.Biweekly;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.component.VTodo;
import biweekly.io.TimezoneAssignment;
import biweekly.property.DateDue;
import biweekly.property.DateEnd;
import biweekly.property.DateStart;
import biweekly.property.Categories;
import biweekly.property.Uid;
import biweekly.util.ICalDate;
import com.svenruppert.chronogrid.client.RemoteEvent;
import org.vaadin.stefan.fullcalendar.Entry;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Maps between {@link RemoteEvent} (iCalendar text + ETag + href)
 * and FullCalendar {@link Entry}.
 *
 * <p>The remote ETag and absolute href are stashed as custom
 * properties on the Entry so the round-trip
 * Read → render → user-edit → save keeps the concurrency token
 * available. Cleared on a freshly constructed Entry, the absence of
 * the ETag is the signal that {@code CalendarService.save} should
 * issue a {@code PUT If-None-Match: *}.
 */
public final class EntryMapper {

  public static final String CUSTOM_ETAG = "caldavEtag";
  public static final String CUSTOM_HREF = "caldavHref";
  public static final String CUSTOM_LOCATION = "caldavLocation";
  public static final String CUSTOM_URL = "caldavUrl";
  public static final String CUSTOM_TZID = "caldavTzid";
  public static final String CUSTOM_KIND = "caldavKind";
  public static final String KIND_VEVENT = "vevent";
  public static final String KIND_VTODO = "vtodo";
  public static final String CUSTOM_TODO_STATUS = "caldavTodoStatus";

  /**
   * User-set per-entry colour (RFC 7986 VEVENT COLOR property).
   * Stamped during {@link #toEntry} when the source VEVENT carries
   * a {@code COLOR} property; written back during
   * {@link #toICalendarText} when set.
   *
   * <p>This is the <em>individual</em> colour. The view's
   * {@code CalendarService.fanOut} pairs it with the surrounding
   * calendar's colour (set on the entry via
   * {@link com.svenruppert.chronogrid.service.CalendarService#CUSTOM_CALENDAR_COLOR})
   * so the renderer can show entry-fill + calendar-border at once.
   */
  public static final String CUSTOM_ENTRY_COLOR = "caldavEntryColor";

  /**
   * Comma-separated, normalised list of user-assigned tags
   * round-tripped through the iCalendar {@code CATEGORIES} property
   * (RFC 5545 §3.8.1.2). Tags are stored lower-cased + trimmed in
   * the custom property so cross-server equality is canonical; the
   * original casing is lost on the wire because CalDAV servers
   * (iCloud, Nextcloud, Radicale, Baïkal) treat CATEGORIES case-
   * insensitively across providers and round-tripping the input
   * casing would break filtering.
   *
   * <p>Empty / blank tags are dropped during normalisation; the
   * custom property is absent (not "") when no tags are set.
   */
  public static final String CUSTOM_CATEGORIES = "caldavCategories";

  /**
   * Suffix marker the writer appends to the DESCRIPTION field for
   * the per-event colour sidechannel. See BUG #2 final analysis:
   *
   * <ul>
   *   <li>The RFC-7986 {@code COLOR} property gets stripped by
   *       iCloud's native UI on the user-edit-rewrite path
   *       (iCloud has no per-event-colour concept in its data
   *       model).</li>
   *   <li>Custom {@code X-} properties (we tried
   *       {@code X-CHRONOGRID-COLOR}) <strong>also</strong> get
   *       stripped — Apple's own docs admit only
   *       {@code X-APPLE-*} properties are preserved through
   *       the UI-edit pipeline.</li>
   *   <li>Apple-survived fields with user-editable text are the
   *       only durable carrier: {@code SUMMARY}, {@code DESCRIPTION},
   *       {@code URL}. {@code DESCRIPTION} carries the colour with
   *       least UX intrusion (footer marker the user can ignore).</li>
   * </ul>
   *
   * <p>Format: a single line at the end of the description,
   * preceded by a blank line for visual separation:
   *
   * <pre>{@code
   * <user's description>
   *
   * [chronogrid-color: #ff0000]
   * }</pre>
   *
   * <p>The reader parses the suffix, strips it from
   * {@code entry.getDescription()} before exposing to the UI, and
   * stamps the captured colour onto {@link #CUSTOM_ENTRY_COLOR}.
   * The writer re-appends the suffix on save. Round-trip is
   * idempotent.
   *
   * <p>Forgiving regex on read (accepts variation in whitespace);
   * strict format on write (one canonical form).
   */
  public static final String COLOUR_MARKER_PREFIX = "[chronogrid-color:";

  private static final java.util.regex.Pattern COLOUR_MARKER_PATTERN =
      java.util.regex.Pattern.compile(
          "(?:\\s*\\n)*\\s*\\[chronogrid-color:\\s*(#[0-9a-fA-F]{3,8})\\]\\s*$");

  private final ZoneId displayZone;

  public EntryMapper() {
    this(ZoneId.systemDefault());
  }

  public EntryMapper(ZoneId displayZone) {
    this.displayZone = displayZone;
  }

  public Entry toEntry(RemoteEvent remote) {
    ICalendar ical = Biweekly.parse(remote.iCalBody()).first();
    if (ical == null) {
      throw new IllegalArgumentException(
          "RemoteEvent at " + remote.href() + " is not a parseable VCALENDAR");
    }
    if (ical.getEvents().isEmpty() && !ical.getTodos().isEmpty()) {
      return toEntryFromTodo(remote, ical);
    }
    if (ical.getEvents().isEmpty()) {
      throw new IllegalArgumentException(
          "RemoteEvent at " + remote.href() + " has no VEVENT or VTODO");
    }
    VEvent vevent = ical.getEvents().get(0);
    Uid uid = vevent.getUid();
    String id = (uid != null && uid.getValue() != null)
        ? uid.getValue()
        : UUID.randomUUID().toString();

    Entry entry = new Entry(id);
    Optional.ofNullable(vevent.getSummary()).map(s -> s.getValue())
        .ifPresent(entry::setTitle);

    // DESCRIPTION holds two things round-tripped: the user's notes
    // and (BUG #2 workaround) an optional [chronogrid-color: #xxx]
    // suffix marker. Parse + strip here so the UI only ever sees
    // the clean user-notes text.
    String descRaw = Optional.ofNullable(vevent.getDescription())
        .map(d -> d.getValue()).orElse(null);
    String descCleaned = descRaw;
    String colourFromMarker = null;
    if (descRaw != null) {
      java.util.regex.Matcher m = COLOUR_MARKER_PATTERN.matcher(descRaw);
      if (m.find()) {
        colourFromMarker = m.group(1);
        descCleaned = descRaw.substring(0, m.start()).stripTrailing();
        if (descCleaned.isEmpty()) descCleaned = null;
      }
    }
    if (descCleaned != null) entry.setDescription(descCleaned);

    boolean allDay = isAllDay(vevent.getDateStart());
    entry.setAllDay(allDay);

    String tzid = readTzid(ical, vevent.getDateStart());
    if (tzid != null) entry.setCustomProperty(CUSTOM_TZID, tzid);
    ZoneId effectiveZone = resolveZone(tzid);

    LocalDateTime start = extractLocalDateTime(vevent.getDateStart(), effectiveZone);
    LocalDateTime end = extractLocalDateTime(vevent.getDateEnd(), effectiveZone);
    if (start != null) entry.setStart(start);
    if (end != null) entry.setEnd(end);

    Optional.ofNullable(vevent.getLocation()).map(l -> l.getValue())
        .filter(s -> !s.isBlank())
        .ifPresent(s -> entry.setCustomProperty(CUSTOM_LOCATION, s));
    Optional.ofNullable(vevent.getUrl()).map(u -> u.getValue())
        .filter(s -> !s.isBlank())
        .ifPresent(s -> entry.setCustomProperty(CUSTOM_URL, s));
    // Read the per-event colour with the BUG #2 fallback chain.
    // Preference order:
    //   (1) RFC-7986 COLOR property   — preserved by all standards-
    //       compliant providers; iCloud preserves it as long as the
    //       user doesn't edit the event in iCloud's own UI.
    //   (2) DESCRIPTION suffix marker — Apple-survivable workaround,
    //       see COLOUR_MARKER_PREFIX docs. Parsed and stripped above.
    //   (3) absent → no CUSTOM_ENTRY_COLOR set; caller may overlay
    //       from a local-store fallback (ChronoGrid does this).
    String rawColor = Optional.ofNullable(vevent.getColor())
        .map(c -> c.getValue()).orElse(null);
    String entryColour =
        (rawColor != null && !rawColor.isBlank()) ? rawColor
        : (colourFromMarker != null && !colourFromMarker.isBlank())
            ? colourFromMarker : null;
    if (entryColour != null) {
      entry.setCustomProperty(CUSTOM_ENTRY_COLOR, entryColour);
    }

    String tags = readCategories(vevent);
    if (!tags.isEmpty()) entry.setCustomProperty(CUSTOM_CATEGORIES, tags);

    entry.setCustomProperty(CUSTOM_KIND, KIND_VEVENT);
    entry.setCustomProperty(CUSTOM_ETAG, remote.etag());
    entry.setCustomProperty(CUSTOM_HREF, remote.href().toString());
    return entry;
  }

  /**
   * Maps a VTODO resource to an {@link Entry}, tagged with
   * {@code caldavKind=vtodo} so the UI can distinguish todos from
   * events. DTSTART maps to {@code entry.setStart}, DUE maps to
   * {@code entry.setEnd} (so the same FullCalendar grid can show both).
   * The VTODO {@code STATUS} ends up on a custom property
   * {@code caldavTodoStatus} for the UI to render badges.
   */
  Entry toEntryFromTodo(RemoteEvent remote, ICalendar ical) {
    VTodo vtodo = ical.getTodos().get(0);
    Uid uid = vtodo.getUid();
    String id = (uid != null && uid.getValue() != null)
        ? uid.getValue()
        : UUID.randomUUID().toString();

    Entry entry = new Entry(id);
    Optional.ofNullable(vtodo.getSummary()).map(s -> s.getValue())
        .ifPresent(entry::setTitle);
    Optional.ofNullable(vtodo.getDescription()).map(d -> d.getValue())
        .ifPresent(entry::setDescription);

    String tzid = readTzid(ical, vtodo.getDateStart());
    if (tzid == null) tzid = readTzid(ical, vtodo.getDateDue());
    if (tzid != null) entry.setCustomProperty(CUSTOM_TZID, tzid);
    ZoneId zone = resolveZone(tzid);

    LocalDateTime start = extractLocalDateTime(vtodo.getDateStart(), zone);
    LocalDateTime due = extractLocalDateTime(vtodo.getDateDue(), zone);
    if (start != null) entry.setStart(start);
    if (due != null) entry.setEnd(due);

    Optional.ofNullable(vtodo.getStatus()).map(s -> s.getValue())
        .filter(s -> !s.isBlank())
        .ifPresent(s -> entry.setCustomProperty(CUSTOM_TODO_STATUS, s));

    entry.setCustomProperty(CUSTOM_KIND, KIND_VTODO);
    entry.setCustomProperty(CUSTOM_ETAG, remote.etag());
    entry.setCustomProperty(CUSTOM_HREF, remote.href().toString());
    return entry;
  }

  /**
   * Writes a VTODO iCalendar text for an Entry that was marked
   * {@code caldavKind=vtodo}. Mirrors {@link #toICalendarText(Entry)}
   * but emits a VTODO component (DUE instead of DTEND, STATUS).
   */
  public String toICalendarTodoText(Entry entry) {
    VTodo vtodo = new VTodo();
    String uid = entry.getId() != null ? entry.getId() : UUID.randomUUID().toString();
    vtodo.setUid(uid);
    if (entry.getTitle() != null) vtodo.setSummary(entry.getTitle());
    if (entry.getDescription() != null) vtodo.setDescription(entry.getDescription());

    String status = entry.getCustomProperty(CUSTOM_TODO_STATUS);
    if (status != null && !status.isBlank()) {
      vtodo.setStatus(new biweekly.property.Status(status));
    }

    boolean allDay = Boolean.TRUE.equals(entry.isAllDay());
    String tzid = entry.getCustomProperty(CUSTOM_TZID);
    ZoneId zone = resolveZone(tzid);

    LocalDateTime start = entry.getStart();
    LocalDateTime due = entry.getEnd();

    ICalendar cal = new ICalendar();
    cal.setVersion(ICalVersion.V2_0);
    cal.setProductId("-//Sven Ruppert//flow-template CalDavClient//EN");

    DateStart dateStart = null;
    DateDue dateDue = null;
    if (start != null) {
      dateStart = new DateStart(toIcalDate(start, zone, allDay));
      vtodo.setDateStart(dateStart);
    }
    if (due != null) {
      dateDue = new DateDue(toIcalDate(due, zone, allDay));
      vtodo.setDateDue(dateDue);
    }

    if (tzid != null && !allDay) {
      TimezoneAssignment assignment = new TimezoneAssignment(
          TimeZone.getTimeZone(tzid), tzid);
      if (dateStart != null) cal.getTimezoneInfo().setTimezone(dateStart, assignment);
      if (dateDue != null) cal.getTimezoneInfo().setTimezone(dateDue, assignment);
    }

    cal.addTodo(vtodo);
    return Biweekly.write(cal).go();
  }

  public static boolean isTodo(Entry entry) {
    return KIND_VTODO.equals(entry.getCustomProperty(CUSTOM_KIND));
  }

  private static boolean isAllDay(biweekly.property.DateOrDateTimeProperty prop) {
    if (prop == null) return false;
    ICalDate value = prop.getValue();
    return value != null && !value.hasTime();
  }

  public String toICalendarText(Entry entry) {
    // Backward-compatible default: emit the DESCRIPTION sidechannel
    // marker. Apple-safe by default (no harm if the provider isn't
    // Apple — the marker is just a discreet text line in the notes).
    return toICalendarText(entry, true);
  }

  /**
   * BUG #7: gate the DESCRIPTION-suffix sidechannel marker on the
   * caller's knowledge of the target provider. Apple iCloud strips
   * COLOR + every custom X- property on user-edit-rewrite, so
   * Apple writes need the marker for round-trip durability. Other
   * providers (Nextcloud, Baikal, Radicale) round-trip COLOR
   * correctly and would show the marker as user-visible noise in
   * their own UI — pass {@code appleSidechannel = false} for those.
   *
   * <p>The reader path is unchanged either way: it always tries
   * COLOR first and falls back to the marker if present, so a
   * legacy entry written with the marker still reads correctly
   * after the producer switches it off.
   */
  public String toICalendarText(Entry entry, boolean appleSidechannel) {
    VEvent vevent = new VEvent();
    String uid = entry.getId() != null ? entry.getId() : UUID.randomUUID().toString();
    vevent.setUid(uid);
    if (entry.getTitle() != null) vevent.setSummary(entry.getTitle());

    String entryColor = entry.getCustomProperty(CUSTOM_ENTRY_COLOR);
    boolean haveColour = entryColor != null && !entryColor.isBlank();

    // Description: combine user notes with the BUG #2 colour-suffix
    // marker ONLY when the target is an Apple/iCloud provider — the
    // marker is the only carrier that survives Apple's user-edit-
    // rewrite. Non-Apple providers don't need it (and would show it
    // as visible noise in their own UI), so we keep the description
    // clean for them.
    String userDesc = entry.getDescription();
    String descToWrite = composeDescription(
        userDesc, (haveColour && appleSidechannel) ? entryColor : null);
    if (descToWrite != null) vevent.setDescription(descToWrite);

    String location = entry.getCustomProperty(CUSTOM_LOCATION);
    if (location != null && !location.isBlank()) vevent.setLocation(location);
    String url = entry.getCustomProperty(CUSTOM_URL);
    if (url != null && !url.isBlank()) vevent.setUrl(url);
    if (haveColour) {
      // RFC-7986 COLOR — preserved by every standards-compliant
      // provider AND by iCloud as long as the event isn't touched
      // in iCloud's own UI. The DESCRIPTION marker above is the
      // safety net for the latter case.
      vevent.setColor(entryColor);
    }

    writeCategories(vevent, entry.getCustomProperty(CUSTOM_CATEGORIES));

    boolean allDay = Boolean.TRUE.equals(entry.isAllDay());
    String tzid = entry.getCustomProperty(CUSTOM_TZID);
    ZoneId zone = resolveZone(tzid);

    LocalDateTime start = entry.getStart();
    LocalDateTime end = entry.getEnd();

    ICalendar cal = new ICalendar();
    cal.setVersion(ICalVersion.V2_0);
    cal.setProductId("-//Sven Ruppert//flow-template CalDavClient//EN");

    DateStart dateStart = null;
    DateEnd dateEnd = null;
    if (start != null) {
      dateStart = new DateStart(toIcalDate(start, zone, allDay));
      vevent.setDateStart(dateStart);
    }
    if (end != null) {
      dateEnd = new DateEnd(toIcalDate(end, zone, allDay));
      vevent.setDateEnd(dateEnd);
    }

    if (tzid != null && !allDay) {
      TimezoneAssignment assignment = new TimezoneAssignment(
          TimeZone.getTimeZone(tzid), tzid);
      if (dateStart != null) cal.getTimezoneInfo().setTimezone(dateStart, assignment);
      if (dateEnd != null) cal.getTimezoneInfo().setTimezone(dateEnd, assignment);
    }

    cal.addEvent(vevent);
    return Biweekly.write(cal).go();
  }

  public static Optional<String> readEtag(Entry entry) {
    return Optional.ofNullable(entry.getCustomProperty(CUSTOM_ETAG));
  }

  public static Optional<URI> readHref(Entry entry) {
    String raw = entry.getCustomProperty(CUSTOM_HREF);
    return Optional.ofNullable(raw).map(URI::create);
  }

  public static void writeEtag(Entry entry, String etag) {
    entry.setCustomProperty(CUSTOM_ETAG, etag);
  }

  public static void writeHref(Entry entry, URI href) {
    entry.setCustomProperty(CUSTOM_HREF, href.toString());
  }

  private LocalDateTime extractLocalDateTime(biweekly.property.DateOrDateTimeProperty prop,
                                              ZoneId zone) {
    if (prop == null || prop.getValue() == null) return null;
    ICalDate value = prop.getValue();
    Instant instant = Instant.ofEpochMilli(value.getTime());
    return LocalDateTime.ofInstant(instant, zone);
  }

  private static String readTzid(ICalendar cal,
                                 biweekly.property.DateOrDateTimeProperty prop) {
    if (prop == null) return null;
    String paramTzid = prop.getParameter("TZID");
    if (paramTzid != null && !paramTzid.isBlank()) return paramTzid;
    // Biweekly normalises TZID into TimezoneInfo on parse — pick it up from there.
    TimezoneAssignment assignment = cal.getTimezoneInfo().getTimezone(prop);
    if (assignment == null) return null;
    if (assignment.getGlobalId() != null) return assignment.getGlobalId();
    if (assignment.getComponent() != null
        && assignment.getComponent().getTimezoneId() != null) {
      return assignment.getComponent().getTimezoneId().getValue();
    }
    return null;
  }

  private ZoneId resolveZone(String tzid) {
    if (tzid == null) return displayZone;
    try {
      return ZoneId.of(tzid);
    } catch (RuntimeException ignored) {
      return displayZone;
    }
  }

  private ICalDate toIcalDate(LocalDateTime ldt, ZoneId zone, boolean dateOnly) {
    Instant instant = ldt.atZone(zone).toInstant();
    return new ICalDate(Date.from(instant), !dateOnly);
  }

  /** For tests that want a deterministic UTC mapping. */
  static ICalDate utcIcalDate(LocalDateTime ldtUtc) {
    Instant instant = ldtUtc.toInstant(ZoneOffset.UTC);
    return new ICalDate(Date.from(instant), true);
  }

  // ── CATEGORIES (Feature #3 — tags) ────────────────────────────

  /**
   * Reads + normalises CATEGORIES from a VEVENT into a single
   * comma-separated string of unique lower-cased tags. Returns ""
   * when no CATEGORIES property is present or all values are blank.
   */
  static String readCategories(VEvent vevent) {
    java.util.List<Categories> all = vevent.getCategories();
    if (all == null || all.isEmpty()) return "";
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
    for (Categories cat : all) {
      for (String raw : cat.getValues()) {
        String norm = normaliseTag(raw);
        if (!norm.isEmpty()) out.add(norm);
      }
    }
    return String.join(",", out);
  }

  /**
   * Writes a comma-separated tag string back to a VEVENT as a single
   * CATEGORIES property. Pass {@code null} or "" to clear; existing
   * CATEGORIES instances are removed so the round-trip is lossless
   * (no stale tags from prior writes).
   */
  static void writeCategories(VEvent vevent, String csv) {
    vevent.removeProperties(Categories.class);
    if (csv == null || csv.isBlank()) return;
    Categories cat = new Categories();
    for (String raw : csv.split(",")) {
      String norm = normaliseTag(raw);
      if (!norm.isEmpty()) cat.getValues().add(norm);
    }
    if (!cat.getValues().isEmpty()) vevent.addCategories(cat);
  }

  /**
   * Public-API helper for callers (UI editor, filter logic) that want
   * the canonical tag-set without re-implementing the trim+lower
   * rules. Returns an empty set for absent / blank input.
   */
  public static java.util.Set<String> readTags(Entry entry) {
    if (entry == null) return java.util.Set.of();
    String raw = entry.getCustomProperty(CUSTOM_CATEGORIES);
    if (raw == null || raw.isBlank()) return java.util.Set.of();
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
    for (String t : raw.split(",")) {
      String norm = normaliseTag(t);
      if (!norm.isEmpty()) out.add(norm);
    }
    return out;
  }

  /**
   * Companion writer to {@link #readTags(Entry)}. Pass {@code null}
   * or empty set to clear the custom property; pass a populated set
   * to normalise + persist. Order is preserved (LinkedHashSet
   * semantics on the caller side leak through).
   */
  public static void writeTags(Entry entry, java.util.Set<String> tags) {
    if (entry == null) return;
    if (tags == null || tags.isEmpty()) {
      entry.setCustomProperty(CUSTOM_CATEGORIES, null);
      return;
    }
    java.util.LinkedHashSet<String> normalised = new java.util.LinkedHashSet<>();
    for (String t : tags) {
      String n = normaliseTag(t);
      if (!n.isEmpty()) normalised.add(n);
    }
    if (normalised.isEmpty()) {
      entry.setCustomProperty(CUSTOM_CATEGORIES, null);
      return;
    }
    entry.setCustomProperty(CUSTOM_CATEGORIES, String.join(",", normalised));
  }

  private static String normaliseTag(String raw) {
    if (raw == null) return "";
    return raw.trim().toLowerCase(java.util.Locale.ROOT);
  }

  // ── DESCRIPTION colour-marker round-trip (BUG #2) ─────────────

  /**
   * Combines the user-visible description with the colour-suffix
   * marker for the BUG #2 sidechannel. {@code null} for no marker,
   * returns the user description unchanged; null user description
   * + null colour returns null (no DESCRIPTION property emitted).
   *
   * <p>Canonical write format:
   * <pre>{@code <user description>\n\n[chronogrid-color: #ff0000]}</pre>
   * with a single blank line separator. If the user description is
   * empty/null but a colour is given, only the marker line is
   * emitted.
   */
  public static String composeDescription(String userDesc, String colour) {
    boolean haveColour = colour != null && !colour.isBlank();
    boolean haveDesc = userDesc != null && !userDesc.isEmpty();
    if (!haveColour) return haveDesc ? userDesc : null;
    String marker = COLOUR_MARKER_PREFIX + " " + colour + "]";
    if (!haveDesc) return marker;
    return userDesc.stripTrailing() + "\n\n" + marker;
  }
}

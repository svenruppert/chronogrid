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

package com.svenruppert.flow.calendar.mapping;

import biweekly.Biweekly;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.component.VTodo;
import biweekly.io.TimezoneAssignment;
import biweekly.property.DateDue;
import biweekly.property.DateEnd;
import biweekly.property.DateStart;
import biweekly.property.Uid;
import biweekly.util.ICalDate;
import com.svenruppert.flow.calendar.client.RemoteEvent;
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
    Optional.ofNullable(vevent.getDescription()).map(d -> d.getValue())
        .ifPresent(entry::setDescription);

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
    VEvent vevent = new VEvent();
    String uid = entry.getId() != null ? entry.getId() : UUID.randomUUID().toString();
    vevent.setUid(uid);
    if (entry.getTitle() != null) vevent.setSummary(entry.getTitle());
    if (entry.getDescription() != null) vevent.setDescription(entry.getDescription());

    String location = entry.getCustomProperty(CUSTOM_LOCATION);
    if (location != null && !location.isBlank()) vevent.setLocation(location);
    String url = entry.getCustomProperty(CUSTOM_URL);
    if (url != null && !url.isBlank()) vevent.setUrl(url);

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
}

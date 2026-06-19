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

package com.svenruppert.chronogrid.client;

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

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

/**
 * One CalDAV calendar surfaced by {@link CalDavDiscovery}. {@code href}
 * is the absolute collection URI ready to drop into the Settings
 * dialog; {@code displayName} is what the server advertises via the
 * {@code <DAV:displayname>} property (falls back to the last path
 * segment when missing); {@code color} is the server-supplied
 * {@code <C:calendar-color>} (may be {@code null} if the server omits
 * it). When a UI merges several calendars it falls back to a
 * deterministic palette keyed on the href hash.
 */
public record DiscoveredCalendar(URI href, String displayName, String color)
    implements Serializable {

  public DiscoveredCalendar {
    Objects.requireNonNull(href, "href must not be null");
  }

  /** Back-compat constructor for callers that pass no colour. */
  public DiscoveredCalendar(URI href, String displayName) {
    this(href, displayName, null);
  }
}

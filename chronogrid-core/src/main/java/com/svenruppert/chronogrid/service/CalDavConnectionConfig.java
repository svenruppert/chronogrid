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

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Per-session CalDAV connection configuration carried in
 * {@code VaadinSession}. Captures the primary collection URI plus
 * optional Basic-Auth credentials and optionally a list of additional
 * read-merged collections.
 *
 * <p>{@code username} / {@code password} may be {@code null} or
 * blank — that means anonymous access, which is exactly what the
 * caldav-testbench wants. {@link #hasAuth()} captures that test.
 *
 * <p>{@code additionalCollections} is the multi-calendar extension:
 * the primary URI receives writes (new events go there); every URI
 * in this list contributes entries to the read-merged view. Empty by
 * default — single-calendar usage is unchanged.
 */
public record CalDavConnectionConfig(URI collectionUri,
                                     String username,
                                     String password,
                                     List<URI> additionalCollections)
    implements Serializable {

  public CalDavConnectionConfig {
    Objects.requireNonNull(collectionUri, "collectionUri must not be null");
    additionalCollections = additionalCollections == null
        ? List.of()
        : List.copyOf(additionalCollections);
  }

  /** Back-compat: single-collection ctor. */
  public CalDavConnectionConfig(URI collectionUri, String username, String password) {
    this(collectionUri, username, password, List.of());
  }

  /** Convenience for unauthenticated targets (e.g. the testbench). */
  public static CalDavConnectionConfig anonymous(URI collectionUri) {
    return new CalDavConnectionConfig(collectionUri, null, null, List.of());
  }

  public boolean hasAuth() {
    return username != null && !username.isBlank();
  }

  /**
   * Returns a copy with the same URI but trimmed / nulled credentials
   * if either field is blank — keeps {@code equals} stable when the
   * dialog produces "" instead of {@code null}.
   */
  public CalDavConnectionConfig normalised() {
    String u = (username == null || username.isBlank()) ? null : username.trim();
    String p = (u == null || password == null || password.isEmpty()) ? null : password;
    return new CalDavConnectionConfig(collectionUri, u, p, additionalCollections);
  }
}

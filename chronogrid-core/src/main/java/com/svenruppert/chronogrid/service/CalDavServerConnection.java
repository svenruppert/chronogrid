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
import java.util.Objects;
import java.util.UUID;

/**
 * One configured CalDAV server connection in the user's session. A
 * subscription belongs to exactly one server; the service routes
 * each {@link CalDavClient} call with the server's credentials.
 *
 * <p>{@code id} is a stable UUID so {@link CalendarSubscription}s
 * can reference their owning server across re-saves of the list.
 * {@code displayName} surfaces in the Subscriptions dialog's "Server"
 * column.
 */
public record CalDavServerConnection(String id,
                                     String displayName,
                                     URI baseUri,
                                     String username,
                                     String password,
                                     com.svenruppert.chronogrid.auth.GoogleOAuthCredentials oauth)
    implements Serializable {

  public CalDavServerConnection {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(baseUri, "baseUri must not be null");
  }

  /**
   * Backward-compatible constructor — Basic-Auth + anonymous
   * paths use this; OAuth-secured servers (Planning #9) use the
   * full constructor with the {@code oauth} parameter or the
   * static {@link #createOAuth} factory.
   */
  public CalDavServerConnection(String id, String displayName, URI baseUri,
                                String username, String password) {
    this(id, displayName, baseUri, username, password, null);
  }

  public static CalDavServerConnection create(String displayName, URI baseUri,
                                              String username, String password) {
    return new CalDavServerConnection(
        UUID.randomUUID().toString(),
        displayName == null || displayName.isBlank()
            ? defaultDisplayName(baseUri)
            : displayName,
        baseUri, username, password, null);
  }

  /**
   * Planning-Feature #9 — factory for Google-style OAuth servers.
   * {@code username} stays null (no Basic-Auth), the OAuth
   * credentials carry the refresh-token + client-config from which
   * the Wizard built the connection.
   */
  public static CalDavServerConnection createOAuth(
      String displayName, URI baseUri,
      com.svenruppert.chronogrid.auth.GoogleOAuthCredentials oauth) {
    Objects.requireNonNull(oauth, "oauth must not be null");
    return new CalDavServerConnection(
        UUID.randomUUID().toString(),
        displayName == null || displayName.isBlank()
            ? defaultDisplayName(baseUri)
            : displayName,
        baseUri, null, null, oauth);
  }

  public boolean hasAuth() {
    return username != null && !username.isBlank();
  }

  /**
   * Planning-Feature #9 — true when this server uses OAuth instead
   * of Basic-Auth. The two paths are mutually exclusive:
   * {@code hasOAuth() && hasAuth()} should never happen — the
   * factories enforce that contract.
   */
  public boolean hasOAuth() {
    return oauth != null;
  }

  private static String defaultDisplayName(URI uri) {
    String host = uri.getHost();
    return host == null ? uri.toString() : host;
  }
}

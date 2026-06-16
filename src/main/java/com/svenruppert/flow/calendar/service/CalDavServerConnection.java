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
                                     String password)
    implements Serializable {

  public CalDavServerConnection {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(baseUri, "baseUri must not be null");
  }

  public static CalDavServerConnection create(String displayName, URI baseUri,
                                              String username, String password) {
    return new CalDavServerConnection(
        UUID.randomUUID().toString(),
        displayName == null || displayName.isBlank()
            ? defaultDisplayName(baseUri)
            : displayName,
        baseUri, username, password);
  }

  public boolean hasAuth() {
    return username != null && !username.isBlank();
  }

  private static String defaultDisplayName(URI uri) {
    String host = uri.getHost();
    return host == null ? uri.toString() : host;
  }
}

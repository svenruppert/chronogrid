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

package junit.com.svenruppert.chronogrid;

import com.svenruppert.chronogrid.auth.GoogleOAuthCredentials;
import com.svenruppert.chronogrid.auth.GoogleTokenRefresher;
import com.svenruppert.chronogrid.service.CalDavServerConnection;
import com.svenruppert.chronogrid.service.CalendarService;
import com.svenruppert.chronogrid.service.CalendarSubscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalendarService.fromConnections — Planning-Feature #9 OAuth wiring")
class CalendarServiceOAuthTest {

  @Test
  @DisplayName("OAuth server without a refresher → IllegalStateException")
  void oauthWithoutRefresherFailsFast() {
    GoogleOAuthCredentials creds = new GoogleOAuthCredentials(
        "client-id", "client-secret", "refresh-token");
    CalDavServerConnection google = CalDavServerConnection.createOAuth(
        "Google",
        URI.create("https://apidata.googleusercontent.com/caldav/v2/"),
        creds);
    CalendarSubscription sub = new CalendarSubscription(
        URI.create("https://apidata.googleusercontent.com/caldav/v2/p/events"),
        "Primary", "#1F77B4", true, google.id());

    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> CalendarService.fromConnections(
            List.of(google), List.of(sub), ZoneOffset.UTC));
    assertTrue(ex.getMessage().contains("OAuth"),
        "error must explain the OAuth-without-refresher contract — "
            + "got: " + ex.getMessage());
  }

  @Test
  @DisplayName("OAuth server + non-null refresher → service is constructed")
  void oauthWithRefresherBuildsService() {
    GoogleOAuthCredentials creds = new GoogleOAuthCredentials(
        "client-id", "client-secret", "refresh-token");
    CalDavServerConnection google = CalDavServerConnection.createOAuth(
        "Google",
        URI.create("https://apidata.googleusercontent.com/caldav/v2/"),
        creds);
    CalendarSubscription sub = new CalendarSubscription(
        URI.create("https://apidata.googleusercontent.com/caldav/v2/p/events"),
        "Primary", "#1F77B4", true, google.id());

    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        new com.google.api.client.testing.http.MockHttpTransport())) {
      CalendarService service = CalendarService.fromConnections(
          List.of(google), List.of(sub), ZoneOffset.UTC, refresher);
      assertNotNull(service,
          "OAuth server + refresher must build a service without "
              + "eagerly refreshing tokens (refresh is lazy on first request)");
    }
  }

  @Test
  @DisplayName("CalDavServerConnection.createOAuth blocks null oauth")
  void createOAuthRejectsNull() {
    assertThrows(NullPointerException.class,
        () -> CalDavServerConnection.createOAuth(
            "Google",
            URI.create("https://apidata.googleusercontent.com/caldav/v2/"),
            null));
  }

  @Test
  @DisplayName("CalDavServerConnection.createOAuth produces hasOAuth=true, hasAuth=false")
  void oauthFlagShape() {
    GoogleOAuthCredentials creds = new GoogleOAuthCredentials(
        "ci", "cs", "rt");
    CalDavServerConnection s = CalDavServerConnection.createOAuth(
        "Google",
        URI.create("https://apidata.googleusercontent.com/caldav/v2/"),
        creds);
    assertTrue(s.hasOAuth(), "OAuth-built connection must signal hasOAuth");
    assertEquals(false, s.hasAuth(),
        "OAuth-built connection must NOT report Basic-Auth — username is null");
    assertEquals(creds, s.oauth());
  }

  @Test
  @DisplayName("Mixed setup: one OAuth + one Basic-Auth server in the same service")
  void mixedAuthSetupWorks() {
    GoogleOAuthCredentials creds = new GoogleOAuthCredentials(
        "ci", "cs", "rt");
    CalDavServerConnection google = CalDavServerConnection.createOAuth(
        "Google",
        URI.create("https://apidata.googleusercontent.com/caldav/v2/"),
        creds);
    CalDavServerConnection nextcloud = CalDavServerConnection.create(
        "Nextcloud", URI.create("https://nc.example.com/remote.php/dav/"),
        "alice", "secret");
    CalendarSubscription sa = new CalendarSubscription(
        URI.create("https://apidata.googleusercontent.com/caldav/v2/p/events"),
        "Primary", "#1F77B4", true, google.id());
    CalendarSubscription sb = new CalendarSubscription(
        URI.create("https://nc.example.com/remote.php/dav/calendars/alice/work/"),
        "Work", "#FF7F0E", true, nextcloud.id());

    try (GoogleTokenRefresher refresher = new GoogleTokenRefresher(
        new com.google.api.client.testing.http.MockHttpTransport())) {
      CalendarService service = CalendarService.fromConnections(
          List.of(google, nextcloud), List.of(sa, sb),
          ZoneOffset.UTC, refresher);
      assertNotNull(service,
          "Multi-auth-shape setup must build a single CalendarService — "
              + "OAuth + Basic-Auth coexist behind the fan-out");
    }
  }
}

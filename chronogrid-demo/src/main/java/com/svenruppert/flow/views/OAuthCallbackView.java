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

package com.svenruppert.flow.views;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.svenruppert.chronogrid.auth.GoogleOAuthCredentials;
import com.svenruppert.chronogrid.auth.OAuthFlowRegistry;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Planning-Feature #9 Schicht 4 — landing page for Google's OAuth
 * authorization-code redirect.
 *
 * <p>Google opens this URL in the user's browser after they have
 * granted (or denied) the Calendar scope. The route is
 * intentionally public — the user has no app session in the new
 * tab — and renders a minimal "you can close this tab" message
 * once the code-for-tokens exchange has finished.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Read {@code state} and {@code code} (or {@code error})
 *       from the query string.</li>
 *   <li>Consume the pending flow from {@link OAuthFlowRegistry}.
 *       A missing entry means the state is stale or spoofed —
 *       render an error message and stop.</li>
 *   <li>If Google returned {@code ?error=...}, invoke the flow's
 *       {@code onComplete} with a null credentials object and the
 *       error message, then render the user-facing error.</li>
 *   <li>Otherwise: exchange the code for tokens via the SDK's
 *       {@link GoogleAuthorizationCodeTokenRequest} (with PKCE
 *       verifier), build a {@link GoogleOAuthCredentials} from
 *       the refresh token, invoke {@code onComplete} on success.
 *       The wizard on the originating UI receives the tokens via
 *       Vaadin Push and continues its flow.</li>
 *   <li>Show "Connection authorised — you can close this tab now."</li>
 * </ol>
 *
 * <p>The exchange step uses a freshly-created
 * {@link NetHttpTransport} — short-lived, OK to leak through the
 * route's lifecycle. The {@code GoogleTokenRefresher} on the
 * originating UI takes over for all subsequent access-token
 * rolls.
 *
 * <p>The route is public (no {@code @VisibleFor} annotation) and
 * uses no parent layout — just a simple centered panel.
 */
@Route(value = OAuthCallbackView.NAV)
public final class OAuthCallbackView extends Composite<VerticalLayout>
    implements BeforeEnterObserver {

  public static final String NAV = "oauth/callback/google";

  private final VerticalLayout root;

  public OAuthCallbackView() {
    this.root = getContent();
    root.setSizeFull();
    root.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
    root.setJustifyContentMode(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.CENTER);
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    QueryParameters params = event.getLocation().getQueryParameters();
    Map<String, List<String>> map = params.getParameters();

    String state = firstOrNull(map, "state");
    String code = firstOrNull(map, "code");
    String error = firstOrNull(map, "error");

    if (state == null) {
      showError("Missing state parameter — the redirect URL is malformed.");
      return;
    }

    OAuthFlowRegistry.OAuthFlow flow = OAuthFlowRegistry.consume(state);
    if (flow == null) {
      showError("State expired or unknown — restart the wizard.");
      return;
    }

    if (error != null) {
      flow.onComplete().accept(null, "Google returned error: " + error);
      showError("Google denied the request: " + error);
      return;
    }

    if (code == null) {
      flow.onComplete().accept(null, "Missing authorization code from Google.");
      showError("No authorization code returned by Google.");
      return;
    }

    try {
      TokenResponse response = new GoogleAuthorizationCodeTokenRequest(
          new NetHttpTransport(),
          GsonFactory.getDefaultInstance(),
          "https://oauth2.googleapis.com/token",
          flow.clientId(),
          flow.clientSecret(),
          code,
          flow.redirectUri())
          .set("code_verifier", flow.codeVerifier())
          .execute();

      String refreshToken = response.getRefreshToken();
      if (refreshToken == null || refreshToken.isBlank()) {
        // Google may omit the refresh token if the user has
        // previously granted the scope and we didn't request
        // access_type=offline + prompt=consent on the initial URL.
        // The wizard builds those parameters; an empty value here
        // signals that bookkeeping bug rather than a Google
        // problem.
        flow.onComplete().accept(null,
            "Google did not return a refresh token — the authorization "
                + "URL must include access_type=offline and prompt=consent. "
                + "The wizard build of the URL has a bug.");
        showError("No refresh token returned — try the wizard again.");
        return;
      }

      GoogleOAuthCredentials creds = new GoogleOAuthCredentials(
          flow.clientId(), flow.clientSecret(), refreshToken);
      flow.onComplete().accept(creds, null);
      showSuccess();
    } catch (IOException ioe) {
      flow.onComplete().accept(null,
          "Token-exchange against oauth2.googleapis.com failed: "
              + ioe.getMessage());
      showError("Token exchange failed: " + ioe.getMessage());
    }
  }

  private static String firstOrNull(Map<String, List<String>> map, String key) {
    List<String> values = map.get(key);
    if (values == null || values.isEmpty()) return null;
    String v = values.get(0);
    return v == null || v.isBlank() ? null : v;
  }

  private void showSuccess() {
    root.removeAll();
    H2 title = new H2("✓ Connection authorised");
    Paragraph hint = new Paragraph(
        "ChronoGrid received your Google Calendar access token. "
            + "You can close this tab now and return to the wizard.");
    Span detail = new Span(
        "If the wizard does not advance automatically, switch back "
            + "to its tab and click \"Next\".");
    detail.addClassName("chronogrid-secondary-text");
    root.add(title, hint, detail);
  }

  private void showError(String message) {
    root.removeAll();
    H2 title = new H2("✗ Authorisation failed");
    Paragraph hint = new Paragraph(message);
    Span detail = new Span(
        "Close this tab and restart the wizard from the Connection Manager.");
    detail.addClassName("chronogrid-secondary-text");
    root.add(title, hint, detail);
  }
}

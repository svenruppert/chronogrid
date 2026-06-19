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

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.i18n.I18nSupport;
import com.svenruppert.flow.security.bootstrap.BootstrapWiring;
import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.model.Credentials;
import com.svenruppert.flow.security.model.UserDirectoryProvider;
import com.svenruppert.flow.security.services.SessionStoreProvider;
import com.svenruppert.jsentinel.authentication.AuthenticationService;
import com.svenruppert.jsentinel.authorization.LoginView;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.svenruppert.jsentinel.authorization.api.tenant.TenantId;
import com.svenruppert.jsentinel.logout.SubjectId;
import com.svenruppert.jsentinel.session.JSentinelVersion;
import com.svenruppert.jsentinel.session.SessionId;
import com.svenruppert.jsentinel.session.SessionRecord;
import com.svenruppert.jsentinel.session.SessionStatus;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import java.time.Clock;
import java.time.Instant;

import static com.svenruppert.flow.views.AppLoginView.NAV;

/**
 * Login route. Extends the framework's pre-built form
 * ({@code username / password / remember-me / sign-in}); the four
 * overrides wire credential validation, post-login navigation, and
 * failure handling.
 */
@Route(NAV)
public class AppLoginView
    extends LoginView
    implements HasLogger, BeforeEnterObserver, I18nSupport {

  public static final String NAV = "login";

  private static final String K_CREDENTIALS_REJECTED = "login.error.credentialsRejected";

  private final AuthenticationService<Credentials, AppUser> authenticationService
      = JSentinelServiceResolver.authenticationService();

  public AppLoginView() {
    // jSentinel's LoginView doesn't bind Enter to the login button.
    // Wire it explicitly: pressing Enter anywhere inside this view
    // fires a synthetic click on the login button, which then goes
    // through the same validate() / checkCredentials() flow as a
    // real mouse click — including drift-detection snapshot and
    // session rotation.
    Shortcuts.addShortcutListener(this,
        this::clickLoginButton, Key.ENTER).listenOn(this);
  }

  private void clickLoginButton() {
    findLoginButton().ifPresent(btn ->
        ComponentUtil.fireEvent(btn, new ClickEvent<>(btn,
            /*fromClient=*/false,
            0, 0, 0, 0, 0, 0,
            false, false, false, false)));
  }

  private java.util.Optional<Button> findLoginButton() {
    return walk(this)
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(b -> b.getId().map(BTN_LOGIN_ID::equals).orElse(false))
        .findFirst();
  }

  private static java.util.stream.Stream<Component> walk(Component root) {
    return java.util.stream.Stream.concat(
        java.util.stream.Stream.of(root),
        root.getChildren().flatMap(AppLoginView::walk));
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    if (BootstrapWiring.instance().stateService().bootstrapRequired()) {
      event.forwardTo(SetupView.class);
    }
  }

  @Override
  public boolean checkCredentials() {
    Credentials credentials = new Credentials(username(), password());
    boolean ok = authenticationService.checkCredentials(credentials);
    if (ok) {
      UserDirectoryProvider.directory().findByCredentials(credentials).ifPresent(user -> {
        SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);
        recordSession(user);
      });
    }
    return ok;
  }

  @Override
  public void reactOnFailedLogin() {
    logger().info("Login rejected — invalid credentials");
    Notification.show(tr(K_CREDENTIALS_REJECTED, "Credentials not accepted."));
  }

  @Override
  public void navigateToApp() {
    UI.getCurrent().navigate(DashboardView.class);
  }

  private static void recordSession(AppUser user) {
    try {
      VaadinSession vaadin = VaadinSession.getCurrent();
      if (vaadin == null) return;
      WrappedSession wrapped = vaadin.getSession();
      String sessionId = wrapped == null ? null : wrapped.getId();
      if (sessionId == null) return;
      Instant now = Instant.now(Clock.systemUTC());
      SessionStoreProvider.sessionStore().save(new SessionRecord(
          SessionId.of(sessionId),
          SubjectId.of(user.id().toString()),
          TenantId.DEFAULT,
          now, now,
          JSentinelVersion.INITIAL,
          SessionStatus.ACTIVE));
    } catch (RuntimeException ignored) {
      // session bookkeeping must not block login
    }
  }
}

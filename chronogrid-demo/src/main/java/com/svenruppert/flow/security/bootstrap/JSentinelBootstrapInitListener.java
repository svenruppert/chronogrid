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

package com.svenruppert.flow.security.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.SetupView;
import com.svenruppert.jsentinel.authentication.AuthenticationService;
import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.dx.runtime.JSentinelRuntime;
import com.svenruppert.jsentinel.dx.vaadin.bootstrap.VaadinSecurity;
import com.svenruppert.jsentinel.starter.profile.VaadinJSentinelStarter;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Layer-1 entry point — runs the fluent {@link VaadinSecurity#bootstrap()}
 * chain once per JVM at Vaadin service init.
 *
 * <p>Sub-aspect configuration (audit, sessions, credentials) is
 * delegated to every registered {@link BootstrapExtension} via
 * {@link BootstrapBuilder#apply(com.svenruppert.jsentinel.dx.vaadin.bootstrap.VaadinJSentinelBootstrap)}.
 * That makes higher layers — persistence, hardening, future MFA /
 * multi-tenant — additive: each ships one {@link BootstrapExtension}
 * registered via {@code META-INF/services/} + {@link BootstrapExtension#SERVICE_NAME}.
 * <strong>This listener never has to change.</strong>
 *
 * <p>One project-specific concern stays inline: a per-UI
 * {@code BeforeEnter} forwarder diverts every navigation to
 * {@code /setup} while the bootstrap flow is uninitialised. The
 * token-generation + token-file write happen inside
 * {@link BootstrapWiring#build()}, triggered by the persistence
 * layer's {@code <clinit>} the first time
 * {@code PersistenceBootstrapExtension} is loaded.
 *
 * <p>Registered via
 * {@code META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener}.
 */
public class JSentinelBootstrapInitListener
    implements VaadinServiceInitListener, HasLogger {

  private static final AtomicBoolean DONE = new AtomicBoolean();

  @Override
  public void serviceInit(ServiceInitEvent event) {
    event.getSource().addUIInitListener(uiInit ->
        uiInit.getUI().addBeforeEnterListener(enter -> {
          if (enter.getNavigationTarget() == SetupView.class) return;
          if (BootstrapWiring.instance().stateService().bootstrapRequired()) {
            logger().info("Forwarding navigation to {} → /setup (bootstrap required)",
                enter.getNavigationTarget().getSimpleName());
            enter.forwardTo(SetupView.class);
          }
        }));

    if (!DONE.compareAndSet(false, true)) {
      return;
    }
    AuthenticationService<?, ?> authn = ServiceLoader.load(AuthenticationService.class)
        .findFirst().orElse(null);
    AuthorizationService<?> authz = ServiceLoader.load(AuthorizationService.class)
        .findFirst().orElse(null);
    if (authn == null || authz == null) {
      // Fail fast — running an app with one of these missing means
      // every request silently bypasses jSentinel, which is the worst
      // possible failure mode for a security stack. Log the diagnosis
      // and abort Vaadin init so the deployment surfaces immediately.
      String diagnosis = String.format(
          "jSentinel bootstrap requires both AuthenticationService "
              + "and AuthorizationService via SPI. Found: "
              + "AuthenticationService=%s, AuthorizationService=%s. "
              + "Check the @JSentinelAutoService annotation processor "
              + "is wired in maven-compiler-plugin's "
              + "annotationProcessorPaths, and that "
              + "target/classes/META-INF/services/com.svenruppert.jsentinel."
              + "{authentication.AuthenticationService,authorization.api."
              + "AuthorizationService} exist.",
          authn == null ? "<missing>" : authn.getClass().getName(),
          authz == null ? "<missing>" : authz.getClass().getName());
      logger().error(diagnosis);
      throw new IllegalStateException(diagnosis);
    }

    JSentinelRuntime runtime = BootstrapBuilder.apply(
        VaadinSecurity.bootstrap()
            .use(VaadinJSentinelStarter.developmentDefaults())
            .authentication(authn)
            .authorization(authz)
            .loginRoute("login")
            .stepUpRoute("step-up")
    ).install();
    logger().info("jSentinel runtime initialised:\n{}", runtime.log());
  }
}

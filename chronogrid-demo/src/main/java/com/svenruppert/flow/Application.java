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

package com.svenruppert.flow;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.vaadin.nano.CoreUIServiceJava;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.eclipse.jetty.server.Server;

/**
 * Embedded-Jetty launcher backed by {@code nano-vaadin-jetty}. Boots the
 * app from a single {@code java -jar} call — an alternative to the
 * default WAR + {@code jetty:run} build.
 *
 * <p>Inside the fat-jar produced by the {@code _shadejar} profile,
 * Vaadin's own {@code RouteRegistryInitializer} discovers every
 * {@code @Route} class via Jetty's annotation scanner, so the launcher
 * starts the server without handing routes in explicitly. {@code
 * nano-vaadin-jetty}'s {@code scanForRoutes(...)} helper is only needed
 * for IDE / {@code mvn exec:java} runs where classes live in
 * {@code target/classes} rather than inside a jar.
 *
 * <p>{@code CoreUIServiceJava.startServer(...)} returns a
 * {@code Result<Server, Exception>}: success carries the running
 * Jetty {@link Server} and the launcher blocks on
 * {@code server.join()} until shutdown; failure logs the cause and
 * exits non-zero.
 *
 * <p>Host and port can be overridden via {@code APP_HOST} / {@code APP_PORT}
 * environment variables or the matching {@code app.host} / {@code app.port}
 * system properties; defaults are {@code 127.0.0.1:8080}.
 */
public final class Application implements HasLogger {

  private static final String DEFAULT_HOST = "127.0.0.1";
  private static final int DEFAULT_PORT = 8080;

  private Application() {
  }

  public static void main(String[] args) {
    new Application().launch();
  }

  private void launch() {
    String host = resolve("app.host", "APP_HOST", DEFAULT_HOST);
    int port = resolvePort(DEFAULT_PORT);

    CoreUIServiceJava.startServer(host, port)
        .peek(server -> joinAndShutdown(server, host, port))
        .peekFailure(cause -> abortOnStartupFailure(host, port, cause));
  }

  @SuppressFBWarnings(value = "DM_EXIT",
      justification = "intentional — main-class launcher exits non-zero so a process supervisor sees the failure")
  private void abortOnStartupFailure(String host, int port, Exception cause) {
    logger().error("Failed to start embedded Jetty on {}:{}", host, port, cause);
    System.exit(1);
  }

  private void joinAndShutdown(Server server, String host, int port) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.stop();
        logger().info("Embedded Jetty stopped");
      } catch (Exception e) {
        logger().warn("Error during Jetty shutdown", e);
      }
    }, "nano-vaadin-shutdown"));
    logger().info("Embedded Jetty serving http://{}:{}/", host, port);
    try {
      server.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger().warn("Server thread interrupted while joining", e);
    }
  }

  private static String resolve(String systemProperty, String envVariable, String fallback) {
    String fromSystem = System.getProperty(systemProperty);
    if (fromSystem != null && !fromSystem.isBlank()) return fromSystem;
    String fromEnv = System.getenv(envVariable);
    if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
    return fallback;
  }

  private static int resolvePort(int fallback) {
    String raw = resolve("app.port", "APP_PORT", Integer.toString(fallback));
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}

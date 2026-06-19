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

/**
 * Dev launcher tailored to the Calendar / CalDAV demo. Pre-wires
 * {@code app.caldav.baseUri} so {@code CalendarServiceProvider}
 * points at the local {@code caldav-testbench} on
 * {@code http://127.0.0.1:5232/calendars/personal/} — the URL
 * {@code start-caldav-dev-server.sh} prints on startup — and then
 * delegates to {@link Application}, which boots the embedded
 * nano-vaadin-jetty server.
 *
 * <p>Run order for the demo:
 *
 * <ol>
 *   <li>Terminal 1: {@code ./start-caldav-dev-server.sh}
 *       (caldav-testbench on :5232).</li>
 *   <li>Terminal 2: {@code ./start-vaadin-demo.sh} — the wrapper
 *       runs {@code mvn exec:java} with
 *       {@code -Dexec.classpathScope=test} so the {@code provided}-scope
 *       {@code jakarta.servlet-api} is on the classpath. Without that
 *       scope the embedded Jetty fails with
 *       {@code NoClassDefFoundError: jakarta/servlet/Servlet}.
 *       The Vaadin app comes up on {@code http://127.0.0.1:8080/}
 *       and the Calendar view talks to the testbench out of the box.</li>
 * </ol>
 *
 * <p>An explicit {@code -Dapp.caldav.baseUri=…} on the command line
 * still wins — the launcher only fills in the default when nothing
 * was passed. Host / port honour {@code app.host} / {@code app.port}
 * the same way {@link Application} does. IDE runs work too, provided
 * the run configuration includes {@code provided}-scope dependencies
 * on the classpath (IntelliJ does this by default; Eclipse / VS Code
 * may need the option enabled explicitly).
 */
public final class CalDavDemo implements HasLogger {

  public static final String CALDAV_BASE_URI_PROPERTY = "app.caldav.baseUri";
  public static final String DEFAULT_CALDAV_BASE_URI =
      "http://127.0.0.1:5232/calendars/personal/";

  private CalDavDemo() {
  }

  public static void main(String[] args) {
    new CalDavDemo().launch(args);
  }

  private void launch(String[] args) {
    String effective = wireDefaultsIfMissing();
    if (DEFAULT_CALDAV_BASE_URI.equals(effective)) {
      logger().info(
          "CalDAV demo wiring {} = {} — make sure ./start-caldav-dev-server.sh "
              + "is running in another terminal.",
          CALDAV_BASE_URI_PROPERTY, effective);
    } else {
      logger().info("CalDAV demo respecting explicit {} = {}",
          CALDAV_BASE_URI_PROPERTY, effective);
    }
    Application.main(args);
  }

  /**
   * Reads {@link #CALDAV_BASE_URI_PROPERTY}; if it is unset or blank,
   * installs {@link #DEFAULT_CALDAV_BASE_URI} as a side effect. Returns
   * whichever value the system property holds afterwards.
   */
  public static String wireDefaultsIfMissing() {
    String existing = System.getProperty(CALDAV_BASE_URI_PROPERTY);
    if (existing == null || existing.isBlank()) {
      System.setProperty(CALDAV_BASE_URI_PROPERTY, DEFAULT_CALDAV_BASE_URI);
      return DEFAULT_CALDAV_BASE_URI;
    }
    return existing;
  }
}

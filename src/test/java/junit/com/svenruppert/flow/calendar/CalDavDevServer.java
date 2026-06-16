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

package junit.com.svenruppert.flow.calendar;

import com.svenruppert.caldav.server.CalDavMode;
import com.svenruppert.caldav.server.CalDavServer;
import com.svenruppert.caldav.server.CalDavServerConfig;
import com.svenruppert.caldav.testsupport.InteractionLog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Long-running dev launcher for the caldav-testbench. Starts the
 * in-memory CalDAV server on a fixed port (default 5232, override via
 * {@code -Dcaldav.dev.port=…}) seeded with one calendar collection
 * named {@code personal}, and parks until interrupted (Ctrl+C).
 *
 * <p>Lives under {@code src/test/java} because
 * {@code caldav-testbench} is a test-scope dependency — it must not
 * ship inside the production WAR. Invoke it from a second terminal
 * via:
 *
 * <pre>
 * ./mvnw exec:java \
 *        -Dexec.classpathScope=test \
 *        -Dexec.mainClass=junit.com.svenruppert.flow.calendar.CalDavDevServer
 * </pre>
 *
 * <p>Then start the Vaadin app in the first terminal with the
 * matching backend URI:
 *
 * <pre>
 * ./mvnw -Dapp.caldav.baseUri=http://127.0.0.1:5232/calendars/personal/
 * </pre>
 */
public final class CalDavDevServer {

  private static final int DEFAULT_PORT = 5232;
  private static final String DEFAULT_CALENDAR = "personal";

  private CalDavDevServer() {
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    int port = resolvePort();
    String calendar = System.getProperty("caldav.dev.calendar", DEFAULT_CALENDAR);

    CalDavServerConfig config = CalDavServerConfig.builder()
        .port(port)
        .calendarIds(List.of(calendar))
        .mode(CalDavMode.LENIENT)
        .interactionLog(new InteractionLog())
        .build();

    CalDavServer server = new CalDavServer(config);
    server.start();

    int actualPort = server.port();
    System.out.println("─────────────────────────────────────────────");
    System.out.println(" caldav-testbench running");
    System.out.println(" base URI   : " + server.baseUri());
    System.out.println(" calendar   : " + calendar);
    System.out.println(" collection : http://127.0.0.1:" + actualPort
        + "/calendars/" + calendar + "/");
    System.out.println();
    System.out.println(" point the Vaadin app at it:");
    System.out.println("   ./mvnw -Dapp.caldav.baseUri=http://127.0.0.1:" + actualPort
        + "/calendars/" + calendar + "/");
    System.out.println();
    System.out.println(" Ctrl+C to stop.");
    System.out.println("─────────────────────────────────────────────");

    CountDownLatch shutdown = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println();
      System.out.println(" stopping caldav-testbench…");
      server.stop();
      shutdown.countDown();
    }, "caldav-dev-shutdown"));

    shutdown.await();
  }

  private static int resolvePort() {
    String raw = System.getProperty("caldav.dev.port",
        Integer.toString(DEFAULT_PORT));
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ignored) {
      return DEFAULT_PORT;
    }
  }
}

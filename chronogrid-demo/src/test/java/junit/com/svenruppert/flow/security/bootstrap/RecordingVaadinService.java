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

package junit.com.svenruppert.flow.security.bootstrap;

import com.vaadin.flow.server.PwaRegistry;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.UIInitListener;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal hand-rolled {@link VaadinService} stub. Captures every
 * {@link #addUIInitListener(UIInitListener)} call so listener tests
 * can observe what {@code serviceInit(event)} registered.
 *
 * <p>All other abstract methods return harmless defaults — the
 * listeners under test don't read any of them.
 */
final class RecordingVaadinService extends VaadinService {

  final List<UIInitListener> captured = new ArrayList<>();

  @Override
  public Registration addUIInitListener(UIInitListener listener) {
    captured.add(listener);
    return () -> captured.remove(listener);
  }

  @Override protected RouteRegistry getRouteRegistry() { return null; }
  @Override protected PwaRegistry getPwaRegistry() { return null; }
  @Override public String getContextRootRelativePath(VaadinRequest r) { return ""; }
  @Override public String getMimeType(String path) { return null; }
  @Override protected boolean requestCanCreateSession(VaadinRequest r) { return false; }
  @Override public String getServiceName() { return "test-service"; }
  @Override public String getMainDivId(VaadinSession s, VaadinRequest r) { return "main"; }
  @Override public URL getStaticResource(String s) { return null; }
  @Override public URL getResource(String s) { return null; }
  @Override public InputStream getResourceAsStream(String s) { return null; }
  @Override public String resolveResource(String s) { return s; }
  @Override protected VaadinContext constructVaadinContext() { return null; }
}

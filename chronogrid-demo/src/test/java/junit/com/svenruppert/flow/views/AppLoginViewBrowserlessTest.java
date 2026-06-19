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

package junit.com.svenruppert.flow.views;

import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.SetupView;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.flow.component.Component;
import junit.com.svenruppert.flow.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("AppLoginView — navigation + route constants")
class AppLoginViewBrowserlessTest extends BrowserlessTest {

  @BeforeEach
  void seedAdminSoBootstrapNotRequired() {
    TestSupport.seedAdminAndResetBootstrap();
  }

  @Test
  @DisplayName("NAV constant is 'login' — guards against route drift")
  void navConstant() {
    assertEquals("login", AppLoginView.NAV);
  }

  @Test
  @DisplayName("navigating to /login lands on AppLoginView when bootstrap not required")
  void navigateLandsOnAppLoginView() {
    Component current = navigate(AppLoginView.class);
    assertNotNull(current);
    assertEquals(AppLoginView.class, current.getClass(),
        "with an admin seeded, /login must NOT forward to /setup");
  }

  @Test
  @DisplayName("SetupView.NAV ('setup') is the forwarder target")
  void setupViewNavConstant() {
    assertEquals("setup", SetupView.NAV);
  }
}

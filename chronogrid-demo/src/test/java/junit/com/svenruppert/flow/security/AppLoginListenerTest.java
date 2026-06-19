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

package junit.com.svenruppert.flow.security;

import com.svenruppert.flow.security.AppLoginListener;
import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.DashboardView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("AppLoginListener — wiring of login + default navigation targets")
class AppLoginListenerTest {

  private final AppLoginListener listener = new AppLoginListener();

  @Test
  @DisplayName("loginNavigationTarget points at AppLoginView")
  void loginTargetIsAppLoginView() {
    assertSame(AppLoginView.class, listener.loginNavigationTarget());
  }

  @Test
  @DisplayName("defaultNavigationTarget points at DashboardView")
  void defaultTargetIsDashboardView() {
    assertSame(DashboardView.class, listener.defaultNavigationTarget());
  }

  @Test
  @DisplayName("login and default targets are distinct — kills swap mutants")
  void loginAndDefaultTargetsDiffer() {
    assertNotEquals(listener.loginNavigationTarget(),
        (Class<?>) listener.defaultNavigationTarget());
  }

  @Test
  @DisplayName("notARestrictedTarget does not throw on a regular class")
  void notARestrictedTargetIsNoThrow() {
    listener.notARestrictedTarget(Object.class);
    // arriving here is the assertion — covers the log-and-return branch
    assertNotNull(listener);
  }

  @Test
  @DisplayName("the two target methods are pure: identical results on consecutive calls")
  void targetsArePure() {
    assertSame(listener.loginNavigationTarget(), listener.loginNavigationTarget());
    assertEquals(listener.defaultNavigationTarget(), listener.defaultNavigationTarget());
  }
}

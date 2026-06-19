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

package com.svenruppert.flow.security;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.DashboardView;
import com.svenruppert.jsentinel.authorization.LoginListener;
import com.svenruppert.jsentinel.authorization.LoginView;
import com.vaadin.flow.component.Component;

/**
 * Wires the framework's authentication-phase navigation listener to
 * the application's login and default route classes. Registered via
 * {@code META-INF/services/com.svenruppert.jsentinel.authorization.LoginListener}.
 */
public class AppLoginListener extends LoginListener<AppUser> {

  @Override
  public void notARestrictedTarget(Class<?> navigationTarget) {
    logger().info("Unrestricted navigation target — no login required: {}",
        navigationTarget.getSimpleName());
  }

  @Override
  public Class<? extends LoginView> loginNavigationTarget() {
    return AppLoginView.class;
  }

  @Override
  public Class<? extends Component> defaultNavigationTarget() {
    return DashboardView.class;
  }
}

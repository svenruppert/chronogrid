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
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import jakarta.servlet.ServletException;

/**
 * Vaadin servlet bootstrap for the application.
 *
 * <p>Mapping + init params are configured in {@code WEB-INF/web.xml},
 * <strong>not</strong> via {@code @WebServlet}. The descriptor-based
 * setup avoids a "multiple servlets map to /*" conflict at Jetty
 * startup — annotation-and-descriptor combinations are not deduplicated
 * by the container.
 *
 * <p>One init param matters for runtime correctness:
 * {@code i18n.provider = com.svenruppert.flow.i18n.AppI18NProvider}.
 * Without it, Vaadin V25 falls back to {@code DefaultI18NProvider} and
 * the LocaleSwitcher silently always returns German on a German JVM.
 */
public class AppServlet
    extends VaadinServlet
    implements HasLogger {

  @Override
  protected void servletInitialized()
      throws ServletException {
    super.servletInitialized();
    logger().info("servletInitialized — i18n.provider init param = "
        + getServletConfig().getInitParameter("i18n.provider"));
    VaadinServletService service = getService();
    service.addSessionInitListener(e -> e.getSession().setErrorHandler(
        err -> err.getThrowable().printStackTrace()));
  }
}

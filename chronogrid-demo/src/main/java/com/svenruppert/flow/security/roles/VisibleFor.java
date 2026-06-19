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

package com.svenruppert.flow.security.roles;

import com.svenruppert.jsentinel.authorization.annotations.JSentinelAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Restriction annotation. Applied to a Vaadin route view
 * ({@code @VisibleFor(ADMIN)}), it is picked up by
 * {@code JSentinelAnnotationScanner} and evaluated by
 * {@link RoleAccessEvaluator} during {@code BeforeEnter}.
 *
 * <p>{@code value()} is any-of: at least one of the listed roles must
 * be present on the subject for navigation to proceed.
 */
@Retention(RetentionPolicy.RUNTIME)
@JSentinelAnnotation(RoleAccessEvaluator.class)
public @interface VisibleFor {
  AuthorizationRole[] value();
}

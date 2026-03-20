/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.i18n;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for determining the current Locale.
 * 用于确定当前区域设置的策略接口。
 *
 * <p>A LocaleContext instance can be associated with a thread
 * via the LocaleContextHolder class.
 * 可以通过 LocaleContextHolder 类将 LocaleContext 实例与线程关联起来。
 *
 * @author Juergen Hoeller
 * @since 1.2
 * @see LocaleContextHolder#getLocale()
 * @see TimeZoneAwareLocaleContext
 */
public interface LocaleContext {

	/**
	 * Return the current Locale, which can be fixed or determined dynamically,
	 * depending on the implementation strategy.
	 * 返回当前区域设置，该设置可以是固定的，也可以是动态确定的，具体取决于实现策略。
	 * @return the current Locale, or {@code null} if no specific Locale associated
	 */
	@Nullable Locale getLocale();

}

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

package org.springframework.context.annotation;

import org.springframework.beans.factory.BeanRegistrar;

/**
 * Common interface for annotation config application contexts,
 * defining {@link #register} and {@link #scan} methods.
 * 注解配置应用程序上下文的通用接口，定义 {@link #register} 和 {@link #scan} 方法。
 *
 * @author Juergen Hoeller
 * @since 4.1
 */
public interface AnnotationConfigRegistry {

	/**
	 * Invoke the given registrars for registering their beans with this
	 * application context.
	 * <p>This can be used to register custom beans without inferring
	 * annotation-based characteristics for primary/fallback/lazy-init,
	 * rather specifying those programmatically if needed.
	 *
	 * 调用指定的注册器，将它们的 bean 注册到此应用程序上下文中。
	 * 这可用于注册自定义 bean，而无需推断基于注解的主 bean/备用 bean/延迟初始化 bean 特性，而是可以根据需要以编程方式指定这些特性。
	 *
	 * @param registrars one or more {@link BeanRegistrar} instances
	 * @since 7.0
	 * @see #register(Class[])
	 */
	void register(BeanRegistrar... registrars);

	/**
	 * Register one or more component classes to be processed, inferring
	 * annotation-based characteristics for primary/fallback/lazy-init
	 * just like for scanned component classes.
	 * <p>Calls to {@code register} are idempotent; adding the same
	 * component class more than once has no additional effect.
	 *
	 * 注册一个或多个待处理的组件类，并像处理已扫描的组件类一样，根据注解推断主组件类/备用组件类/延迟初始化组件类的特征。注册调用是幂等的；多次添加同一个组件类不会产生任何额外效果。
	 *
	 * @param componentClasses one or more component classes,
	 * for example, {@link Configuration @Configuration} classes
	 * @see #scan(String...)
	 */
	void register(Class<?>... componentClasses);

	/**
	 * Perform a scan within the specified base packages.
	 * 在指定的基础软件包内执行扫描
	 *
	 * @param basePackages the packages to scan for component classes
	 * @see #register(Class[])
	 */
	void scan(String... basePackages);

}

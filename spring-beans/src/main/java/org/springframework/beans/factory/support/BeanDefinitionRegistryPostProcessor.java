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

package org.springframework.beans.factory.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Extension to the standard {@link BeanFactoryPostProcessor} SPI, allowing for
 * the registration of further bean definitions <i>before</i> regular
 * BeanFactoryPostProcessor detection kicks in. In particular,
 * BeanDefinitionRegistryPostProcessor may register further bean definitions
 * which in turn define BeanFactoryPostProcessor instances.
 * <p>扩展了标准{@link BeanFactoryPostProcessor} SPI，允许在常规{@code BeanFactoryPostProcessor}检测开始之前注册进一步的bean定义。
 * 特别是，{@code BeanDefinitionRegistryPostProcessor}可以注册进一步的bean定义，这些定义反过来定义{@code BeanFactoryPostProcessor}实例。
 *
 * @author Juergen Hoeller
 * @since 3.0.1
 * @see org.springframework.context.annotation.ConfigurationClassPostProcessor
 */
public interface BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean definition registry after its
	 * standard initialization. All regular bean definitions will have been loaded,
	 * but no beans will have been instantiated yet. This allows for adding further
	 * bean definitions before the next post-processing phase kicks in.
	 * 在标准初始化之后修改应用程序上下文的内部bean定义注册中心。将加载所有常规bean定义，但还没有实例化任何bean。这允许在下一个后处理阶段开始之前添加进一步的bean定义。
	 *
	 * @param registry the bean definition registry used by the application context
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;

	/**
	 * Empty implementation of {@link BeanFactoryPostProcessor#postProcessBeanFactory}
	 * since custom {@code BeanDefinitionRegistryPostProcessor} implementations will
	 * typically only provide a {@link #postProcessBeanDefinitionRegistry} method.
	 * <p>空的实现{@link BeanFactoryPostProcessor#postProcessBeanFactory}，因为自定义的{@code BeanDefinitionRegistryPostProcessor}实现通常只提供一个{@link #postProcessBeanDefinitionRegistry}方法。
	 * @since 6.1
	 */
	@Override
	default void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
	}

}

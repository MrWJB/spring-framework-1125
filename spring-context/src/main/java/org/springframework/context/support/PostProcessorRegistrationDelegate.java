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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionValueResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 * AbstractApplicationContext后处理器处理的委托类。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		// 警告：虽然看起来可以很容易地重构此方法的主体以避免使用多个循环和多个列表，但使用多个列表和多次传递处理器名称是有意的。我们必须确保我们遵守了priityorordered和Ordered处理器的合同。
		// 具体来说，我们一定不能导致处理器被实例化（通过getBean（）调用）或以错误的顺序在ApplicationContext中注册。
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22
		// 在提交修改此方法的PR之前，请查看所有被拒绝的PostProcessorRegistrationDelegate相关PR，
		// 确保你的提议不会引入破坏性变更：
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 首先调用BeanDefinitionRegistryPostProcessors（如果有的话）。
		Set<String> processedBeans = new HashSet<>();

		// 判断beanFactory是否实现了BeanDefinitionRegistry接口
		if (beanFactory instanceof BeanDefinitionRegistry registry) {
			// 创建regularPostProcessors列表
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// 创建registryProcessors列表
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 遍历beanFactoryPostProcessors
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// 判断postProcessor是否实现了BeanDefinitionRegistryPostProcessor接口
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor registryProcessor) {
					// 调用registryProcessor的postProcessBeanDefinitionRegistry方法
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					// 将registryProcessor添加到registryProcessors中
					registryProcessors.add(registryProcessor);
				}
				else {
					// 将postProcessor添加到regularPostProcessors中
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 这里不要初始化FactoryBeans：我们需要保留所有未初始化的常规bean，以便让bean工厂后处理程序应用于它们！
			// 将BeanDefinitionRegistryPostProcessors（实现了PriorityOrdered， Ordered和其他）分开。
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 首先，调用BeanDefinitionRegistryPostProcessors来实现PriorityOrdered。
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			// 遍历postProcessorNames
			for (String ppName : postProcessorNames) {
				// 判断ppName是否实现了PriorityOrdered接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 将实现了PriorityOrdered的BeanDefinitionRegistryPostProcessor添加到currentRegistryProcessors中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将ppName添加到processedBeans中
					processedBeans.add(ppName);
				}
			}
			// 对currentRegistryProcessors进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 将currentRegistryProcessors添加到registryProcessors中
			registryProcessors.addAll(currentRegistryProcessors);

			// 调用BeanDefinitionRegistryPostProcessors的postProcessBeanDefinitionRegistry方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			// 清空currentRegistryProcessors
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 接下来，调用实现Ordered的BeanDefinitionRegistryPostProcessors。
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			// 遍历postProcessorNames
			for (String ppName : postProcessorNames) {
				// 判断ppName是否实现了Ordered接口
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					// 将实现了Ordered的BeanDefinitionRegistryPostProcessor添加到currentRegistryProcessors中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将ppName添加到processedBeans中
					processedBeans.add(ppName);
				}
			}
			// 对currentRegistryProcessors进行排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 将currentRegistryProcessors添加到registryProcessors中
			registryProcessors.addAll(currentRegistryProcessors);	

			// 调用BeanDefinitionRegistryPostProcessors的postProcessBeanDefinitionRegistry方法	
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			// 清空currentRegistryProcessors
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 最后，调用所有其他BeanDefinitionRegistryPostProcessors，直到没有其他BeanDefinitionRegistryPostProcessors出现为止。
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				// 遍历postProcessorNames
				for (String ppName : postProcessorNames) {
					// 判断ppName是否实现了BeanDefinitionRegistryPostProcessor接口
					if (!processedBeans.contains(ppName)) {
						// 将实现了BeanDefinitionRegistryPostProcessor接口的bean添加到currentRegistryProcessors中
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						// 将ppName添加到processedBeans中
						processedBeans.add(ppName);
						// 设置reiterate为true
						reiterate = true;
					}
				}
				// 对currentRegistryProcessors进行排序
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				// 将currentRegistryProcessors添加到registryProcessors中
				registryProcessors.addAll(currentRegistryProcessors);
				// 调用BeanDefinitionRegistryPostProcessors的postProcessBeanDefinitionRegistry方法
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
				// 清空currentRegistryProcessors
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 现在，调用到目前为止处理过的所有处理器的postProcessBeanFactory回调。
			// BeanDefinitionRegistryPostProcessor的postProcessBeanFactory回调
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			// BeanFactoryPostProcessor的postProcessBeanFactory回调
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			// 调用注册到上下文实例的工厂处理器。
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 这里不要初始化FactoryBeans：我们需要保留所有未初始化的常规bean，以便让bean工厂后处理程序应用于它们！
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 将BeanFactoryPostProcessors分为实现了PriorityOrdered，Ordered和其他。
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 创建orderedPostProcessorNames列表
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 创建nonOrderedPostProcessorNames列表
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历postProcessorNames
		for (String ppName : postProcessorNames) {
			// 判断ppName是否已经处理过
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
				// 跳过 - 已经在第一阶段处理过了
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 将实现了PriorityOrdered的BeanFactoryPostProcessor添加到priorityOrderedPostProcessors中
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				// 将ppName添加到orderedPostProcessorNames中
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 将ppName添加到nonOrderedPostProcessorNames中
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 首先，调用实现了PriorityOrdered的BeanFactoryPostProcessors。
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 调用实现了PriorityOrdered的BeanFactoryPostProcessors。
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// 接下来，调用实现了Ordered的BeanFactoryPostProcessors。
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		// 遍历orderedPostProcessorNames		
		for (String postProcessorName : orderedPostProcessorNames) {
			// 将实现了Ordered的BeanFactoryPostProcessor添加到orderedPostProcessors中
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 对orderedPostProcessors进行排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 调用实现了Ordered的BeanFactoryPostProcessors。
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 最后，调用所有其他BeanFactoryPostProcessors。
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		// 遍历nonOrderedPostProcessorNames
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			// 将实现了BeanFactoryPostProcessor接口的bean添加到nonOrderedPostProcessors中
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 调用所有其他BeanFactoryPostProcessors。
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, for example, replacing placeholders in values...
		// 清除缓存的合并bean定义，因为后处理器可能已经修改了原始元数据，例如，替换值中的占位符...
		beanFactory.clearMetadataCache();
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 * 注册给定的BeanPostProcessor bean。
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		// 警告：虽然看起来可以很容易地重构此方法的主体以避免使用多个循环和多个列表，但使用多个列表和多次传递处理器名称是有意的。
		// 我们必须确保我们遵守了PriorityOrdered和Ordered处理器的合同。具体来说，我们一定不能导致处理器被实例化（通过getBean（）调用）或以错误的顺序在ApplicationContext中注册。
		// 在提交拉取请求（PR）以更改此方法之前，请查看所有涉及更改PostProcessorRegistrationDelegate的被拒绝PR，
		// 确保提议不会带来破坏性变更：
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs a warn message when a bean is created during BeanPostProcessor instantiation,
		// i.e. when a bean is not eligible for getting processed by all BeanPostProcessors.
		// 注册BeanPostProcessorChecker，在BeanPostProcessor实例化期间创建bean时记录警告消息。
		// 例如，当一个bean没有资格被所有BeanPostProcessors处理时。
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// 添加BeanPostProcessorChecker
		beanFactory.addBeanPostProcessor(
				new BeanPostProcessorChecker(beanFactory, postProcessorNames, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,Ordered, and the rest.
		// 区分实现了PriorityOrdered，Ordered和其他的BeanPostProcessors。
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		// 遍历postProcessorNames
		for (String ppName : postProcessorNames) {
			// 判断ppName是否实现了PriorityOrdered接口
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 将实现了PriorityOrdered的BeanPostProcessor添加到priorityOrderedPostProcessors中
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				// 将pp添加到priorityOrderedPostProcessors中
				priorityOrderedPostProcessors.add(pp);
				// 判断pp是否实现了MergedBeanDefinitionPostProcessor接口
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					// 将实现了MergedBeanDefinitionPostProcessor的BeanPostProcessor添加到internalPostProcessors中
					internalPostProcessors.add(pp);
				}
			}
			// 判断ppName是否实现了Ordered接口
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				// 将ppName添加到orderedPostProcessorNames中
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 将ppName添加到nonOrderedPostProcessorNames中
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		// 首先，注册实现PriorityOrdered的BeanPostProcessors。
		// 对priorityOrderedPostProcessors进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 注册实现PriorityOrdered的BeanPostProcessors。
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		// 接下来，注册实现Ordered的BeanPostProcessors。
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		// 遍历orderedPostProcessorNames
		for (String ppName : orderedPostProcessorNames) {
			// 将实现了Ordered的BeanPostProcessor添加到orderedPostProcessors中
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			// 将pp添加到orderedPostProcessors中
			orderedPostProcessors.add(pp);
			// 判断pp是否实现了MergedBeanDefinitionPostProcessor接口
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				// 将实现了MergedBeanDefinitionPostProcessor的BeanPostProcessor添加到internalPostProcessors中
				internalPostProcessors.add(pp);
			}
		}
		// 对orderedPostProcessors进行排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 注册实现Ordered的BeanPostProcessors。
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		// 现在，注册所有常规BeanPostProcessors
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		// 遍历nonOrderedPostProcessorNames
		for (String ppName : nonOrderedPostProcessorNames) {
			// 将实现了BeanPostProcessor接口的bean添加到nonOrderedPostProcessors中
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			// 将pp添加到nonOrderedPostProcessors中
			nonOrderedPostProcessors.add(pp);
			// 判断pp是否实现了MergedBeanDefinitionPostProcessor接口
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				// 将实现了MergedBeanDefinitionPostProcessor的BeanPostProcessor添加到internalPostProcessors中
				internalPostProcessors.add(pp);
			}
		}
		// 注册所有常规BeanPostProcessors。
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		// 最后，重新注册所有内部BeanPostProcessors。
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 将检测内部bean的后处理器重新注册为ApplicationListeners,将其移动到处理器链的末端（用于拾取代理等）。
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	/**
	 * Load and sort the post-processors of the specified type.
	 * 加载并排序指定类型的后处理器。
	 * @param beanFactory the bean factory to use
	 * @param beanFactory 要使用的BeanFactory
	 * @param beanPostProcessorType the post-processor type
	 * @param beanPostProcessorType 后处理器类型
	 * @param <T> the post-processor type
	 * @param <T> 后处理器类型
	 * @return a list of sorted post-processors for the specified type
	 * @return 指定类型的已排序后处理器列表
	 */
	static <T extends BeanPostProcessor> List<T> loadBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, Class<T> beanPostProcessorType) {

		String[] postProcessorNames = beanFactory.getBeanNamesForType(beanPostProcessorType, true, false);
		List<T> postProcessors = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			postProcessors.add(beanFactory.getBean(ppName, beanPostProcessorType));
		}
		sortPostProcessors(postProcessors, beanFactory);
		return postProcessors;

	}

	/**
	 * Selectively invoke {@link MergedBeanDefinitionPostProcessor} instances
	 * registered in the specified bean factory, resolving bean definitions and
	 * any attributes if necessary as well as any inner bean definitions that
	 * they may contain.
	 * 有选择性地调用指定BeanFactory中注册的{@link MergedBeanDefinitionPostProcessor}，
	 * 按需解析Bean定义、属性以及可能包含的内部Bean定义。
	 * @param beanFactory the bean factory to use
	 * @param beanFactory 要使用的BeanFactory
	 */
	static void invokeMergedBeanDefinitionPostProcessors(DefaultListableBeanFactory beanFactory) {
		new MergedBeanDefinitionPostProcessorInvoker(beanFactory).invokeMergedBeanDefinitionPostProcessors();
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		// 无需排序？
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory dlbf) {
			comparatorToUse = dlbf.getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 * 调用给定的BeanDefinitionRegistryPostProcessor bean。
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

		// 遍历postProcessors
		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			// 记录postProcessor的启动
			StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
					.tag("postProcessor", postProcessor::toString);
			// 调用postProcessor的postProcessBeanDefinitionRegistry方法
			postProcessor.postProcessBeanDefinitionRegistry(registry);
			// 记录postProcessor的结束
			postProcessBeanDefRegistry.end();
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 * 调用给定的BeanFactoryPostProcessor。
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanFactory(beanFactory);
			postProcessBeanFactory.end();
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 * 注册给定的BeanPostProcessor。
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<? extends BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory abstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			// 针对内部的CopyOnWriteArrayList，批量添加更高效
			abstractBeanFactory.addBeanPostProcessors(postProcessors);
		}
		else {
			for (BeanPostProcessor postProcessor : postProcessors) {
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs a warn message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 * BeanPostProcessorChecker在BeanPostProcessor实例化期间发现有Bean被创建时记录告警，
	 * 表示该Bean无法被所有BeanPostProcessor处理。
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final String[] postProcessorNames;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory,
				String[] postProcessorNames, int beanPostProcessorTargetCount) {

			this.beanFactory = beanFactory;
			this.postProcessorNames = postProcessorNames;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isWarnEnabled()) {
					Set<String> bppsInCreation = new LinkedHashSet<>(2);
					for (String bppName : this.postProcessorNames) {
						if (this.beanFactory.isCurrentlyInCreation(bppName)) {
							bppsInCreation.add(bppName);
						}
					}
					if (bppsInCreation.size() == 1) {
						String bppName = bppsInCreation.iterator().next();
						if (this.beanFactory.containsBeanDefinition(bppName) &&
								beanName.equals(this.beanFactory.getBeanDefinition(bppName).getFactoryBeanName())) {
							logger.warn("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
									"] is not eligible for getting processed by all BeanPostProcessors " +
									"(for example: not eligible for auto-proxying). The currently created " +
									"BeanPostProcessor " + bppsInCreation + " is declared through a non-static " +
									"factory method on that class; consider declaring it as static instead.");
							return bean;
						}
					}
					logger.warn("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying). Is this bean getting eagerly " +
							"injected/applied to a currently created BeanPostProcessor " + bppsInCreation + "? " +
							"Check the corresponding BeanPostProcessor declaration and its dependencies/advisors. " +
							"If this bean does not have to be post-processed, declare it with ROLE_INFRASTRUCTURE.");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == BeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}


	private static final class MergedBeanDefinitionPostProcessorInvoker {

		private final DefaultListableBeanFactory beanFactory;

		private MergedBeanDefinitionPostProcessorInvoker(DefaultListableBeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		private void invokeMergedBeanDefinitionPostProcessors() {
			List<MergedBeanDefinitionPostProcessor> postProcessors = PostProcessorRegistrationDelegate.loadBeanPostProcessors(
					this.beanFactory, MergedBeanDefinitionPostProcessor.class);
			for (String beanName : this.beanFactory.getBeanDefinitionNames()) {
				RootBeanDefinition bd = (RootBeanDefinition) this.beanFactory.getMergedBeanDefinition(beanName);
				Class<?> beanType = resolveBeanType(bd);
				postProcessRootBeanDefinition(postProcessors, beanName, beanType, bd);
				bd.markAsPostProcessed();
			}
			registerBeanPostProcessors(this.beanFactory, postProcessors);
		}

		private void postProcessRootBeanDefinition(List<MergedBeanDefinitionPostProcessor> postProcessors,
				String beanName, Class<?> beanType, RootBeanDefinition bd) {

			BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this.beanFactory, beanName, bd);
			postProcessors.forEach(postProcessor -> postProcessor.postProcessMergedBeanDefinition(bd, beanType, beanName));
			for (PropertyValue propertyValue : bd.getPropertyValues().getPropertyValueList()) {
				postProcessValue(postProcessors, valueResolver, propertyValue.getValue());
			}
			for (ValueHolder valueHolder : bd.getConstructorArgumentValues().getIndexedArgumentValues().values()) {
				postProcessValue(postProcessors, valueResolver, valueHolder.getValue());
			}
			for (ValueHolder valueHolder : bd.getConstructorArgumentValues().getGenericArgumentValues()) {
				postProcessValue(postProcessors, valueResolver, valueHolder.getValue());
			}
		}

		private void postProcessValue(List<MergedBeanDefinitionPostProcessor> postProcessors,
				BeanDefinitionValueResolver valueResolver, @Nullable Object value) {
			if (value instanceof BeanDefinitionHolder bdh &&
					bdh.getBeanDefinition() instanceof AbstractBeanDefinition innerBd) {

				Class<?> innerBeanType = resolveBeanType(innerBd);
				resolveInnerBeanDefinition(valueResolver, innerBd, (innerBeanName, innerBeanDefinition)
						-> postProcessRootBeanDefinition(postProcessors, innerBeanName, innerBeanType, innerBeanDefinition));
			}
			else if (value instanceof AbstractBeanDefinition innerBd) {
				Class<?> innerBeanType = resolveBeanType(innerBd);
				resolveInnerBeanDefinition(valueResolver, innerBd, (innerBeanName, innerBeanDefinition)
						-> postProcessRootBeanDefinition(postProcessors, innerBeanName, innerBeanType, innerBeanDefinition));
			}
			else if (value instanceof TypedStringValue typedStringValue) {
				resolveTypeStringValue(typedStringValue);
			}
		}

		private void resolveInnerBeanDefinition(BeanDefinitionValueResolver valueResolver, BeanDefinition innerBeanDefinition,
				BiConsumer<String, RootBeanDefinition> resolver) {

			valueResolver.resolveInnerBean(null, innerBeanDefinition, (name, rbd) -> {
				resolver.accept(name, rbd);
				return Void.class;
			});
		}

		private void resolveTypeStringValue(TypedStringValue typedStringValue) {
			try {
				typedStringValue.resolveTargetType(this.beanFactory.getBeanClassLoader());
			}
			catch (ClassNotFoundException ignored) {
			}
		}

		private Class<?> resolveBeanType(AbstractBeanDefinition bd) {
			if (!bd.hasBeanClass()) {
				try {
					bd.resolveBeanClass(this.beanFactory.getBeanClassLoader());
				}
				catch (ClassNotFoundException ex) {
					// ignore
					// 忽略处理
				}
			}
			return bd.getResolvableType().toClass();
		}
	}

}

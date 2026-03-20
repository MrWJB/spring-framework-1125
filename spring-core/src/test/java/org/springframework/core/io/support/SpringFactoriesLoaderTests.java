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

package org.springframework.core.io.support;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.assertj.core.extractor.Extractors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.io.support.SpringFactoriesLoader.FactoryInstantiator;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;
import org.springframework.core.log.LogMessage;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link SpringFactoriesLoader}.
 *
 * @author Arjen Poutsma
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Andy Wilkinson
 * @author Madhura Bhave
 */
class SpringFactoriesLoaderTests {

	@BeforeAll
	static void clearCache() {
		SpringFactoriesLoader.cache.clear();
		assertThat(SpringFactoriesLoader.cache).isEmpty();
	}

	@AfterAll
	static void checkCache() {
		assertThat(SpringFactoriesLoader.cache).hasSize(3);
		SpringFactoriesLoader.cache.clear();
	}


	/**
	 * 加载工厂类
	 */
	@Test
	@Deprecated
	void loadFactoryNames() {
		List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(DummyFactory.class, null);
		assertThat(factoryNames).containsExactlyInAnyOrder(MyDummyFactory1.class.getName(), MyDummyFactory2.class.getName());
	}

	/**
	 * 当没有注册的实现返回空列表时加载
	 */
	@Test
	void loadWhenNoRegisteredImplementationsReturnsEmptyList() {
		List<Integer> factories = SpringFactoriesLoader.forDefaultResourceLocation().load(Integer.class);
		assertThat(factories).isEmpty();
	}

	/**
	 * 当重复的注册以正确的顺序呈现返回列表时加载
	 */
	@Test
	void loadWhenDuplicateRegistrationsPresentReturnsListInCorrectOrder() {
		List<DummyFactory> factories = SpringFactoriesLoader.forDefaultResourceLocation().load(DummyFactory.class);
		assertThat(factories).hasExactlyElementsOfTypes(MyDummyFactory1.class, MyDummyFactory2.class);
	}


	/**
	 * 判断类是否为私包
	 */
	@Test
	void loadWhenPackagePrivateFactory() {
		List<DummyPackagePrivateFactory> factories =
				SpringFactoriesLoader.forDefaultResourceLocation().load(DummyPackagePrivateFactory.class);
		assertThat(factories).hasSize(1);
		assertThat(Modifier.isPublic(factories.get(0).getClass().getModifiers())).isFalse();
	}

	/**
	 * 加载不兼容的类型抛出异常时加载
	 */
	@Test
	void loadWhenIncompatibleTypeThrowsException() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SpringFactoriesLoader.forDefaultResourceLocation().load(String.class))
			.withMessageContaining("Unable to instantiate factory class " +
					"[org.springframework.core.io.support.MyDummyFactory1] for factory type [java.lang.String]");
	}


	/**
	 * 加载不兼容的类型返回空列表时，加载日志失败处理程序
	 */
	@Test
	void loadWithLoggingFailureHandlerWhenIncompatibleTypeReturnsEmptyList() {
		Log logger = mock();
		FailureHandler failureHandler = FailureHandler.logging(logger);
		List<String> factories = SpringFactoriesLoader.forDefaultResourceLocation().load(String.class, failureHandler);
		assertThat(factories).isEmpty();
	}

	/**
	 * 在没有默认构造函数时加载带参数解析器
	 */
	@Test
	void loadWithArgumentResolverWhenNoDefaultConstructor() {
		// 参数解析器
		ArgumentResolver resolver = ArgumentResolver.of(String.class, "injected");

		// 通过类加载器加载指定目录下的spring.factories文件，通过load方法加载DummyFactory接口的实现类
		List<DummyFactory> factories = SpringFactoriesLoader.forDefaultResourceLocation(LimitedClassLoader.constructorArgumentFactories)
					.load(DummyFactory.class, resolver);
		// 断言
		assertThat(factories).hasExactlyElementsOfTypes(MyDummyFactory1.class, MyDummyFactory2.class,
				ConstructorArgsDummyFactory.class);
		assertThat(factories).extracting(DummyFactory::getString).containsExactly("Foo", "Bar", "injected");
	}

	/**
	 * 当多个构造函数抛出异常时加载
	 */
	@Test
	void loadWhenMultipleConstructorsThrowsException() {
		// 如果方法传入的参数与of方法传入的类型匹配，那么就返回of方法的value值。
		ArgumentResolver resolver = ArgumentResolver.of(String.class, "injected");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> SpringFactoriesLoader.forDefaultResourceLocation(LimitedClassLoader.multipleArgumentFactories)
							.load(DummyFactory.class, resolver))

				.withMessageContaining("Unable to instantiate factory class " +
						"[org.springframework.core.io.support.MultipleConstructorArgsDummyFactory] for factory type [org.springframework.core.io.support.DummyFactory]")
				.havingRootCause().withMessageContaining("Class [org.springframework.core.io.support.MultipleConstructorArgsDummyFactory] has no suitable constructor");
	}


	/**
	 * 当缺少参数项时，加载日志失败处理程序
	 */
	@Test
	void loadWithLoggingFailureHandlerWhenMissingArgumentDropsItem() {
		Log logger = mock();
		// 失败处理程序
		FailureHandler failureHandler = FailureHandler.logging(logger);
		// 通过指定的类加载器加载spring.factories文件，加载DummyFactory接口的实现类
		List<DummyFactory> factories = SpringFactoriesLoader.forDefaultResourceLocation(LimitedClassLoader.multipleArgumentFactories)
					.load(DummyFactory.class, failureHandler);
		// 断言（hasExactlyElementsOfTypes 方法用于检查集合中的元素是否仅包含指定类型的元素，并且每个类型的元素数量与预期一致。这个方法可以帮助你在测试中验证集合的元素类型分布。）
		assertThat(factories).hasExactlyElementsOfTypes(MyDummyFactory1.class, MyDummyFactory2.class);
	}


	/**
	 * 加载工厂从默认位置加载
	 */
	@Test
	void loadFactoriesLoadsFromDefaultLocation() {
		List<DummyFactory> factories = SpringFactoriesLoader.loadFactories(
				DummyFactory.class, null);
		assertThat(factories).hasExactlyElementsOfTypes(MyDummyFactory1.class, MyDummyFactory2.class);
	}


	/**
	 * 当资源位置不存在时，加载资源位置返回空列表
	 */
	@Test
	void loadForResourceLocationWhenLocationDoesNotExistReturnsEmptyList() {
		List<DummyFactory> factories = SpringFactoriesLoader.forResourceLocation(
				"META-INF/missing/missing-spring.factories").load(DummyFactory.class);
		// 断言
		assertThat(factories).isEmpty();
	}


	/**
	 * 从指定资源位置加载
	 */
	@Test
	void loadForResourceLocationLoadsFactories() {
		List<DummyFactory> factories = SpringFactoriesLoader.forResourceLocation(
				"META-INF/custom/custom-spring.factories").load(DummyFactory.class);
		assertThat(factories).hasExactlyElementsOfTypes(MyDummyFactory1.class);
	}

	/**
	 * 传入默认类加载器和传空（null）使用相同的缓存结果
	 */
	@Test
	void sameCachedResultIsUsedForDefaultClassLoaderAndNullClassLoader() {
		SpringFactoriesLoader forNull = SpringFactoriesLoader.forDefaultResourceLocation(null);
		SpringFactoriesLoader forDefault = SpringFactoriesLoader.forDefaultResourceLocation(ClassUtils.getDefaultClassLoader());
		assertThat(forNull).extracting("factories").isSameAs(Extractors.byName("factories").apply(forDefault));
	}


	/**
	 * 陈旧类装入器与缓存结果一起使用
	 */
	@Test
	void staleClassLoaderIsUsedWithCachedResult() {
		// 默认类加载器
		ClassLoader defaultClassLoader = ClassUtils.getDefaultClassLoader();
		ClassLoader cl1 = new ClassLoader() {
		};

		// 通过默认类加载器加载资源
		SpringFactoriesLoader factories1 = SpringFactoriesLoader.forDefaultResourceLocation(defaultClassLoader);
		assertThat(factories1).extracting("classLoader").isEqualTo(defaultClassLoader);

		// 线程上下文类加载器
		ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			// 设置线程上下文类加载器
			Thread.currentThread().setContextClassLoader(cl1);
			//
			SpringFactoriesLoader factories2 = SpringFactoriesLoader.forDefaultResourceLocation(null);
			assertThat(factories2).extracting("classLoader").isNull();
		}
		finally {
			Thread.currentThread().setContextClassLoader(previousClassLoader);
		}
	}

	@Nested
	class FailureHandlerTests {

		@Test
		void throwingReturnsHandlerThatThrowsIllegalArgumentException() {
			FailureHandler handler = FailureHandler.throwing();
			RuntimeException cause = new RuntimeException();
			assertThatIllegalArgumentException().isThrownBy(() -> handler.handleFailure(
					DummyFactory.class, MyDummyFactory1.class.getName(),
					cause)).withMessageStartingWith("Unable to instantiate factory class").withCause(cause);
		}

		@Test
		void throwingWithFactoryReturnsHandlerThatThrows() {
			FailureHandler handler = FailureHandler.throwing(IllegalStateException::new);
			RuntimeException cause = new RuntimeException();
			assertThatIllegalStateException().isThrownBy(() -> handler.handleFailure(
					DummyFactory.class, MyDummyFactory1.class.getName(),
					cause)).withMessageStartingWith("Unable to instantiate factory class").withCause(cause);
		}

		@Test
		void loggingReturnsHandlerThatLogs() {
			Log logger = mock();
			FailureHandler handler = FailureHandler.logging(logger);
			RuntimeException cause = new RuntimeException();
			handler.handleFailure(DummyFactory.class, MyDummyFactory1.class.getName(), cause);
			verify(logger).trace(isA(LogMessage.class), eq(cause));
		}

		@Test
		void handleMessageReturnsHandlerThatAcceptsMessage() {
			List<Throwable> failures = new ArrayList<>();
			List<String> messages = new ArrayList<>();
			FailureHandler handler = FailureHandler.handleMessage((message, failure) -> {
				failures.add(failure);
				messages.add(message.get());
			});
			RuntimeException cause = new RuntimeException();
			handler.handleFailure(DummyFactory.class, MyDummyFactory1.class.getName(), cause);
			assertThat(failures).containsExactly(cause);
			assertThat(messages).singleElement().asString().startsWith("Unable to instantiate factory class");
		}
	}


	@Nested
	class ArgumentResolverTests {

		@Test
		void ofValueResolvesValue() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		@Test
		void ofValueSupplierResolvesValue() {
			ArgumentResolver resolver = ArgumentResolver.ofSupplied(CharSequence.class, () -> "test");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		@Test
		void fromAdaptsFunction() {
			ArgumentResolver resolver = ArgumentResolver.from(
					type -> CharSequence.class.equals(type) ? "test" : null);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isNull();
		}

		@Test
		void andValueReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(Integer.class, 123);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
		}

		@Test
		void andValueWhenSameTypeReturnsCompositeResolvingFirst() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(CharSequence.class, "ignore");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
		}

		@Test
		void andValueSupplierReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").andSupplied(Integer.class, () -> 123);
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
		}

		@Test
		void andValueSupplierWhenSameTypeReturnsCompositeResolvingFirst() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").andSupplied(CharSequence.class, () -> "ignore");
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
		}

		@Test
		void andResolverReturnsComposite() {
			ArgumentResolver resolver = ArgumentResolver.of(CharSequence.class, "test").and(Integer.class, 123);
			resolver = resolver.and(ArgumentResolver.of(CharSequence.class, "ignore").and(Long.class, 234L));
			assertThat(resolver.resolve(CharSequence.class)).isEqualTo("test");
			assertThat(resolver.resolve(String.class)).isNull();
			assertThat(resolver.resolve(Integer.class)).isEqualTo(123);
			assertThat(resolver.resolve(Long.class)).isEqualTo(234L);
		}
	}


	@Nested
	class FactoryInstantiatorTests {

		private final ArgumentResolver resolver = ArgumentResolver.of(String.class, "test");

		@Test
		void defaultConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					DefaultConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void singleConstructorWithArgumentsCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SingleConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void multiplePrivateAndSinglePublicConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					MultiplePrivateAndSinglePublicConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void multiplePackagePrivateAndSinglePublicConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					MultiplePackagePrivateAndSinglePublicConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void singlePackagePrivateConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SinglePackagePrivateConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void singlePrivateConstructorCreatesInstance() throws Exception {
			Object instance = FactoryInstantiator.forClass(
					SinglePrivateConstructor.class).instantiate(this.resolver);
			assertThat(instance).isNotNull();
		}

		@Test
		void multiplePackagePrivateConstructorsThrowsException() {
			assertThatIllegalStateException().isThrownBy(
					() -> FactoryInstantiator.forClass(MultiplePackagePrivateConstructors.class))
				.withMessageContaining("has no suitable constructor");
		}

		static class DefaultConstructor {
		}

		static class SingleConstructor {

			SingleConstructor(String arg) {
			}
		}

		static class MultiplePrivateAndSinglePublicConstructor {

			public MultiplePrivateAndSinglePublicConstructor(String arg) {
				this(arg, false);
			}

			private MultiplePrivateAndSinglePublicConstructor(String arg, boolean extra) {
			}
		}

		static class MultiplePackagePrivateAndSinglePublicConstructor {

			public MultiplePackagePrivateAndSinglePublicConstructor(String arg) {
				this(arg, false);
			}

			MultiplePackagePrivateAndSinglePublicConstructor(String arg, boolean extra) {
			}
		}

		static class SinglePackagePrivateConstructor {

			SinglePackagePrivateConstructor(String arg) {
			}
		}

		static class SinglePrivateConstructor {

			private SinglePrivateConstructor(String arg) {
			}
		}

		static class MultiplePackagePrivateConstructors {

			MultiplePackagePrivateConstructors(String arg) {
				this(arg, false);
			}

			MultiplePackagePrivateConstructors(String arg, boolean extra) {
			}
		}
	}


	private static class LimitedClassLoader extends URLClassLoader {

		private static final ClassLoader constructorArgumentFactories = new LimitedClassLoader("constructor-argument-factories");

		private static final ClassLoader multipleArgumentFactories = new LimitedClassLoader("multiple-arguments-factories");

		LimitedClassLoader(String location) {
			super(new URL[] { toUrl(location) });
		}

		private static URL toUrl(String location) {
			try {
				return new File("src/test/resources/org/springframework/core/io/support/" + location + "/").toURI().toURL();
			}
			catch (MalformedURLException ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

}

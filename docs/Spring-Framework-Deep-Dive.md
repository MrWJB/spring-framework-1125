# Spring Framework 深度详解指南

本文档基于 Spring Framework 源代码（版本 6.x+）对以下主题进行深度详解：
- JMS（Java Message Service）
- JMX（Java Management Extensions）
- Email
- Task Execution and Scheduling（任务执行与调度）
- Cache Abstraction（缓存抽象）
- Observability Support（可观测性支持）
- JVM AOT Cache
- JVM Checkpoint Restore
- Appendix（附录）

---

## 目录

1. [JMS（Java Message Service）](#1-jmsjava-message-service)
2. [JMX（Java Management Extensions）](#2-jmxjava-management-extensions)
3. [Email（邮件支持）](#3-email邮件支持)
4. [Task Execution and Scheduling（任务执行与调度）](#4-task-execution-and-scheduling任务执行与调度)
5. [Cache Abstraction（缓存抽象）](#5-cache-abstraction缓存抽象)
6. [Observability Support（可观测性支持）](#6-observability-support可观测性支持)
7. [JVM AOT Cache](#7-jvm-aot-cache)
8. [JVM Checkpoint Restore](#8-jvm-checkpoint-restore)
9. [Appendix（附录）](#9-appendix附录)

---

## 1. JMS（Java Message Service）

### 1.1 概述

JMS（Java Message Service）是 Java EE 的消息传递服务规范，Spring Framework 通过 [`spring-jms`](spring-jms) 模块提供了完整的企业级消息支持。Spring JMS 抽象层简化了与消息中间件的交互，同时保留了底层 JMS -provider 的全部功能。

### 1.2 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Spring JMS 架构                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │   JmsTemplate │    │ Message      │    │ Listener    │      │
│  │              │    │ Listener     │    │ Container   │      │
│  │  消息发送    │    │  Container   │    │             │      │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘      │
│         │                   │                   │               │
│         └───────────────────┼───────────────────┘               │
│                             │                                   │
│                    ┌────────▼────────┐                         │
│                    │  JmsAccessor    │                         │
│                    │ (连接/会话管理)  │                         │
│                    └────────┬────────┘                         │
│                             │                                   │
│                    ┌────────▼────────┐                         │
│                    │ ConnectionFactory│                         │
│                    │ (JMS Provider)  │                         │
│                    └─────────────────┘                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 核心组件

#### 1.3.1 JmsTemplate

[`JmsTemplate`](spring-jms/src/main/java/org/springframework/jms/core/JmsTemplate.java) 是 Spring JMS 的核心类，用于同步发送和接收消息。

**主要特性：**
- 简化 JMS 操作（发送、接收、转换）
- 自动资源管理（连接、会话、消息生产者/消费者）
- 支持消息转换（MessageConverter）
- 支持目的地解析（DestinationResolver）
- 事务管理集成
- **Observability 支持**（自 Spring 6.1）

**关键方法：**

```java
// 发送消息
void send(Destination destination, MessageCreator creator);
void send(String destinationName, MessageCreator creator);

// 发送并等待回复
Message sendAndReceive(Destination destination, MessageCreator creator);

// 转换发送
void convertAndSend(Object message);
void convertAndSend(Destination destination, Object message);
Object receiveAndConvert();
Object receiveAndConvert(Destination destination);
```

**配置示例：**

```java
@Configuration
public class JmsConfig {
    
    @Bean
    public ConnectionFactory connectionFactory() {
        // 可以是 ActiveMQ, Artemis, IBM MQ 等
        return new ActiveMQConnectionFactory("tcp://localhost:61616");
    }
    
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        template.setObservationRegistry(observationRegistry); // Spring 6.1+
        return template;
    }
}
```

#### 1.3.2 MessageListenerContainer

[`AbstractMessageListenerContainer`](spring-jms/src/main/java/org/springframework/jms/listener/AbstractMessageListenerContainer.java) 及实现类负责异步消息监听。

**容器类型：**

| 容器类型 | 类 | 特点 |
|---------|-----|------|
| Simple | [`SimpleMessageListenerContainer`](spring-jms/src/main/java/org/springframework/jms/listener/SimpleMessageListenerContainer.java) | 简单同步监听，适合低吞吐量 |
| Default | [`DefaultMessageListenerContainer`](spring-jms/src/main/java/org/springframework/jms/listener/DefaultMessageListenerContainer.java) | 异步多线程，支持缓存、重试 |
| DefaultJca | JCA 容器 | J2EE 容器集成 |

**配置示例：**

```java
@Bean
public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
        ConnectionFactory connectionFactory,
        ObservationRegistry observationRegistry) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setObservationRegistry(observationRegistry);
    factory.setConcurrency("5-10"); // 最小/最大消费者数
    return factory;
}
```

#### 1.3.3 消息监听器注解

[`@JmsListener`](spring-jms/src/main/java/org/springframework/jms/annotation/JmsListener.java) 注解用于声明消息监听方法。

```java
@Component
public class OrderMessageListener {
    
    @JmsListener(destination = "orders", concurrency = "3-10")
    public void handleOrder(Order order) {
        // 处理订单消息
    }
    
    @JmsListener(destination = "notifications")
    public void handleNotification(Message message) {
        // 处理通知消息
    }
}
```

### 1.4 消息转换器

Spring JMS 提供多种消息转换器实现：

| 转换器 | 类 | 用途 |
|--------|-----|------|
| SimpleMessageConverter | [`SimpleMessageConverter`](spring-jms/src/main/java/org/springframework/jms/support/converter/SimpleMessageConverter.java) | 基本类型转换 |
| MappingJackson2MessageConverter | [`MappingJackson2MessageConverter`](spring-jms/src/main/java/org/springframework/jms/support/converter/MappingJackson2MessageConverter.java) | JSON 转换 |
| MarshallingMessageConverter | [`MarshallingMessageConverter`](spring-jms/src/main/java/org/springframework/jms/support/converter/MarshallingMessageConverter.java) | XML 转换 |

### 1.5 连接管理

#### 1.5.1 SingleConnectionFactory

[`SingleConnectionFactory`](spring-jms/src/main/java/org/springframework/jms/connection/SingleConnectionFactory.java) 包装底层连接工厂，共享单一连接。

```java
@Bean
public ConnectionFactory connectionFactory() {
    SingleConnectionFactory factory = new SingleConnectionFactory(actualConnectionFactory);
    factory.setReconnectOnException(true);
    return factory;
}
```

#### 1.5.2 CachingConnectionFactory

[`CachingConnectionFactory`](spring-jms/src/main/java/org/springframework/jms/connection/CachingConnectionFactory.java) 提供会话和消息生产者缓存。

```java
@Bean
public CachingConnectionFactory cachingConnectionFactory() {
    CachingConnectionFactory factory = new CachingConnectionFactory(actualConnectionFactory);
    factory.setSessionCacheSize(10);  // 缓存会话数
    factory.setCacheProducers(true);  // 缓存生产者
    return factory;
}
```

### 1.6 事务支持

#### 1.6.1 JmsTransactionManager

[`JmsTransactionManager`](spring-jms/src/main/java/org/springframework/jms/connection/JmsTransactionManager.java) 提供本地 JMS 事务管理。

```java
@Bean
public PlatformTransactionManager transactionManager(ConnectionFactory connectionFactory) {
    return new JmsTransactionManager(connectionFactory);
}
```

#### 1.6.2 XA 事务

通过 JTA（Java Transaction API）支持分布式事务。

### 1.7 Observability 支持（Spring 6.1+）

Spring JMS 完全集成 Micrometer Observation API：

```java
// 配置观察
jmsTemplate.setObservationRegistry(observationRegistry);
listenerContainer.setObservationRegistry(observationRegistry);

// 收集的指标
// - jms.message.send.duration
// - jms.message.receive.duration
// - jms.message.process.duration
```

---

## 2. JMX（Java Management Extensions）

### 2.1 概述

JMX（Java Management Extensions）是 Java 平台的管理和监控标准。Spring Framework 在 [`spring-context`](spring-context/src/main/java/org/springframework/jmx) 包中提供了全面的 JMX 支持，包括 MBean 导出、远程访问、通知等。

### 2.2 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Spring JMX 架构                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │           MBeanExporter                 │                   │
│  │    (MBean 注册与导出)                   │                   │
│  └──────────────────┬──────────────────────┘                   │
│                     │                                            │
│  ┌──────────────────┼──────────────────────┐                   │
│  │                  │                      │                   │
│  ▼                  ▼                      ▼                   │
│ ┌────────┐    ┌───────────┐      ┌──────────────┐            │
│ │ Assembler│    │ Naming    │      │ Notification │            │
│ │         │    │ Strategy  │      │ Publisher    │            │
│ └────────┘    └───────────┘      └──────────────┘            │
│     │                                                     │
│  ┌──┴──────────────────────────────────────┐               │
│  │           JMX Metadata                  │               │
│  │  @ManagedResource, @ManagedAttribute  │               │
│  │  @ManagedOperation, @ManagedMetric    │               │
│  └───────────────────────────────────────┘                │
│                                                            │
│  ┌─────────────────────────────────────────┐              │
│  │         Remote Access (JSR-160)        │              │
│  │  ConnectorServerFactoryBean            │              │
│  │  MBeanServerConnectionFactoryBean      │              │
│  └─────────────────────────────────────────┘              │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 2.3 核心组件

#### 2.3.1 MBeanExporter

[`MBeanExporter`](spring-context/src/main/java/org/springframework/jmx/export/MBeanExporter.java) 是将 Spring Bean 导出为 JMX MBean 的核心类。

**功能：**
- 自动检测并导出 Spring Bean 为 MBean
- 支持标准 MBean、MXBean、动态 MBean
- 可配置 ObjectName 策略
- 支持延迟注册
- 集成通知发布

**基本配置：**

```java
@Configuration
@EnableMBeanExport
public class JmxConfig {
    // 自动导出所有 @ManagedResource 注解的 Bean
}
```

```xml
<context:mbean-export/>
```

#### 2.3.2 MBean 信息组装器

Spring 提供多种 MBeanInfoAssembler 实现：

| Assembler | 类 | 说明 |
|-----------|-----|------|
| MetadataAssembler | [`MetadataMBeanInfoAssembler`](spring-context/src/main/java/org/springframework/jmx/export/assembler/MetadataMBeanInfoAssembler.java) | 基于注解 |
| MethodNameBased | [`MethodNameBasedMBeanInfoAssembler`](spring-context/src/main/java/org/springframework/jmx/export/assembler/MethodNameBasedMBeanInfoAssembler.java) | 基于方法名 |
| MethodExclusion | [`MethodExclusionMBeanInfoAssembler`](spring-context/src/main/java/org/springframework/jmx/export/assembler/MethodExclusionMBeanInfoAssembler.java) | 排除指定方法 |

#### 2.3.3 ObjectName 策略

| 策略 | 类 | 说明 |
|------|-----|------|
| IdentityNamingStrategy | [`IdentityNamingStrategy`](spring-context/src/main/java/org/springframework/jmx/export/naming/IdentityNamingStrategy.java) | 使用 Bean 标识 |
| KeyNamingStrategy | [`KeyNamingStrategy`](spring-context/src/main/java/org/springframework/jmx/export/naming/KeyNamingStrategy.java) | 使用 Bean 键 |
| MetadataNamingStrategy | [`MetadataNamingStrategy`](spring-context/src/main/java/org/springframework/jmx/export/naming/MetadataNamingStrategy.java) | 使用元数据 |
| Custom | 实现 [`ObjectNamingStrategy`](spring-context/src/main/java/org/springframework/jmx/export/naming/ObjectNamingStrategy.java) | 自定义策略 |

### 2.4 注解支持

#### 2.4.1 @ManagedResource

```java
@ManagedResource(
    objectName = "myapp:type=MyService,name=MyService",
    description = "My Service MBean"
)
public class MyService implements MyServiceMBean {
    // 实现 MBean 接口
}
```

#### 2.4.2 @ManagedAttribute

```java
@ManagedAttribute(
    description = "The current count",
    currencyTimeLimit = 10  // 缓存时间（秒）
)
public int getCount() { ... }

public void setCount(int count) { ... }
```

#### 2.4.3 @ManagedOperation

```java
@ManagedOperation(description = "Reset the counter")
public void reset() {
    this.count = 0;
}
```

#### 2.4.4 @ManagedMetric

```java
@ManagedMetric(
    category = "Performance",
    unit = "requests"
)
public long getRequestCount() { ... }
```

### 2.5 远程访问

#### 2.5.1 JMX Connector Server

```java
@Bean
public ConnectorServerFactoryBean connectorServerFactoryBean() {
    ConnectorServerFactoryBean factory = new ConnectorServerFactoryBean();
    factory.setServiceUrl("service:jmx:rmi://localhost:9999/jndi/rmi://localhost:1099/myconnector");
    return factory;
}
```

#### 2.5.2 客户端连接

```java
@Bean
public MBeanServerConnectionFactoryBean mBeanServerConnectionFactoryBean() {
    MBeanServerConnectionFactoryBean factory = new MBeanServerConnectionFactoryBean();
    factory.setServiceUrl("service:jmx:rmi://localhost:9999/jndi/rmi://localhost:1099/myconnector");
    return factory;
}
```

### 2.6 通知发布

#### 2.6.1 NotificationPublisher

```java
@ManagedResource(objectName = "myapp:type=MyService")
public class MyService implements NotificationPublisherAware {
    
    private NotificationPublisher notificationPublisher;
    
    @Override
    public void setNotificationPublisher(NotificationPublisher notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
    }
    
    public void doSomething() {
        // 执行业务逻辑
        notificationPublisher.sendNotification(
            new Notification("myapp.event", this, 0, "Event occurred")
        );
    }
}
```

---

## 3. Email（邮件支持）

### 3.1 概述

Spring Framework 通过 [`spring-context-support`](spring-context-support/src/main/java/org/springframework/mail) 模块提供 Email 支持，抽象了 JavaMail API，简化了邮件发送操作。

### 3.2 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Spring Email 架构                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐                                           │
│  │   MailSender    │ ◄─── 接口                                 │
│  └────────┬────────┘                                           │
│           │                                                     │
│  ┌────────▼────────┐                                           │
│  │JavaMailSender  │ ◄─── 接口                                 │
│  └────────┬────────┘                                           │
│           │                                                     │
│  ┌────────▼────────┐                                           │
│  │JavaMailSenderImpl│ ◄─── 实现类                              │
│  └────────┬────────┘                                           │
│           │                                                     │
│  ┌────────▼────────┐                                           │
│  │  JavaMail API  │ ( jakarta.mail / javax.mail )             │
│  └─────────────────┘                                           │
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │           MimeMessageHelper             │                   │
│  │  (复杂邮件构造工具)                      │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 核心组件

#### 3.3.1 MailSender

[`MailSender`](spring-context-support/src/main/java/org/springframework/mail/MailSender.java) 是简单邮件发送的接口。

```java
public interface MailSender {
    void send(SimpleMailMessage simpleMessage) throws MailException;
    void send(SimpleMailMessage... simpleMessages) throws MailException;
}
```

#### 3.3.2 JavaMailSender

[`JavaMailSender`](spring-context-support/src/main/java/org/springframework/mail/javamail/JavaMailSender.java) 是支持 MIME 邮件的接口。

```java
public interface JavaMailSender extends MailSender {
    MimeMessage createMimeMessage();
    MimeMessage createMimeMessage(InputStream contentStream) throws MailException;
    void send(MimeMessage mimeMessage) throws MailException;
    void send(MimeMessage... mimeMessages) throws MailException;
    void send(MimeMessagePreparator preparator) throws MailException;
    void send(MimeMessagePreparator... preparators) throws MailException;
}
```

#### 3.3.3 JavaMailSenderImpl

[`JavaMailSenderImpl`](spring-context-support/src/main/java/org/springframework/mail/javamail/JavaMailSenderImpl.java) 是实际实现类。

**配置示例：**

```java
@Bean
public JavaMailSenderImpl mailSender() {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost("smtp.example.com");
    sender.setPort(587);
    sender.setUsername("user@example.com");
    sender.setPassword("password");
    sender.setJavaMailProperties(javaMailProperties());
    return sender;
}

@Bean
public Properties javaMailProperties() {
    Properties props = new Properties();
    props.setProperty("mail.smtp.auth", "true");
    props.setProperty("mail.smtp.starttls.enable", "true");
    props.setProperty("mail.smtp.ssl.trust", "smtp.example.com");
    return props;
}
```

### 3.4 邮件消息

#### 3.4.1 SimpleMailMessage

[`SimpleMailMessage`](spring-context-support/src/main/java/org/springframework/mail/SimpleMailMessage.java) 用于发送简单文本邮件。

```java
SimpleMailMessage message = new SimpleMailMessage();
message.setFrom("sender@example.com");
message.setTo("recipient@example.com");
message.setSubject("Subject");
message.setText("Email body");
mailSender.send(message);
```

#### 3.4.2 MimeMessage + MimeMessageHelper

对于复杂邮件（HTML、附件、内联资源），使用 [`MimeMessageHelper`](spring-context-support/src/main/java/org/springframework/mail/javamail/MimeMessageHelper.java)。

```java
@Autowired
private JavaMailSender mailSender;

public void sendRichEmail() throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    
    // 第二个参数 true 表示 multipart
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    
    helper.setFrom("sender@example.com");
    helper.setTo("recipient@example.com");
    helper.setSubject("Rich Email");
    
    // HTML 内容
    helper.setText("<h1>Hello</h1><p>This is an HTML email.</p>", true);
    
    // 附件
    helper.addAttachment("document.pdf", new ClassPathResource("doc.pdf"));
    
    // 内联图片
    helper.addInline("logo", new ClassPathResource("logo.png"));
    
    mailSender.send(message);
}
```

### 3.5 邮件异常

| 异常类 | 说明 |
|--------|------|
| [`MailException`](spring-context-support/src/main/java/org/springframework/mail/MailException.java) | 基础异常 |
| [`MailAuthenticationException`](spring-context-support/src/main/java/org/springframework/mail/MailAuthenticationException.java) | 认证失败 |
| [`MailSendException`](spring-context-support/src/main/java/org/springframework/mail/MailSendException.java) | 发送失败 |
| [`MailParseException`](spring-context-support/src/main/java/org/springframework/mail/MailParseException.java) | 解析错误 |
| [`MailPreparationException`](spring-context-support/src/main/java/org/springframework/mail/MailPreparationException.java) | 邮件准备错误 |

---

## 4. Task Execution and Scheduling（任务执行与调度）

### 4.1 概述

Spring Framework 提供了强大的任务执行和调度支持，位于 [`spring-context`](spring-context/src/main/java/org/springframework/scheduling) 包中。

### 4.2 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│              Task Execution & Scheduling 架构                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │          TaskExecutor (接口)            │                   │
│  │    执行异步任务的抽象                    │                   │
│  └──────────────────┬──────────────────────┘                   │
│                     │                                            │
│     ┌───────────────┼───────────────┐                         │
│     │               │               │                          │
│     ▼               ▼               ▼                          │
│ ┌────────┐   ┌──────────┐   ┌────────────┐                   │
│ │ThreadPool│   │  Simple  │   │  TaskQueue │                   │
│ │TaskExecutor│ │TaskExecutor│ │TaskExecutor │                   │
│ └────────┘   └──────────┘   └────────────┘                   │
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │        TaskScheduler (接口)              │                   │
│  │    执行定时任务的抽象                    │                   │
│  └──────────────────┬──────────────────────┘                   │
│                     │                                            │
│     ┌───────────────┼───────────────┐                         │
│     │               │               │                          │
│     ▼               ▼               ▼                          │
│ ┌──────────┐  ┌────────────┐ ┌──────────────┐                │
│ │ThreadPool│  │TimerTask  │ │Quartz        │                │
│ │TaskScheduler│ │Scheduler │ │Scheduler    │                │
│ └──────────┘  └────────────┘ └──────────────┘                │
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │     @Scheduled (注解)                    │                   │
│  │     @Async (注解)                       │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 TaskExecutor（任务执行器）

#### 4.3.1 内置实现

| 实现 | 类 | 用途 |
|------|-----|------|
| SyncTaskExecutor | [`SyncTaskExecutor`](spring-context/src/main/java/org/springframework/core/task/SyncTaskExecutor.java) | 同步执行 |
| SimpleAsyncTaskExecutor | [`SimpleAsyncTaskExecutor`](spring-context/src/main/java/org/springframework/core/task/SimpleAsyncTaskExecutor.java) | 简单异步，无限线程 |
| ThreadPoolTaskExecutor | [`ThreadPoolTaskExecutor`](spring-context/src/main/java/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.java) | 线程池执行 |
| ConcurrentTaskExecutor | [`ConcurrentTaskExecutor`](spring-context/src/main/java/org/springframework/core/task/ConcurrentTaskExecutor.java) | Java Executor 适配器 |
| WorkManagerTaskExecutor | [`WorkManagerTaskExecutor`](spring-context/src/main/java/org/springframework/scheduling/work/WorkManagerTaskExecutor.java) | Work Manager 集成 |

#### 4.3.2 ThreadPoolTaskExecutor 配置

```java
@Bean
public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("my-task-");
    executor.setRejectedExecutionHandler(new CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

### 4.4 TaskScheduler（任务调度器）

#### 4.4.1 内置实现

| 实现 | 类 | 用途 |
|------|-----|------|
| ThreadPoolTaskScheduler | [`ThreadPoolTaskScheduler`](spring-context/src/main/java/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.java) | 线程池调度 |
| TimerTaskScheduler | [`TimerTaskScheduler`](spring-context/src/main/java/org/springframework/scheduling/timer/TimerTaskScheduler.java) | Java Timer 调度 |
| SystemmTaskScheduler | [`SystemmTaskScheduler`](spring-context/src/main/java/org/springframework/scheduling/timer/SystemmTaskScheduler.java) | 系统任务调度 |

#### 4.4.2 Trigger 接口

[`Trigger`](spring-context/src/main/java/org/springframework/scheduling/Trigger.java) 接口定义任务执行时机。

```java
public interface Trigger {
    Date nextExecutionTime(TriggerContext triggerContext);
}
```

**内置 Trigger 实现：**

| Trigger | 说明 |
|---------|------|
| CronTrigger | Cron 表达式触发 |
| PeriodicTrigger | 固定频率/延迟触发 |

### 4.5 注解支持

#### 4.5.1 @EnableScheduling

[`@EnableScheduling`](spring-context/src/main/java/org/springframework/scheduling/annotation/EnableScheduling.java) 启用定时任务支持。

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // 配置 TaskScheduler Bean
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        return scheduler;
    }
}
```

#### 4.5.2 @Scheduled

[`@Scheduled`](spring-context/src/main/java/org/springframework/scheduling/annotation/Scheduled.java) 注解用于声明定时任务。

```java
@Component
public class ScheduledTasks {
    
    // 固定频率（毫秒）
    @Scheduled(fixedRate = 5000)
    public void task1() {
        // 每 5 秒执行
    }
    
    // 固定延迟（上次完成后）
    @Scheduled(fixedDelay = 3000)
    public void task2() {
        // 上次完成后 3 秒执行
    }
    
    // 初始延迟 + 固定频率
    @Scheduled(initialDelay = 1000, fixedRate = 5000)
    public void task3() {
        // 首次延迟 1 秒，之后每 5 秒执行
    }
    
    // Cron 表达式
    @Scheduled(cron = "0 0 * * * *")
    public void task4() {
        // 每小时执行
    }
    
    // Zone 时区
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Shanghai")
    public void task5() {
        // 每天早上 9 点（上海时区）执行
    }
}
```

#### 4.5.3 @EnableAsync + @Async

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    // 配置异步执行器
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

```java
@Service
public class AsyncService {
    
    @Async("taskExecutor")
    public Future<String> asyncMethod() {
        // 异步执行
        return new AsyncResult<>("result");
    }
    
    @Async
    public CompletableFuture<String> asyncMethodReturningCompletableFuture() {
        // 返回 CompletableFuture
        return CompletableFuture.completedFuture("result");
    }
}
```

#### 4.5.4 SchedulingConfigurer

```java
@Configuration
@EnableScheduling
public class SchedulingConfigurerConfig implements SchedulingConfigurer {
    
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 编程方式注册任务
        taskRegistrar.addTriggerTask(
            () -> System.out.println("Custom task"),
            triggerContext -> {
                // 自定义 Trigger 逻辑
                return new CronTrigger("0 * * * * *").nextExecutionTime(triggerContext);
            }
        );
    }
}
```

### 4.6 Quartz 集成

Spring 提供完整的 Quartz 集成支持。

#### 4.6.1 SchedulerFactoryBean

[`SchedulerFactoryBean`](spring-context-support/src/main/java/org/springframework/scheduling/quartz/SchedulerFactoryBean.java) 管理 Quartz 调度器。

```java
@Bean
public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource) {
    SchedulerFactoryBean factory = new SchedulerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setOverwriteExistingJobs(true);
    factory.setStartupDelay(10);
    factory.setApplicationContextSchedulerContextKey("applicationContext");
    return factory;
}
```

#### 4.6.2 JobDetailFactoryBean

```java
@Bean
public JobDetailFactoryBean jobDetail() {
    JobDetailFactoryBean factory = new JobDetailFactoryBean();
    factory.setJobClass(MyJob.class);
    factory.setDurability(true);
    return factory;
}

public class MyJob extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        // 执行任务
    }
}
```

#### 4.6.3 CronTriggerFactoryBean

```java
@Bean
public CronTriggerFactoryBean cronTrigger(JobDetail jobDetail) {
    CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
    factory.setJobDetail(jobDetail);
    factory.setCronExpression("0 0 * * * ?");
    return factory;
}
```

### 4.7 Observability 支持（Spring 6.1+）

```java
@Configuration
@EnableScheduling
public class ObservabilitySchedulingConfig implements SchedulingConfigurer {
    
    private final ObservationRegistry observationRegistry;
    
    public ObservabilitySchedulingConfig(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }
    
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setObservationRegistry(observationRegistry);
    }
}
```

收集的指标：
- `tasks.scheduled.execution` - 任务执行观察

---

## 5. Cache Abstraction（缓存抽象）

### 5.1 概述

Spring Cache 抽象位于 [`spring-context-support`](spring-context-support/src/main/java/org/springframework/cache) 包中，提供了统一的缓存编程模型。

### 5.2 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Cache 架构                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │     @Cacheable (注解)                   │                   │
│  │     @CacheEvict (注解)                  │                   │
│  │     @CachePut (注解)                    │                   │
│  │     @Caching (注解)                     │                   │
│  └──────────────────┬──────────────────────┘                   │
│                     │                                            │
│  ┌──────────────────▼──────────────────────┐                   │
│  │        CacheManager (接口)              │                   │
│  │         缓存管理器                       │                   │
│  └──────────────────┬──────────────────────┘                   │
│                     │                                            │
│  ┌──────────────────┼──────────────────────┐                   │
│  │                  │                      │                   │
│  ▼                  ▼                      ▼                   │
│ ┌────────┐    ┌───────────┐       ┌──────────────┐            │
│ │Concurrent│   │  EhCache  │       │    JCache    │            │
│ │MapCache │   │  Cache    │       │    (JSR-107) │            │
│ └────────┘    └───────────┘       └──────────────┘            │
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │     Cache Abstraction Layers           │                   │
│  │  ┌─────────────────────────────────┐   │                   │
│  │  │      CacheInterceptor          │   │                   │
│  │  │      AOP 切面                   │   │                   │
│  │  └─────────────────────────────────┘   │                   │
│  │  ┌─────────────────────────────────┐   │                   │
│  │  │  CacheOperationSource          │   │                   │
│  │  │  操作源（注解解析）             │   │                   │
│  │  └─────────────────────────────────┘   │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 核心接口

#### 5.3.1 Cache

```java
public interface Cache {
    String getName();
    Object getNativeCache();
    ValueWrapper get(Object key);
    <T> T get(Object key, Class<T> type);
    <T> T get(Object key, Callable<T> valueLoader);
    void put(Object key, Object value);
    void evict(Object key);
    void clear();
    // ... 更多方法
}
```

#### 5.3.2 CacheManager

```java
public interface CacheManager {
    Cache getCache(String name);
    Collection<String> getCacheNames();
}
```

### 5.4 注解支持

#### 5.4.1 @Cacheable

```java
@Service
public class UserService {
    
    @Cacheable("users")
    public User getUserById(Long id) {
        // 从数据库查询
        return userRepository.findById(id);
    }
    
    // 多个缓存
    @Cacheable({"users", "userDetail"})
    public User getUserDetail(Long id) {
        return userRepository.findById(id);
    }
    
    // 条件缓存
    @Cacheable(value = "users", condition = "#id > 100")
    public User getUserByIdCondition(Long id) {
        return userRepository.findById(id);
    }
    
    // 键表达式
    @Cacheable(value = "users", key = "'user_' + #id")
    public User getUserByIdKey(Long id) {
        return userRepository.findById(id);
    }
    
    // 键生成器
    @Cacheable(value = "users", keyGenerator = "customKeyGenerator")
    public User getUserByIdGenerator(Long id) {
        return userRepository.findById(id);
    }
}
```

#### 5.4.2 @CachePut

```java
@Service
public class UserService {
    
    @CachePut(value = "users", key = "#result.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }
}
```

#### 5.4.3 @CacheEvict

```java
@Service
public class UserService {
    
    // 清除单个
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    
    // 清除所有
    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsers() {
        // 清除所有用户缓存
    }
    
    // 在方法执行前清除
    @CacheEvict(value = "users", beforeInvocation = true)
    public void clearUsersBefore(Long id) {
        throw new RuntimeException("Exception will trigger cache clear");
    }
}
```

#### 5.4.4 @Caching

```java
@Service
public class UserService {
    
    @Caching(
        evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "userDetail", key = "#id")
        }
    )
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

### 5.5 缓存实现

#### 5.5.1 ConcurrentMapCache

基于 `ConcurrentHashMap` 的简单实现，适合开发/测试。

```java
@Bean
public ConcurrentMapCacheManager cacheManager() {
    return new ConcurrentMapCacheManager("users", "orders");
}
```

#### 5.5.2 CaffeineCache

Caffeine 是高性能的缓存库。

```java
@Bean
public CaffeineCacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager("users");
    manager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .recordStats());
    return manager;
}
```

**配置选项：**

| 选项 | 说明 |
|------|------|
| maximumSize | 最大条目数 |
| maximumWeight | 最大权重 |
| expireAfterWrite | 写入后过期 |
| expireAfterAccess | 访问后过期 |
| refreshAfterWrite | 写入后刷新 |
| recordStats | 记录统计 |

#### 5.5.3 JCache (JSR-107)

```java
@Bean
public JCacheCacheManager cacheManager() throws Exception {
    JCacheCacheManager manager = new JCacheCacheManager();
    manager.setCacheManager(Caching.getCachingProvider().getCacheManager());
    return manager;
}
```

### 5.6 启用缓存

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        // 配置缓存管理器
        return new ConcurrentMapCacheManager();
    }
}
```

### 5.7 自定义配置

#### 5.7.1 KeyGenerator

```java
@Bean
public KeyGenerator customKeyGenerator() {
    return (target, method, params) -> {
        StringBuilder sb = new StringBuilder();
        sb.append(target.getClass().getName());
        sb.append(":");
        sb.append(method.getName());
        for (Object param : params) {
            sb.append(":").append(param.toString());
        }
        return sb.toString();
    };
}
```

#### 5.7.2 CacheResolver

```java
@Bean
public CacheResolver cacheResolver(CacheManager cacheManager) {
    return new CustomCacheResolver(cacheManager);
}
```

---

## 6. Observability Support（可观测性支持）

### 6.1 概述

Spring Framework 6.x 全面集成了 Micrometer Observation API，提供统一的可观测性支持。Observability 包含三个支柱：
- **Metrics（指标）** - 量化测量
- **Traces（追踪）** - 请求流程
- **Logs（日志）** - 事件记录

### 6.2 核心架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Observability 架构                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │         ObservationRegistry             │                   │
│  │     (观察注册表 - 核心组件)             │                   │
│  └──────────────────┬──────────────────────┘                   │
│                     │                                            │
│  ┌──────────────────▼──────────────────────┐                   │
│  │            Observation                  │                   │
│  │     (观察上下文)                        │                   │
│  └──────────────────┬──────────────────────┘                   │
│                     │                                            │
│     ┌───────────────┼───────────────┐                         │
│     │               │               │                          │
│     ▼               ▼               ▼                          │
│ ┌────────┐    ┌──────────┐   ┌────────────┐                   │
│ │ Timer  │    │ Counter  │   │   Gauge    │                   │
│ └────────┘    └──────────┘   └────────────┘                   │
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │       ObservationHandler                │                   │
│  │  (处理观察事件)                         │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │     ObservationConvention               │                   │
│  │   (命名约定自定义)                      │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 集成组件

#### 6.3.1 HTTP 服务端

```java
// 配置
@Bean
public WebHttpHandlerBuilder webHttpHandlerBuilder(ApplicationContext applicationContext) {
    return WebHttpHandlerBuilder.applicationContext(applicationContext)
        .observationRegistry(observationRegistry);
}
```

收集的指标：`http.server.requests`

#### 6.3.2 HTTP 客户端

```java
// RestTemplate
RestTemplate template = new RestTemplate();
template.setObservationRegistry(observationRegistry);

// RestClient (Spring 6.1+)
RestClient client = RestClient.builder()
    .observationRegistry(observationRegistry)
    .build();
```

收集的指标：`http.client.requests`

#### 6.3.3 JMS

```java
// JmsTemplate
jmsTemplate.setObservationRegistry(observationRegistry);

// Listener Container
listenerContainer.setObservationRegistry(observationRegistry);
```

收集的指标：
- `jms.message.send`
- `jms.message.receive`
- `jms.message.process`

#### 6.3.4 Scheduling

```java
// 配置
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {
    
    private final ObservationRegistry observationRegistry;
    
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setObservationRegistry(observationRegistry);
    }
}
```

收集的指标：`tasks.scheduled.execution`

### 6.4 自定义 Observation

```java
@Service
public class MyService {
    
    private final ObservationRegistry observationRegistry;
    
    public void doSomething(String param) {
        Observation observation = Observation.createNotStarted(
            "my.operation",
            observationRegistry
        );
        
        observation.operationName("my.custom.operation");
        observation.contextualName("myService.doSomething");
        observation.lowCardinalityKeyValue("type", "custom");
        
        observation.start();
        
        try {
            // 业务逻辑
            doBusiness(param);
        } catch (Exception e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }
}
```

### 6.5 测试支持

```java
@Test
void testWithObservation() {
    TestObservationRegistry registry = TestObservationRegistry.create();
    
    // 执行测试
    
    // 验证观察
    assertThat(registry)
        .hasObservationWithNameEqualTo("my.operation")
        .that()
        .hasBeenStarted()
        .hasBeenStopped();
}
```

---

## 7. JVM AOT Cache

### 7.1 概述

Spring AOT（ahead-of-time）处理在构建时优化应用程序，提高启动性能并减少内存占用。AOT 处理主要在 Spring Boot 的构建插件中完成，但 Spring Framework 提供了核心基础设施。

### 7.2 AOT 处理流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     Spring AOT 处理流程                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │  Build Time │───▶│   Process   │───▶│  Generate   │         │
│  │             │    │   (处理)    │    │  (生成代码) │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                                 │               │
│                                                 ▼               │
│                                          ┌─────────────┐        │
│                                          │   Compile   │        │
│                                          │   (编译)    │        │
│                                          └─────────────┘        │
│                                                 │               │
│                                                 ▼               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Runtime   │◀───│   Native    │◀───│    Run      │         │
│  │   (运行时)  │    │   Image     │    │   (运行)    │         │
│  │             │    │  (原生镜像) │    │             │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.3 核心 API

#### 7.3.1 AotDetector

[`AotDetector`](spring-core/src/main/java/org/springframework/aot/AotDetector.java) 用于检测 AOT 模式。

```java
public class AotDetector {
    
    // 系统属性：启用 AOT
    public static final String AOT_ENABLED = "spring.aot.enabled";
    
    // 检测是否使用 AOT 生成的工件
    public static boolean useGeneratedArtifacts() {
        return (inNativeImage || SpringProperties.getFlag(AOT_ENABLED));
    }
}
```

#### 7.3.2 RuntimeHints

Spring 提供运行时提示机制，帮助识别运行时需要的反射、资源等。

```java
@Bean
public RuntimeHints runtimeHints() {
    RuntimeHints hints = new RuntimeHints();
    
    // 反射
    hints.reflection().registerType(MyClass.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    
    // 资源
    hints.resources().addPattern("META-INF/my.properties");
    
    // 类加载
    hints.classLoad().registerClassForName("com.example.MyClass");
    
    return hints;
}
```

### 7.4 AOT 处理器

#### 7.4.1 BeanFactoryInitializationAotProcessor

```java
public interface BeanFactoryInitializationAotProcessor {
    
    AotContribution processAheadOfTime(BeanFactory beanFactory);
}
```

#### 7.4.2 BeanRegistrationAotProcessor

```java
public interface BeanRegistrationAotProcessor {
    
    AotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory,
                                       BeanDefinition beanDefinition);
}
```

### 7.5 AOT 贡献（AotContribution）

```java
public interface AotContribution {
    
    // 生成运行时初始化代码
    void applyToInitializing(ApplicationContextInitializer initializer);
    
    // 注册运行时提示
    void registerRuntimeHints(RuntimeHints hints);
}
```

---

## 8. JVM Checkpoint Restore

### 8.1 概述

JVM Checkpoint Restore 是基于 CRaC（Coordinated Restore at Checkpoint）JVM 的功能，允许在特定时刻创建 JVM 检查点（checkpoint），之后可以恢复到该状态。这对于快速启动预热后的应用非常有用。

### 8.2 Spring 支持

Spring Framework 通过 [`DefaultLifecycleProcessor`](spring-context/src/main/java/org/springframework/context/support/DefaultLifecycleProcessor.java) 支持 Checkpoint/Restore。

### 8.3 核心机制

```java
public class DefaultLifecycleProcessor {
    
    // 启用检查点
    private boolean checkpointOnRefresh = false;
    
    // CRaC 委托
    private class CracDelegate implements org.crac.Resource {
        
        @Override
        public void beforeCheckpoint(org.crac.Context<? extends org.crac.Resource> context) {
            // 准备检查点
            Thread thread = new Thread(this::preventShutdown);
            thread.start();
        }
        
        @Override
        public void afterRestore(org.crac.Context<? extends org.crac.Resource> context) {
            // 恢复后处理
            logger.info("Restored from checkpoint");
        }
    }
}
```

### 8.4 配置

```java
@Bean
public ConfigurableApplicationContext applicationContext() {
    GenericApplicationContext context = new GenericApplicationContext();
    // ... 配置
    
    // 启用检查点恢复
    System.setProperty("spring.lifecycle.checkpoint.on.refresh", "true");
    
    return context;
}
```

### 8.5 使用要求

1. **CRaC 启用 JVM**：需要使用支持 CRaC 的 JVM（如 Azul Zulu Prime, Oracle OpenJDK）
2. **CRaC 依赖**：
```xml
<dependency>
    <groupId>org.crac</groupId>
    <artifactId>crac</artifactId>
    <version>1.4.0</version>
</dependency>
```

---

## 9. Appendix（附录）

### 9.1 模块依赖关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Framework 模块依赖                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  spring-core ──────────────────────────────────────────────►   │
│      │                                                          │
│      ├──────► spring-beans ──────────────────────────────────► │
│      │              │                                           │
│      │              ├──────► spring-aop ──────────────────────► │
│      │              │                                           │
│      │              ├──────► spring-context ──────────────────► │
│      │              │              │                            │
│      │              │              ├────► spring-jms           │
│      │              │              │                            │
│      │              │              ├────► spring-context-support│
│      │              │              │     (mail, cache, sched)  │
│      │              │              │                            │
│      │              │              ├────► spring-instrument     │
│      │              │              │                            │
│      │              └──────────────┴────────────────────────►   │
│      │                                                          │
│      ├──────► spring-expression ─────────────────────────────►  │
│      │                                                          │
│      └──────► spring-core-test ─────────────────────────────►   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 版本历史

| Spring 版本 | JMS | JMX | Email | Scheduling | Cache | Observability | AOT |
|-------------|-----|-----|-------|------------|-------|---------------|-----|
| 2.5 | ✓ | ✓ | ✓ | XML | ✓ | - | - |
| 3.0 | ✓ | ✓ | ✓ | @Scheduled | ✓ | - | - |
| 4.0 | ✓ | ✓ | ✓ | ✓ | ✓ | - | - |
| 5.0 | ✓ | ✓ | ✓ | ✓ | ✓ | - | - |
| 5.3 | ✓ | ✓ | ✓ | ✓ | ✓ | 初步 | - |
| 6.0 | ✓ | ✓ | ✓ | ✓ | ✓ | 完整 | ✓ |
| 6.1 | ✓ | ✓ | ✓ | ✓ | ✓ | 增强 | ✓ |

### 9.3 配置属性参考

#### 9.3.1 JMS 配置

```properties
# JMS
spring.jms.pub-sub-domain=false
spring.jms.cache.enabled=true
spring.jms.cache.session-cache-size=1
```

#### 9.3.2 Scheduling 配置

```properties
# Scheduling
spring.task.scheduling.pool.size=1
spring.task.execution.pool.core-size=1
spring.task.execution.pool.max-size=
spring.task.execution.pool.queue-capacity=
spring.task.execution.thread-name-prefix=task-
spring.task.execution.shutdown.await-termination=false
spring.task.execution.shutdown.await-termination-period=
```

#### 9.3.3 Cache 配置

```properties
# Cache
spring.cache.cache-names=userCache,orderCache
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=10m
spring.cache.ehcache.spec=maximumSize=10000
spring.cache.jcache.config=classpath:ehcache.xml
```

#### 9.3.4 AOT 配置

```properties
# AOT
spring.aot.enabled=true
spring.native.hints=com.example.MyHints
```

### 9.4 最佳实践

#### 9.4.1 JMS 最佳实践

1. 使用连接池（CachingConnectionFactory）
2. 配置适当的消息确认模式
3. 使用事务确保消息可靠性
4. 实施错误处理和重试机制
5. 监控消息处理时间

#### 9.4.2 JMX 最佳实践

1. 最小化暴露的管理接口
2. 使用合适的 ObjectName 命名空间
3. 实施安全控制
4. 谨慎使用通知
5. 定期审查暴露的属性和操作

#### 9.4.3 Email 最佳实践

1. 使用连接池
2. 实现发送失败重试
3. 验证邮件地址
4. 使用 HTML 邮件模板
5. 注意邮件大小限制

#### 9.4.4 Scheduling 最佳实践

1. 使用线程池执行器
2. 避免任务冲突
3. 合理设置并发数
4. 实现任务监控和日志
5. 考虑时区问题

#### 9.4.5 Cache 最佳实践

1. 选择合适的缓存实现
2. 设置合理的过期策略
3. 避免缓存雪崩
4. 实现缓存击穿保护
5. 监控缓存命中率

### 9.5 常见问题

#### 9.5.1 JMS 常见问题

**Q: 消息丢失如何处理？**
A: 使用持久化消息、事务、消息确认机制

**Q: 如何处理慢消费者？**
A: 调整并发设置、使用死信队列

#### 9.5.2 Scheduling 常见问题

**Q: @Scheduled 任务不执行？**
A: 检查 @EnableScheduling 是否启用、任务方法是否有参数

**Q: 定时任务执行时间过长？**
A: 使用异步执行、调整调度池大小

#### 9.5.3 Cache 常见问题

**Q: 缓存和数据库不一致？**
A: 使用 CacheEvict、使用双写模式

**Q: 缓存内存溢出？**
A: 设置最大容量、使用过期策略

---

## 参考资料

- [Spring Framework 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/)
- [Spring JMS 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#jms)
- [Spring JMX 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#jmx)
- [Spring Cache 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)
- [Spring Scheduling 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- [Micrometer 文档](https://micrometer.io/docs)
- [CRaC 项目](https://wiki.openjdk.org/display/CRaC)

---

*本文档基于 Spring Framework 源代码分析编写，内容截止至版本 6.x。*

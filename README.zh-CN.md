# metrics-resteasy

[English](./README.md) | [简体中文](./README.zh-CN.md)

[![Java](https://img.shields.io/badge/Java-21-orange)](https://github.com/easy-4-java/metrics-resteasy) [![License](https://img.shields.io/badge/license-Apache%202.0-green)](./LICENSE)

metrics-resteasy 将 Dropwizard Metrics（metrics-core 4.1.1）与 RESTEasy JAX-RS 应用集成。

> **项目状态**：`feature/3.0.x` 版本线维护中（JDK 8）。制品尚未发布到 Maven Central，通过项目私服与 GitHub Releases 分发。

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 功能与状态](#2-功能与状态)
- [3. 环境要求与兼容性](#3-环境要求与兼容性)
- [4. 架构与模块](#4-架构与模块)
- [5. 安装](#5-安装)
- [6. 快速开始](#6-快速开始)
- [7. 配置](#7-配置)
- [8. 核心用法 / API](#8-核心用法--api)
- [9. 测试与构建](#9-测试与构建)
- [10. 版本与分支](#10-版本与分支)
- [11. 贡献与许可](#11-贡献与许可)

## 1. 项目概述

`metrics-resteasy` 将 [Dropwizard Metrics](https://metrics.dropwizard.io/)（metrics-core 4.1.1）与 [RESTEasy](https://resteasy.github.io/) JAX-RS 应用集成。它是一个 JAX-RS `DynamicFeature`，检查资源方法并依据 `metrics-annotation` 的 `@Timed` / `@Metered` 注解自动为其插桩 timer 与 meter。

是什么：

- `MetricsFeature`——`@Provider` JAX-RS `DynamicFeature`（仅服务端，`@ConstrainedTo(SERVER)`），按 `@Timed` / `@Metered` 注解为每个资源注册拦截器；
- `TimedInterceptor`——`ContainerRequestFilter` + `ContainerResponseFilter` 组合，请求时启动 `Timer.Context`，响应时停止；
- `MeterInterceptor`——`ContainerRequestFilter`，每次请求调用 `meter.mark()`；
- 指标命名——支持注解的显式 `name` / `absolute` 属性，否则由资源方法与路径推导名称。

不是什么：

- 不是 RESTEasy 专用运行时——它只使用标准 JAX-RS 2.0（`javax.ws.rs.container.*`），因此也适用于支持 `DynamicFeature` 的其他 JAX-RS 实现。

典型场景：

| 场景 | 使用 |
| :--- | :--- |
| 为所有 `@Timed` 资源方法计时 | 将 `MetricsFeature` 注册为 provider |
| 统计每个 `@Metered` 资源方法的请求量 | 同一 feature；meter 名称由方法 + 路径推导 |
| 显式指标名称 | `@Timed(name = "...", absolute = true)` |

## 2. 功能与状态

| 能力 | 状态 | 说明 |
| :--- | :--- | :--- |
| `@Timed` 资源插桩 | 已实现 | `MetricsFeature` → `TimedInterceptor`（请求/响应过滤器） |
| `@Metered` 资源插桩 | 已实现 | `MetricsFeature` → `MeterInterceptor`（请求过滤器） |
| 显式/绝对指标名 | 已实现 | `chooseName` 遵循 `@Timed(name, absolute)` / `@Metered(name, absolute)` |
| 仅服务端 | 已实现 | feature 上标注 `@ConstrainedTo(RuntimeType.SERVER)` |
| 测试 | 暂无 | 本分支无 `src/test` |

## 3. 环境要求与兼容性

| 项目 | 要求 |
| :--- | :--- |
| JDK | 21+ |
| Maven | 3.0+（内置 Maven Wrapper `mvnw`） |
| 依赖 | resteasy-jaxrs 3.9.0.Final、metrics-core 4.1.1、metrics-annotation、metrics-healthchecks、guava 33.6.0-jre、slf4j-api 2.0.18、javax.servlet-api（provided）、lombok（provided）；junit 4.13.2（测试） |

版本线：

| 分支 | JDK | 版本模式 |
| :--- | :---: | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` |
| `feature/2.0.x` | 17 | `2.0.x.*` |
| `feature/3.0.x` | 21 | `3.0.x.*` |

## 4. 架构与模块

```text
JAX-RS 资源方法 (@Timed / @Metered)
        |
        v
MetricsFeature (DynamicFeature, @Provider, 仅服务端)
        |
        +--> TimedInterceptor  (timer.time() ... context.stop())
        `--> MeterInterceptor  (meter.mark())
        |
        v
MetricRegistry (metrics-core 4.1.1)
```

单模块 jar，`com.codahale.metrics.resteasy` 下 3 个类：

| 类 | 职责 |
| :--- | :--- |
| `MetricsFeature` | `DynamicFeature`；注册拦截器并解析指标名 |
| `TimedInterceptor` | 请求 + 响应过滤器，对调用计时 |
| `MeterInterceptor` | 请求过滤器，标记 meter |

## 5. 安装

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>metrics-resteasy</artifactId>
    <version>3.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

Gradle：

```groovy
implementation 'io.github.easy4j:metrics-resteasy:3.0.x.x.20260630-SNAPSHOT'
```

快照版本由项目私服提供（见 pom 中 `distributionManagement`）。尚未发布 Maven Central 正式版。

## 6. 快速开始

将 feature 与你的 `MetricRegistry` 关联，并在资源方法上添加注解：

```java
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.resteasy.MetricsFeature;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/orders")
public class OrderResource {

    @GET
    @Timed
    @Metered
    public String list() {
        return "ok";
    }
}

// 在 JAX-RS Application / RESTEasy 装配中：
MetricRegistry registry = new MetricRegistry();
singletons.add(new MetricsFeature(registry));   // 注册为 JAX-RS provider
```

此后每次 `GET /orders` 都会在 `registry` 中更新一个 timer 与一个 meter；指标名由资源方法与路径推导（设置 `absolute = true` 时使用显式 `name`）。

## 7. 配置

无属性文件配置。行为由以下两点驱动：

- 资源方法上的 `@Timed` / `@Metered` 注解（含 `name` 与 `absolute`）；
- 传入 `new MetricsFeature(registry)` 的 `MetricRegistry` 实例。

## 8. 核心用法 / API

### 8.1 显式指标名

```java
@GET
@Timed(name = "orders.list", absolute = true)
public String list() { ... }
// 记录到 registry.timer("orders.list")，而非推导名称
```

### 8.2 独立使用拦截器

拦截器也可自行装配直接复用：

```java
Timer timer = registry.timer("custom");
TimedInterceptor interceptor = new TimedInterceptor(timer); // 过滤器对，无需注解
```

## 9. 测试与构建

```bash
./mvnw clean verify
```

构建配置：

- JUnit 4 + Maven Surefire（本分支暂无测试源码）；
- JaCoCo 覆盖率报告 + 行覆盖率检查规则，最低目标 90%（`haltOnFailure=false`）；
- package 阶段附加源码包与 Javadoc 包；
- 提供 `central` 发布 profile（GPG 签名 + Central 发布插件），仅用于正式发布。

## 10. 版本与分支

三条并行版本线，各自绑定一个 JDK 基线：

| 分支 | JDK | 版本模式 | 维护状态 |
| :--- | :---: | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` | 当前开发线 |
| `feature/2.0.x` | 17 | `2.0.x.*` | 并行维护 |
| `feature/3.0.x` | 21 | `3.0.x.*` | 并行维护 |

本分支快照版本为 `3.0.x.x.20260630-SNAPSHOT`。

## 11. 贡献与许可

欢迎通过 GitHub Issue 或 Pull Request 参与贡献。所有源码基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) 许可。

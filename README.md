# metrics-resteasy

[English](./README.md) | [简体中文](./README.zh-CN.md)

> **Status**: maintained on the `feature/3.0.x` line (JDK 21). Artifacts are not yet published to Maven Central; they are distributed through the project's private repository and GitHub Releases.

## Table of Contents

- [1. Project Overview](#1-project-overview)
- [2. Features & Status](#2-features--status)
- [3. Requirements & Compatibility](#3-requirements--compatibility)
- [4. Architecture & Modules](#4-architecture--modules)
- [5. Installation](#5-installation)
- [6. Quick Start](#6-quick-start)
- [7. Configuration](#7-configuration)
- [8. Core Usage / API](#8-core-usage--api)
- [9. Testing & Build](#9-testing--build)
- [10. Versioning & Branches](#10-versioning--branches)
- [11. Contributing & License](#11-contributing--license)

## 1. Project Overview

`metrics-resteasy` integrates [Dropwizard Metrics](https://metrics.dropwizard.io/) (metrics-core 4.1.1) with [RESTEasy](https://resteasy.github.io/) JAX-RS applications. It is a JAX-RS `DynamicFeature` that inspects resource methods and automatically instruments them with timers (`@Timed`) and meters (`@Metered`) from `metrics-annotation`.

What it is:

- `MetricsFeature` — a `@Provider` JAX-RS `DynamicFeature` (server-side only, `@ConstrainedTo(SERVER)`) that registers per-resource interceptors based on the `@Timed` / `@Metered` annotations;
- `TimedInterceptor` — `ContainerRequestFilter` + `ContainerResponseFilter` pair that starts a `Timer.Context` on request and stops it on response;
- `MeterInterceptor` — `ContainerRequestFilter` that calls `meter.mark()` per request;
- Metric naming — explicit `name`/`absolute` annotation attributes are honored; otherwise the name is derived from the resource method and its path.

What it is not:

- Not a RESTEasy-specific runtime — it is plain JAX-RS 2.0 (`javax.ws.rs.container.*`), so it also works in other JAX-RS implementations that support `DynamicFeature`.

Typical scenarios:

| Scenario | What to use |
| :--- | :--- |
| Time every `@Timed` resource method | Register `MetricsFeature` as a provider |
| Count requests per `@Metered` resource method | Same feature; meter name derived from method + path |
| Explicit metric names | `@Timed(name = "...", absolute = true)` |

## 2. Features & Status

| Capability | Status | Notes |
| :--- | :--- | :--- |
| `@Timed` resource instrumentation | Implemented | `MetricsFeature` → `TimedInterceptor` (request/response filters) |
| `@Metered` resource instrumentation | Implemented | `MetricsFeature` → `MeterInterceptor` (request filter) |
| Explicit/absolute metric names | Implemented | `chooseName` honors `@Timed(name, absolute)` / `@Metered(name, absolute)` |
| Server-only enforcement | Implemented | `@ConstrainedTo(RuntimeType.SERVER)` on the feature |
| Tests | Not yet present | No `src/test` in this branch |

## 3. Requirements & Compatibility

| Item | Requirement |
| :--- | :--- |
| JDK | 21+ |
| Maven | 3.0+ (Maven Wrapper `mvnw` included) |
| Dependencies | resteasy-jaxrs 3.9.0.Final, metrics-core 4.1.1, metrics-annotation, metrics-healthchecks, guava 33.6.0-jre, slf4j-api 2.0.18, javax.servlet-api (provided), lombok (provided); junit 4.13.2 (test) |

Version lines:

| Branch | JDK | Version pattern |
| :--- | :---: | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` |
| `feature/2.0.x` | 17 | `2.0.x.*` |
| `feature/3.0.x` | 21 | `3.0.x.*` |

## 4. Architecture & Modules

```text
JAX-RS resource method (@Timed / @Metered)
        |
        v
MetricsFeature (DynamicFeature, @Provider, server-only)
        |
        +--> TimedInterceptor  (timer.time() ... context.stop())
        `--> MeterInterceptor  (meter.mark())
        |
        v
MetricRegistry (metrics-core 4.1.1)
```

Single-module jar with three classes in `com.codahale.metrics.resteasy`:

| Class | Role |
| :--- | :--- |
| `MetricsFeature` | `DynamicFeature`; registers the interceptors and resolves metric names |
| `TimedInterceptor` | request + response filters timing the invocation |
| `MeterInterceptor` | request filter marking the meter |

## 5. Installation

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>metrics-resteasy</artifactId>
    <version>3.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.easy4j:metrics-resteasy:3.0.x.x.20260630-SNAPSHOT'
```

The snapshot is served from the project's private repository (see `distributionManagement` in the pom). No Maven Central release is available yet.

## 6. Quick Start

Register the feature with your `MetricRegistry` and annotate a resource method:

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

// in your JAX-RS Application / RESTEasy setup:
MetricRegistry registry = new MetricRegistry();
singletons.add(new MetricsFeature(registry));   // registered as a JAX-RS provider
```

Every `GET /orders` call now updates a timer and a meter in `registry`; the metric name is derived from the resource method and its path (or from the explicit `name` when `absolute = true` is set).

## 7. Configuration

No property-file configuration. Behavior is driven by:

- the `@Timed` / `@Metered` annotations on resource methods (incl. `name` and `absolute`);
- the `MetricRegistry` instance passed to `new MetricsFeature(registry)`.

## 8. Core Usage / API

### 8.1 Explicit metric names

```java
@GET
@Timed(name = "orders.list", absolute = true)
public String list() { ... }
// records to registry.timer("orders.list") instead of the derived name
```

### 8.2 Standalone interceptors

The interceptors can be reused directly if you wire them yourself:

```java
Timer timer = registry.timer("custom");
TimedInterceptor interceptor = new TimedInterceptor(timer); // filter pair, no annotation needed
```

## 9. Testing & Build

```bash
./mvnw clean verify
```

The build is configured with:

- JUnit 4 + Maven Surefire (no test sources exist in this branch yet);
- JaCoCo coverage reporting plus a line-coverage check rule with a 90% minimum target (`haltOnFailure=false`);
- Source and Javadoc jars attached at package time;
- a `central` release profile (GPG signing + Central publishing) reserved for official releases.

## 10. Versioning & Branches

Three parallel version lines, each bound to a JDK baseline:

| Branch | JDK | Version pattern | Maintenance |
| :--- | :---: | :--- | :--- |
| `feature/1.0.x` | 8 | `1.0.x.*` | Current development line |
| `feature/2.0.x` | 17 | `2.0.x.*` | Maintained in parallel |
| `feature/3.0.x` | 21 | `3.0.x.*` | Maintained in parallel |

Snapshots on this branch are versioned `3.0.x.x.20260630-SNAPSHOT`.

## 11. Contributing & License

Contributions are welcome — open an issue or pull request on GitHub. All source files are licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.codahale.metrics.resteasy;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Joiner;

import javax.ws.rs.*;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Method;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * JAX-RS {@link DynamicFeature} that wires Dropwizard Metrics into a
 * RESTeasy-based application by inspecting resource methods for the
 * {@link Timed} and {@link Metered} annotations and registering the matching
 * interceptor.
 *
 * <p>For every resource method whose method-level metadata carries a
 * {@link Timed} annotation, a {@link Timer} is registered with the supplied
 * {@link MetricRegistry} and a {@link TimedInterceptor} bound to that timer
 * is added to the {@link FeatureContext}. The same pattern is followed for
 * the {@link Metered} annotation, which registers a {@link Meter} and a
 * {@link MeterInterceptor}.</p>
 *
 * <p>Meter/timer names follow the same convention as Dropwizard's
 * {@code MetricsFilter}: a name derived from the HTTP method and the JAX-RS
 * path is used when the annotation does not specify one explicitly. When the
 * annotation provides an explicit name and {@code absolute = false} (the
 * default), the explicit name is appended to the derived name. When
 * {@code absolute = true}, the explicit name is used as-is.</p>
 *
 * <p>The feature is annotated with
 * {@link ConstrainedTo}{@code (RuntimeType.SERVER)} so it is only
 * instantiated on the server side of JAX-RS.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see DynamicFeature
 * @see Timed
 * @see Metered
 * @see MetricRegistry
 */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class MetricsFeature implements DynamicFeature {

    /**
     * The Dropwizard {@link MetricRegistry} that owns all timers and meters
     * created by this feature. Held for the lifetime of the feature and never
     * {@code null}.
     */
    private final MetricRegistry registry;

    /**
     * Constructs a new feature that registers metrics into the supplied
     * {@link MetricRegistry}.
     *
     * @param registry the registry that will own the created timers and
     *                 meters; must not be {@code null}.
     */
    public MetricsFeature(MetricRegistry registry) {
        this.registry = registry;
    }

    /**
     * JAX-RS callback invoked once per resource method to register any
     * metric-collecting filters required by {@link Timed} or
     * {@link Metered} annotations on that method.
     *
     * <p>When the method is annotated with {@link Timed}, a timer with the
     * derived name is created (or fetched) from the registry and a
     * {@link TimedInterceptor} bound to it is registered with the context.
     * The same procedure applies for {@link Metered} and
     * {@link MeterInterceptor}. Methods that carry neither annotation are
     * left untouched.</p>
     *
     * @param resourceInfo provides reflective access to the matched resource
     *                     method and class; supplied by the JAX-RS runtime.
     * @param context       the per-method registration context supplied by
     *                     JAX-RS; modified in-place by this method.
     */
    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        Method resourceMethod = resourceInfo.getResourceMethod();
        if (resourceMethod.isAnnotationPresent(Timed.class)) {
            final Timed annotation = resourceMethod.getAnnotation(Timed.class);
            final String name = chooseName(annotation.name(), annotation.absolute(), resourceInfo);
            final Timer timer = registry.timer(name);
            context.register(new TimedInterceptor(timer));
        }

        if (resourceMethod.isAnnotationPresent(Metered.class)) {
            final Metered annotation = resourceMethod.getAnnotation(Metered.class);
            final String name = chooseName(annotation.name(), annotation.absolute(), resourceInfo);
            final Meter meter = registry.meter(name);
            context.register(new MeterInterceptor(meter));
        }
    }

    /**
     * Resolves the metric name to register, honouring the
     * {@code explicitName} and {@code absolute} attributes on the annotation
     * and falling back to the default
     * {@code "{HTTP_METHOD} - {ROOT_PATH}/{METHOD_PATH}"} convention.
     *
     * <p>When {@code explicitName} is non-empty and {@code absolute} is
     * {@code true}, the explicit name is used verbatim. When
     * {@code explicitName} is non-empty but {@code absolute} is {@code false}
     * the explicit name is combined with the derived base name via
     * {@link MetricRegistry#name(String, String...)}. Otherwise the base
     * name derived from {@link #getName(ResourceInfo)} is returned.</p>
     *
     * @param explicitName the name declared on the annotation, may be
     *                     {@code null} or empty.
     * @param absolute     whether the explicit name should be used verbatim.
     * @param resourceInfo the matched resource info used to derive the
     *                     default name.
     * @return the resolved metric name; never {@code null}.
     */
    private String chooseName(String explicitName, boolean absolute, ResourceInfo resourceInfo) {
        if (explicitName != null && !explicitName.isEmpty()) {
            if (absolute) {
                return explicitName;
            }
            return name(getName(resourceInfo), explicitName);
        }

        return getName(resourceInfo);
    }

    /**
     * Builds the default metric name from the resource method's HTTP verb
     * and its JAX-RS path.
     *
     * @param resourceInfo the matched resource info.
     * @return a string of the form {@code "GET - /root/path"}.
     */
    private String getName(ResourceInfo resourceInfo) {
        return getMethod(resourceInfo.getResourceMethod()) + " - " + getPath(resourceInfo);
    }

    /**
     * Resolves the JAX-RS path of the resource method by joining the
     * class-level {@link Path} (if present) and the method-level
     * {@link Path} (if present) with a forward slash. Null segments are
     * skipped so the result never contains {@code "//"}.
     *
     * @param resourceInfo the matched resource info.
     * @return the joined path, possibly empty if neither annotation is
     *         present.
     */
    private String getPath(ResourceInfo resourceInfo) {
        String rootPath = null;
        String methodPath = null;

        if (resourceInfo.getResourceClass().isAnnotationPresent(Path.class)) {
            rootPath = resourceInfo.getResourceClass().getAnnotation(Path.class).value();
        }

        if (resourceInfo.getResourceMethod().isAnnotationPresent(Path.class)) {
            methodPath = resourceInfo.getResourceMethod().getAnnotation(Path.class).value();
        }

        return Joiner.on("/").skipNulls().join(rootPath, methodPath);
    }

    /**
     * Determines the HTTP method annotation on the resource method and
     * returns its standard {@link HttpMethod} string constant.
     *
     * @param resourceMethod the matched resource method.
     * @return the HTTP method string such as {@code "GET"} or {@code "POST"}.
     * @throws IllegalStateException if the method carries no HTTP verb
     *                               annotation at all (i.e. none of
     *                               {@link GET}, {@link POST}, {@link PUT},
     *                               {@link DELETE}, {@link HEAD} or
     *                               {@link OPTIONS}).
     */
    private String getMethod(Method resourceMethod) {
        if (resourceMethod.isAnnotationPresent(GET.class)) {
            return HttpMethod.GET;
        }
        if (resourceMethod.isAnnotationPresent(POST.class)) {
            return HttpMethod.POST;
        }
        if (resourceMethod.isAnnotationPresent(PUT.class)) {
            return HttpMethod.PUT;
        }
        if (resourceMethod.isAnnotationPresent(DELETE.class)) {
            return HttpMethod.DELETE;
        }
        if (resourceMethod.isAnnotationPresent(HEAD.class)) {
            return HttpMethod.HEAD;
        }
        if (resourceMethod.isAnnotationPresent(OPTIONS.class)) {
            return HttpMethod.OPTIONS;
        }

        throw new IllegalStateException("Resource method without GET, POST, PUT, DELETE, HEAD or OPTIONS annotation");
    }
}
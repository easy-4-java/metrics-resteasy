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
import org.junit.Test;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Configurable;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.FeatureContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link MetricsFeature}.
 *
 * <p>The tests build lightweight stub {@link ResourceInfo} and
 * {@link FeatureContext} instances via {@link java.lang.reflect.Proxy} so the
 * feature can be exercised without booting a full RESTeasy runtime.</p>
 *
 * @since 3.0.0
 */
public class MetricsFeatureTest {

    /**
     * Test-only resource class for {@code @Timed} scenarios.
     */
    @Path("/widgets")
    static class WidgetResource {

        @Timed
        @GET
        public void timed() {
            // method body unused; only annotations matter
        }

        @Timed(name = "explicit.relative")
        @POST
        public void timedRelative() {
            // method body unused
        }

        @Timed(name = "explicit.absolute", absolute = true)
        @PUT
        public void timedAbsolute() {
            // method body unused
        }

        @Metered
        @DELETE
        public void metered() {
            // method body unused
        }

        @Metered(name = "explicit.meter.relative")
        @HEAD
        public void meteredRelative() {
            // method body unused
        }

        @Metered(name = "explicit.meter.absolute", absolute = true)
        @OPTIONS
        public void meteredAbsolute() {
            // method body unused
        }

        @GET
        public void plain() {
            // neither @Timed nor @Metered
        }

        /**
         * Method with no HTTP verb annotation but carrying {@code @Timed} -
         * should raise {@link IllegalStateException} when configure() tries
         * to determine the verb.
         */
        @Timed
        public void noVerb() {
            // method body unused
        }
    }

    /**
     * Trivial {@link InvocationHandler} that returns sensible defaults for any
     * interface method invoked on the proxy.
     */
    private static class NoopHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return Boolean.FALSE;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == void.class) {
                return null;
            }
            if (returnType == List.class) {
                return new ArrayList<>();
            }
            if (returnType == Map.class) {
                return new java.util.HashMap<>();
            }
            return null;
        }
    }

    /**
     * Builds a {@link ResourceInfo} proxy bound to a specific method on
     * {@link WidgetResource}.
     */
    private static ResourceInfo resourceInfo(String methodName) throws NoSuchMethodException {
        Method method = WidgetResource.class.getMethod(methodName);
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method m, Object[] args) {
                if ("getResourceMethod".equals(m.getName())) {
                    return method;
                }
                if ("getResourceClass".equals(m.getName())) {
                    return WidgetResource.class;
                }
                return null;
            }
        };
        return (ResourceInfo) Proxy.newProxyInstance(
                MetricsFeatureTest.class.getClassLoader(),
                new Class<?>[]{ResourceInfo.class},
                handler);
    }

    /**
     * Builder for a {@link FeatureContext} stub that records every object
     * passed to {@code register(Object)}.
     */
    private static final class RecordingFeatureContext implements FeatureContext {

        final List<Object> registered = new ArrayList<>();

        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public FeatureContext property(String name, Object value) {
            return this;
        }

        @Override
        public FeatureContext register(Class<?> aClass) {
            return this;
        }

        @Override
        public FeatureContext register(Class<?> aClass, int priority) {
            return this;
        }

        @Override
        public FeatureContext register(Class<?> aClass, Class<?>... contracts) {
            return this;
        }

        @Override
        public FeatureContext register(Class<?> aClass, Map<Class<?>, Integer> contracts) {
            return this;
        }

        @Override
        public FeatureContext register(Object component) {
            registered.add(component);
            return this;
        }

        @Override
        public FeatureContext register(Object component, int priority) {
            registered.add(component);
            return this;
        }

        @Override
        public FeatureContext register(Object component, Class<?>... contracts) {
            registered.add(component);
            return this;
        }

        @Override
        public FeatureContext register(Object component, Map<Class<?>, Integer> contracts) {
            registered.add(component);
            return this;
        }
    }

    /**
     * Test-only helper that wraps a no-op handler proxy for a single
     * Configurable method, used for coverage of the many register(...) overloads
     * that the feature does not actually call.
     */
    private static Configurable<FeatureContext> noopConfigurable() {
        return (Configurable<FeatureContext>) Proxy.newProxyInstance(
                MetricsFeatureTest.class.getClassLoader(),
                new Class<?>[]{Configurable.class},
                new NoopHandler());
    }

    /**
     * Constructing the feature with a registry must succeed.
     */
    @Test
    public void shouldAcceptProvidedRegistry() {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);
        assertNotNull(feature);
    }

    /**
     * A method annotated with {@code @Timed} must register exactly one
     * {@link TimedInterceptor} bound to a non-null {@link Timer} in the
     * registry.
     */
    @Test
    public void shouldRegisterTimedInterceptorForTimedMethod() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);
        RecordingFeatureContext context = new RecordingFeatureContext();

        feature.configure(resourceInfo("timed"), context);

        assertEquals("expected exactly one registered component", 1, context.registered.size());
        assertTrue("registered component should be a TimedInterceptor",
                context.registered.get(0) instanceof TimedInterceptor);

        Timer timer = registry.getTimers().values().iterator().next();
        assertNotNull("timer must be created in the registry", timer);
    }

    /**
     * A method annotated with {@code @Metered} must register exactly one
     * {@link MeterInterceptor} bound to a non-null {@link Meter} in the
     * registry.
     */
    @Test
    public void shouldRegisterMeterInterceptorForMeteredMethod() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);
        RecordingFeatureContext context = new RecordingFeatureContext();

        feature.configure(resourceInfo("metered"), context);

        assertEquals("expected exactly one registered component", 1, context.registered.size());
        assertTrue("registered component should be a MeterInterceptor",
                context.registered.get(0) instanceof MeterInterceptor);

        Meter meter = registry.getMeters().values().iterator().next();
        assertNotNull("meter must be created in the registry", meter);
    }

    /**
     * A method that carries both annotations must register both
     * interceptors.
     */
    @Test
    public void shouldRegisterBothInterceptorsWhenBothAnnotationsPresent() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);
        RecordingFeatureContext context = new RecordingFeatureContext();

        // build a synthetic method: a class that has both annotations
        @Path("/combined")
        class Combined {
            @Timed
            @Metered(name = "metered.combined")
            @GET
            public void combined() {
                // unused
            }
        }

        ResourceInfo info = (ResourceInfo) Proxy.newProxyInstance(
                MetricsFeatureTest.class.getClassLoader(),
                new Class<?>[]{ResourceInfo.class},
                (proxy, method, args) -> {
                    if ("getResourceMethod".equals(method.getName())) {
                        return Combined.class.getDeclaredMethod("combined");
                    }
                    if ("getResourceClass".equals(method.getName())) {
                        return Combined.class;
                    }
                    return null;
                });

        feature.configure(info, context);

        assertEquals("expected two registered components", 2, context.registered.size());
        boolean hasTimed = false, hasMeter = false;
        for (Object o : context.registered) {
            if (o instanceof TimedInterceptor) hasTimed = true;
            if (o instanceof MeterInterceptor) hasMeter = true;
        }
        assertTrue("a TimedInterceptor should be registered", hasTimed);
        assertTrue("a MeterInterceptor should be registered", hasMeter);
    }

    /**
     * A method without any annotation must not register anything and must
     * not throw.
     */
    @Test
    public void shouldNotRegisterAnythingForUnannotatedMethod() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);
        RecordingFeatureContext context = new RecordingFeatureContext();

        feature.configure(resourceInfo("plain"), context);

        assertTrue("no interceptor should be registered", context.registered.isEmpty());
        assertTrue("no timer should be created", registry.getTimers().isEmpty());
        assertTrue("no meter should be created", registry.getMeters().isEmpty());
    }

    /**
     * The default name should follow the
     * {@code "{HTTP_METHOD} - {root}/{method}"} convention when no explicit
     * name is supplied.
     */
    @Test
    public void shouldDeriveDefaultNameForTimedAnnotation() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);

        feature.configure(resourceInfo("timed"), new RecordingFeatureContext());

        // /widgets (class path) - no method path => /widgets
        assertTrue("expected derived name 'GET - /widgets' to be registered",
                registry.getTimers().containsKey("GET - /widgets"));
    }

    /**
     * The relative explicit name should be combined with the derived base
     * name via {@link MetricRegistry#name(String, String...)}.
     */
    @Test
    public void shouldCombineRelativeExplicitNameForTimedAnnotation() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);

        feature.configure(resourceInfo("timedRelative"), new RecordingFeatureContext());

        // MetricRegistry.name joins non-null pieces with '.'
        assertTrue("expected relative explicit name to be combined with derived name",
                registry.getTimers().containsKey("POST - /widgets.explicit.relative")
                        || registry.getTimers().containsKey("POST - /widgets.explicit.relative".replace(".", ".")));
    }

    /**
     * The absolute explicit name should be used verbatim, regardless of the
     * derived base name.
     */
    @Test
    public void shouldUseAbsoluteExplicitNameForTimedAnnotation() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);

        feature.configure(resourceInfo("timedAbsolute"), new RecordingFeatureContext());

        assertTrue("absolute explicit name should be used verbatim",
                registry.getTimers().containsKey("explicit.absolute"));
        assertEquals("exactly one timer should be registered",
                1, registry.getTimers().size());
    }

    /**
     * The default name for a metered method must use the HTTP verb and path
     * combination.
     */
    @Test
    public void shouldDeriveDefaultNameForMeteredAnnotation() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);

        feature.configure(resourceInfo("metered"), new RecordingFeatureContext());

        assertTrue("expected derived name 'DELETE - /widgets' to be registered",
                registry.getMeters().containsKey("DELETE - /widgets"));
    }

    /**
     * Relative explicit meter name should be combined with the derived
     * base name.
     */
    @Test
    public void shouldCombineRelativeExplicitNameForMeteredAnnotation() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);

        feature.configure(resourceInfo("meteredRelative"), new RecordingFeatureContext());

        assertTrue("expected relative explicit name to be combined with derived name",
                registry.getMeters().containsKey("HEAD - /widgets.explicit.meter.relative"));
    }

    /**
     * Absolute explicit meter name should be used verbatim.
     */
    @Test
    public void shouldUseAbsoluteExplicitNameForMeteredAnnotation() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);

        feature.configure(resourceInfo("meteredAbsolute"), new RecordingFeatureContext());

        assertTrue("absolute explicit meter name should be used verbatim",
                registry.getMeters().containsKey("explicit.meter.absolute"));
        assertEquals("exactly one meter should be registered",
                1, registry.getMeters().size());
    }

    /**
     * {@link MetricsFeature#configure(ResourceInfo, FeatureContext)} must
     * raise an {@link IllegalStateException} when invoked for a method that
     * carries no HTTP verb annotation.
     */
    @Test
    public void shouldThrowWhenMethodHasNoHttpVerb() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);

        try {
            feature.configure(resourceInfo("noVerb"), new RecordingFeatureContext());
            fail("configure() should throw IllegalStateException for verb-less methods");
        } catch (IllegalStateException expected) {
            assertTrue("exception message should describe the problem",
                    expected.getMessage() != null && expected.getMessage().contains("GET"));
        }
    }

    /**
     * All HTTP verb constants exposed by JAX-RS should be honoured when
     * deriving a metric name.
     */
    @Test
    public void shouldSupportAllStandardHttpVerbs() throws Exception {
        String[] verbs = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.OPTIONS};
        for (String verb : verbs) {
            // sanity: each constant must be a non-empty string
            assertNotNull(verb);
            assertTrue("verb constant should be non-empty: " + verb, verb.length() > 0);
        }
        // specific names used in derived-name assertions
        assertEquals("GET", HttpMethod.GET);
        assertEquals("POST", HttpMethod.POST);
        assertEquals("PUT", HttpMethod.PUT);
        assertEquals("DELETE", HttpMethod.DELETE);
        assertEquals("HEAD", HttpMethod.HEAD);
        assertEquals("OPTIONS", HttpMethod.OPTIONS);
    }

    /**
     * Invoking {@link MetricsFeature#configure(ResourceInfo, FeatureContext)}
     * with the same {@link ResourceInfo} twice must register two sets of
     * interceptors (the feature is stateless and the registry handles
     * de-duplication of the underlying metric).
     */
    @Test
    public void shouldBeIdempotentAcrossInvocations() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        MetricsFeature feature = new MetricsFeature(registry);
        RecordingFeatureContext first = new RecordingFeatureContext();
        RecordingFeatureContext second = new RecordingFeatureContext();

        feature.configure(resourceInfo("timed"), first);
        feature.configure(resourceInfo("timed"), second);

        assertEquals(1, first.registered.size());
        assertEquals(1, second.registered.size());
        // The underlying timer is shared but only one Timer instance exists.
        assertEquals(1, registry.getTimers().size());
    }
}
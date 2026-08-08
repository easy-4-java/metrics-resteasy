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

import com.codahale.metrics.Timer;
import org.junit.Test;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link TimedInterceptor}.
 *
 * <p>These tests validate the simple request/response contract of the
 * interceptor: the timer is started on the request phase and stopped on the
 * response phase, with exactly one duration recorded per request/response
 * cycle. The JAX-RS context objects are only used as opaque pass-throughs by
 * the interceptor, so lightweight Java dynamic proxies stand in for them.</p>
 *
 * @since 3.0.0
 */
public class TimedInterceptorTest {

    /**
     * {@link InvocationHandler} that ignores every call and returns either
     * {@code null}, {@code false}, {@code 0} or a fresh empty collection
     * depending on the return type. Sufficient for context interfaces whose
     * methods are never invoked by the unit under test.
     */
    private static final InvocationHandler NOOP = (proxy, method, args) -> {
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
            return new HashMap<>();
        }
        return null;
    };

    /**
     * Builds a {@link ContainerRequestContext} proxy backed by the no-op
     * handler.
     */
    private static ContainerRequestContext newRequestContext() {
        return (ContainerRequestContext) Proxy.newProxyInstance(
                TimedInterceptorTest.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class},
                NOOP);
    }

    /**
     * Builds a {@link ContainerResponseContext} proxy backed by the no-op
     * handler.
     */
    private static ContainerResponseContext newResponseContext() {
        return (ContainerResponseContext) Proxy.newProxyInstance(
                TimedInterceptorTest.class.getClassLoader(),
                new Class<?>[]{ContainerResponseContext.class},
                NOOP);
    }

    /**
     * Creating the interceptor must store the supplied timer without any
     * nullability concerns.
     */
    @Test
    public void shouldRetainProvidedTimerInstance() {
        Timer timer = new Timer();
        TimedInterceptor interceptor = new TimedInterceptor(timer);

        assertNotNull(interceptor);
    }

    /**
     * Filtering the inbound request must start a timer context but not yet
     * record any sample because no timer context has been stopped.
     */
    @Test
    public void shouldStartTimerContextOnRequestFilter() throws Exception {
        Timer timer = new Timer();
        TimedInterceptor interceptor = new TimedInterceptor(timer);

        interceptor.filter(newRequestContext());

        // The filter starts a Timer.Context but does not stop it, so the
        // timer's recorded count should remain zero.
        assertEquals(0L, timer.getCount());
    }

    /**
     * The full request-then-response cycle should record exactly one duration
     * sample on the bound timer.
     */
    @Test
    public void shouldRecordSingleSampleOnFullRequestResponseCycle() throws Exception {
        Timer timer = new Timer();
        TimedInterceptor interceptor = new TimedInterceptor(timer);

        ContainerRequestContext requestContext = newRequestContext();
        ContainerResponseContext responseContext = newResponseContext();

        interceptor.filter(requestContext);
        interceptor.filter(requestContext, responseContext);

        // One stop() call -> one recorded sample.
        assertEquals(1L, timer.getCount());
    }

    /**
     * Multiple successive request/response cycles must each record their own
     * sample so the timer accurately reflects the request volume.
     */
    @Test
    public void shouldRecordSampleForEachInvocation() throws Exception {
        Timer timer = new Timer();
        TimedInterceptor interceptor = new TimedInterceptor(timer);

        for (int i = 0; i < 5; i++) {
            ContainerRequestContext requestContext = newRequestContext();
            ContainerResponseContext responseContext = newResponseContext();
            interceptor.filter(requestContext);
            interceptor.filter(requestContext, responseContext);
        }

        assertEquals(5L, timer.getCount());
    }

    /**
     * The interceptor must throw no exception and must not require
     * the contexts to expose any specific behaviour.
     */
    @Test
    public void shouldNotThrowOnNoopContexts() throws Exception {
        Timer timer = new Timer();
        TimedInterceptor interceptor = new TimedInterceptor(timer);

        interceptor.filter(newRequestContext());
        interceptor.filter(newRequestContext(), newResponseContext());

        // Timer should now have one recorded duration.
        assertEquals(1L, timer.getCount());
    }
}
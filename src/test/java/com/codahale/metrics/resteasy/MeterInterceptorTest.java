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
import org.junit.Test;

import javax.ws.rs.container.ContainerRequestContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link MeterInterceptor}.
 *
 * <p>These tests confirm that the request filter delegates to
 * {@link Meter#mark()} exactly once per invocation and that repeated
 * invocations accumulate marks on the bound meter. The JAX-RS context is
 * used only as an opaque pass-through by the interceptor, so a Java
 * dynamic proxy stands in for it.</p>
 *
 * @since 3.0.0
 */
public class MeterInterceptorTest {

    /**
     * {@link InvocationHandler} that ignores every call.
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
                MeterInterceptorTest.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class},
                NOOP);
    }

    /**
     * Constructing the interceptor must succeed with a non-null meter.
     */
    @Test
    public void shouldRetainProvidedMeterInstance() {
        Meter meter = new Meter();
        MeterInterceptor interceptor = new MeterInterceptor(meter);

        assertNotNull(interceptor);
    }

    /**
     * A single invocation must mark the bound meter exactly once.
     */
    @Test
    public void shouldMarkMeterOncePerInvocation() throws Exception {
        Meter meter = new Meter();
        MeterInterceptor interceptor = new MeterInterceptor(meter);

        interceptor.filter(newRequestContext());

        assertEquals(1L, meter.getCount());
    }

    /**
     * Repeated invocations must each mark the meter so the total count
     * grows linearly with the number of filtered requests.
     */
    @Test
    public void shouldMarkMeterForEachInvocation() throws Exception {
        Meter meter = new Meter();
        MeterInterceptor interceptor = new MeterInterceptor(meter);

        int invocations = 10;
        for (int i = 0; i < invocations; i++) {
            interceptor.filter(newRequestContext());
        }

        assertEquals((long) invocations, meter.getCount());
    }

    /**
     * A filter call against a {@link ContainerRequestContext} implementation
     * that has no stubbed behaviour must not raise any exception &mdash; the
     * interceptor only touches the meter.
     */
    @Test
    public void shouldNotThrowOnNoopContext() throws Exception {
        Meter meter = new Meter();
        MeterInterceptor interceptor = new MeterInterceptor(meter);

        interceptor.filter(newRequestContext());

        assertEquals(1L, meter.getCount());
    }
}
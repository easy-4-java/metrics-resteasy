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

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;

/**
 * JAX-RS request filter that marks a Dropwizard {@link Meter} every time a
 * resource method is invoked.
 *
 * <p>This filter is registered by {@link MetricsFeature} when a resource
 * method is annotated with {@code @Metered}. It implements only the request
 * side of the JAX-RS filter contract because metering the inbound invocation
 * is sufficient &mdash; the Dropwizard meter then automatically maintains the
 * rolling count of calls per second, mean rate, etc.</p>
 *
 * <p>The interceptor holds no state of its own beyond the supplied meter;
 * each invocation simply calls {@link Meter#mark()} on the meter.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see Meter
 * @see MetricsFeature
 * @see ContainerRequestFilter
 */
public class MeterInterceptor implements ContainerRequestFilter {

    /**
     * The Dropwizard {@link Meter} that is marked on each invocation.
     * Held for the entire lifetime of this interceptor; never {@code null}.
     */
    private final Meter meter;

    /**
     * Constructs a new interceptor bound to the supplied {@link Meter}.
     *
     * @param meter the meter that will be marked on each request; must not be
     *              {@code null}.
     */
    public MeterInterceptor(Meter meter) {
        this.meter = meter;
    }

    /**
     * Marks the bound meter exactly once for every inbound request.
     *
     * <p>Implementing {@link ContainerRequestFilter#filter(ContainerRequestContext)}
     * means this method is invoked by the JAX-RS runtime before the matched
     * resource method executes. The recorded mark contributes to the meter's
     * one-minute, five-minute and fifteen-minute moving averages in addition
     * to its overall count and mean rate.</p>
     *
     * @param requestContext the inbound request context supplied by JAX-RS.
     * @throws IOException if the underlying I/O machinery fails; never thrown
     *                     by this implementation but required by the contract.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        meter.mark();
    }
}
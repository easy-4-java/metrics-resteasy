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

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * JAX-RS request/response interceptor that records the elapsed time of a
 * resource method invocation into a Dropwizard {@link Timer}.
 *
 * <p>This filter is registered by {@link MetricsFeature} when a resource
 * method is annotated with {@code @Timed}. On the inbound request it starts a
 * timer context via {@link Timer#time()} and on the outbound response it stops
 * the same context, automatically recording the latency of the operation.</p>
 *
 * <p>Because it implements both {@link ContainerRequestFilter} and
 * {@link ContainerResponseFilter}, JAX-RS guarantees that a single instance
 * will be shared between the matching request and response events for the
 * same resource invocation, so the {@code context} field can reliably be
 * used to correlate the timing start and stop.</p>
 *
 * @author easy-4-java contributors
 * @since 3.0.0
 * @see Timer
 * @see MetricsFeature
 * @see ContainerRequestFilter
 * @see ContainerResponseFilter
 */
@Provider
public class TimedInterceptor implements ContainerRequestFilter, ContainerResponseFilter {

    /**
     * The Dropwizard {@link Timer} that accumulates the recorded elapsed times.
     * Held for the entire lifetime of this interceptor; never {@code null}.
     */
    private final Timer timer;

    /**
     * The active timing context for the current request. Captured on the
     * request phase by {@link #filter(ContainerRequestContext)} and stopped on
     * the response phase by {@link #filter(ContainerRequestContext, ContainerResponseContext)}.
     * May be {@code null} until the request filter has executed.
     */
    private Timer.Context context;

    /**
     * Constructs a new interceptor bound to the supplied {@link Timer}.
     *
     * @param timer the timer used to record latencies; must not be {@code null}.
     */
    public TimedInterceptor(Timer timer) {
        this.timer = timer;
    }

    /**
     * Starts the timer context for the inbound request.
     *
     * <p>Implementing {@link ContainerRequestFilter#filter(ContainerRequestContext)}
     * means this method is invoked by the JAX-RS runtime before the matched
     * resource method is called.</p>
     *
     * @param requestContext the inbound request context supplied by JAX-RS.
     * @throws IOException if the underlying I/O machinery fails; never thrown
     *                     by this implementation but required by the contract.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        context = timer.time();
    }

    /**
     * Stops the timer context that was started during the request phase.
     *
     * <p>Implementing {@link ContainerResponseFilter#filter(ContainerRequestContext, ContainerResponseContext)}
     * means this method is invoked by the JAX-RS runtime after the matched
     * resource method has finished (either successfully or by throwing). The
     * recorded duration will be applied to the underlying {@link Timer}.</p>
     *
     * @param requestContext  the original inbound request context.
     * @param responseContext the outbound response context.
     * @throws IOException if the underlying I/O machinery fails; never thrown
     *                     by this implementation but required by the contract.
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        context.stop();
    }
}
/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.lang.Nullable;

import java.util.Collections;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * A gauge tracks a value that may go up or down. The value that is published for gauges is
 * an instantaneous sample of the gauge at publishing time.
 *
 * @author Jon Schneider
 */
public interface Gauge extends Meter {
    /**
     * @param name The gauge's name.
     * @param obj  An object with some state or function which the gauge's instantaneous value
     *             is determined from.
     * @param f    A function that yields a double value for the gauge, based on the state of
     *             {@code obj}.
     * @param <T>  The type of object to gauge.
     * @return A new gauge builder.
     */
    static <T> Builder<T> builder(String name, @Nullable T obj, ToDoubleFunction<T> f) {
        return new Builder<>(name, obj, f);
    }

    /**
     * A convenience method for building a gauge from a supplying function, holding a strong
     * reference to this function.
     *
     * @param name The gauge's name.
     * @param f    A function that yields a double value for the gauge.
     * @return A new gauge builder.
     * @since 1.1.0
     */
    @Incubating(since = "1.1.0")
    static Builder<Supplier<Number>> builder(String name, Supplier<Number> f) {
        return new Builder<>(name, f, f2 -> {
            Number val = f2.get();
            return val == null ? Double.NaN : val.doubleValue();
        }).strongReference(true);
    }

    /**
     * The act of observing the value by calling this method triggers sampling
     * of the underlying number or user-defined function that defines the value for the gauge.
     *
     * @return The current value.
     */
    double value();

    @Override
    default Iterable<Measurement> measure() {
        return Collections.singletonList(new Measurement(this::value, Statistic.VALUE));
    }

    /**
     * Fluent builder for gauges.
     *
     * @param <T> The type of the state object from which the gauge value is extracted.
     */
    class Builder<T> {
        private final String name;
        private final ToDoubleFunction<T> f;
        private Tags tags = Tags.empty();
        private boolean strongReference = false;

        @Nullable
        private Meter.Id syntheticAssociation = null;

        @Nullable
        private final T obj;

        @Nullable
        private String description;

        @Nullable
        private String baseUnit;

        private Builder(String name, @Nullable T obj, ToDoubleFunction<T> f) {
            this.name = name;
            this.obj = obj;
            this.f = f;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return The gauge builder with added tags.
         */
        public Builder<T> tags(String... tags) {
            return tags(Tags.of(tags));
        }

        /**
         * @param tags Tags to add to the eventual gauge.
         * @return The gauge builder with added tags.
         */
        public Builder<T> tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The gauge builder with a single added tag.
         */
        public Builder<T> tag(String key, String value) {
            this.tags = tags.and(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual gauge.
         * @return The gauge builder with added description.
         */
        public Builder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * @param unit Base unit of the eventual gauge.
         * @return The gauge builder with added base unit.
         */
        public Builder<T> baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        /**
         * For internal use. Marks a gauge as a derivative of another metric. For example, percentiles and
         * histogram gauges generated by {@link HistogramGauges} are derivatives of a {@link Timer} or
         * {@link DistributionSummary}.
         * <p>
         * This method may be removed in future minor or major releases if we find a way to mark derivatives in a
         * private way that does not have other API compatibility consequences.
         *
         * @param syntheticAssociation The meter id of a meter for which this metric is a synthetic derivative.
         * @return The gauge builder with an added synthetic association.
         */
        @Incubating(since = "1.1.0")
        public Builder<T> synthetic(Meter.Id syntheticAssociation) {
            this.syntheticAssociation = syntheticAssociation;
            return this;
        }

        /**
         * Indicates that the gauge should maintain a strong reference on the object upon which
         * its instantaneous value is determined.
         *
         * @param strong Whether or not to maintain a strong reference on the gauged object.
         * @return The gauge builder with altered strong reference semantics.
         * @since 1.1.0
         */
        @Incubating(since = "1.1.0")
        public Builder<T> strongReference(boolean strong) {
            this.strongReference = strong;
            return this;
        }

        /**
         * Add the gauge to a single registry, or return an existing gauge in that registry. The returned
         * gauge will be unique for each registry, but each registry is guaranteed to only create one gauge
         * for the same combination of name and tags.
         *
         * @param registry A registry to add the gauge to, if it doesn't already exist.
         * @return A new or existing gauge.
         */
        public Gauge register(MeterRegistry registry) {
            return registry.gauge(new Meter.Id(name, tags, baseUnit, description, Type.GAUGE, syntheticAssociation), obj,
                    strongReference ? new StrongReferenceGaugeFunction<>(obj, f) : f);
        }
    }
}

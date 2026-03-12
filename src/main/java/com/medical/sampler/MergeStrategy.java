package com.medical.sampler;

import java.util.function.BinaryOperator;

/**
 * Strategy that resolves two {@link Measurement} objects competing for the same
 * (type, interval) bucket during down-sampling.
 *
 * <p>Built-in strategies are exposed as static factory methods so call-sites read
 * naturally:
 * <pre>{@code
 *   new MeasurementSampler(MergeStrategy.last())
 *   new MeasurementSampler(MergeStrategy.first())
 *   new MeasurementSampler(MergeStrategy.average())
 * }</pre>
 */
@FunctionalInterface
public interface MergeStrategy extends BinaryOperator<Measurement> {

    /**
     * Keeps the measurement with the <em>later</em> timestamp.
     * Ties (equal timestamps) keep the existing value.
     * This is the default strategy described in the specification.
     */
    static MergeStrategy last() {
        return (existing, candidate) ->
                candidate.measurementTime().isAfter(existing.measurementTime())
                        ? candidate
                        : existing;
    }

    /**
     * Keeps the measurement with the <em>earlier</em> timestamp.
     * Ties (equal timestamps) keep the existing value.
     */
    static MergeStrategy first() {
        return (existing, candidate) ->
                candidate.measurementTime().isBefore(existing.measurementTime())
                        ? candidate
                        : existing;
    }

    /**
     * Returns a synthetic measurement whose value is the arithmetic mean of
     * {@code existing} and {@code candidate}.
     *
     * <p>The timestamp of the <em>later</em> of the two measurements is used so
     * the result stays within the correct interval boundary.
     *
     * <p>Note: the averaged value is a derived quantity;
     */
    static MergeStrategy average() {
        return (existing, candidate) -> {
            double avg = (existing.measurementValue() + candidate.measurementValue()) / 2.0;
            var laterTime = existing.measurementTime().isAfter(candidate.measurementTime())
                    ? existing.measurementTime()
                    : candidate.measurementTime();
            return new Measurement(laterTime, avg, existing.type());
        };
    }
}
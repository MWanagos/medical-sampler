package com.medical.sampler;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Samples second-precision medical measurements onto a fixed 5-minute time grid.
 */
public class MeasurementSampler {

    private static final long INTERVAL_SECONDS = 5 * 60L;

    /**
     * Samples the given measurements to a 5-minute time grid.
     *
     * <p>Measurements strictly before {@code startOfSampling} are ignored.
     *
     * @param startOfSampling       the reference start time for the grid; measurements before
     *                              this instant are discarded
     * @param unsampledMeasurements all raw measurements (may be unsorted, may be empty)
     * @return a map from each measurement type to its sampled, time-sorted list of measurements
     */
    public Map<MeasurementType, List<Measurement>> sample(
            Instant startOfSampling,
            List<Measurement> unsampledMeasurements) {

        var bestPerTypeAndInterval = group(startOfSampling, unsampledMeasurements);

        Map<MeasurementType, List<Measurement>> result = new EnumMap<>(MeasurementType.class);

        bestPerTypeAndInterval.forEach((type, intervalMap) ->
                result.put(type, intervalMap.values().stream()
                        .sorted(Comparator.comparing(Measurement::measurementTime))
                        .toList())
        );

        return result;
    }

    /**
     * Groups measurements by type and interval, keeping only the latest per group.
     *
     * @param startOfSampling       the reference start time; measurements before this are discarded
     * @param unsampledMeasurements raw measurements in any order
     * @return nested map: {@code type -> (intervalIndex -> latest measurement in that interval)}
     */
    private Map<MeasurementType, Map<Long, Measurement>> group(
            Instant startOfSampling,
            List<Measurement> unsampledMeasurements) {

        Map<MeasurementType, Map<Long, Measurement>> bestPerTypeAndInterval =
                new EnumMap<>(MeasurementType.class);

        for (var measurement : unsampledMeasurements) {
            if (measurement.measurementTime().isBefore(startOfSampling)) {
                continue;
            }

            var intervalIndex = computeIntervalIndex(startOfSampling, measurement.measurementTime());

            bestPerTypeAndInterval
                    .computeIfAbsent(measurement.type(), t -> new HashMap<>())
                    .merge(intervalIndex, measurement, MeasurementSampler::keepLater);
        }

        return bestPerTypeAndInterval;
    }

    /**
     * Computes which interval a given measurement time falls into,
     * relative to the sampling start.
     *
     * @param startOfSampling the grid reference point
     * @param measurementTime the timestamp to classify
     * @return a non-negative interval index
     */
    private static long computeIntervalIndex(Instant startOfSampling, Instant measurementTime) {
        long offsetSeconds = measurementTime.getEpochSecond() - startOfSampling.getEpochSecond();

        if (offsetSeconds > 0) {
            return (offsetSeconds - 1) / INTERVAL_SECONDS;
        } else {
            return 0;
        }
    }

    /**
     * Returns the measurement with the later timestamp.
     * Used as a merge function when two measurements fall in the same interval.
     *
     * @param existing  the measurement already stored for the interval
     * @param candidate the newly encountered measurement for the same interval
     * @return {@code candidate} if its timestamp is strictly after {@code existing}; {@code existing} otherwise
     */
    private static Measurement keepLater(Measurement existing, Measurement candidate) {
        return candidate.measurementTime().isAfter(existing.measurementTime())
                ? candidate
                : existing;
    }
}
package com.medical.sampler;

import java.time.Instant;

/**
 * Immutable data package representing a single medical device measurement.
 *
 * @param measurementTime  the exact second-precision timestamp of the measurement
 * @param measurementValue the measured value (e.g. 36.6 for temperature in °C)
 * @param type             the category of measurement (temperature, SpO2, heart rate, …)
 */
public record Measurement(
        Instant measurementTime,
        Double measurementValue,
        MeasurementType type
) {}
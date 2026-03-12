package com.medical.sampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static com.medical.sampler.MeasurementType.HEART_RATE;
import static com.medical.sampler.MeasurementType.SPO2;
import static com.medical.sampler.MeasurementType.TEMPERATURE;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MeasurementSampler")
class MeasurementSamplerTest {

    private MeasurementSampler sampler;

    @BeforeEach
    void setUp() {
        sampler = new MeasurementSampler();
    }

    private static Instant at(String time) {
        return Instant.parse("2026-03-12T" + time + "Z");
    }

    private static Measurement m(String time, MeasurementType type, double value) {
        return new Measurement(at(time), value, type);
    }

    private static final int FIVE_MINUTES_SECONDS = 5 * 60;

    private static List<Measurement> nMeasurementsInOneInterval(Instant base, MeasurementType type, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new Measurement(base.plusSeconds(i % FIVE_MINUTES_SECONDS), (double) i, type))
                .toList();
    }

    @Nested
    @DisplayName("given the example from the specification")
    class SpecificationExample {

        @Test
        @DisplayName("produces the exact expected output")
        void specExample() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:04:45", TEMPERATURE, 35.79),
                    m("10:01:18", SPO2, 98.78),
                    m("10:09:07", TEMPERATURE, 35.01),
                    m("10:03:34", SPO2, 96.49),
                    m("10:02:01", TEMPERATURE, 35.82),
                    m("10:05:00", SPO2, 97.17),
                    m("10:05:01", SPO2, 95.08)
            );

            // when
            var result = sampler.sample(start, input);

            // then
            assertThat(result.get(TEMPERATURE))
                    .containsExactly(
                            m("10:05:00", TEMPERATURE, 35.79),
                            m("10:10:00", TEMPERATURE, 35.01)
                    );

            assertThat(result.get(SPO2))
                    .containsExactly(
                            m("10:05:00", SPO2, 97.17),
                            m("10:10:00", SPO2, 95.08)
                    );
        }
    }

    @Nested
    @DisplayName("boundary behaviour")
    class BoundaryBehaviour {

        @Test
        @DisplayName("a measurement exactly on the grid boundary belongs to the current (ending) interval")
        void exactlyOnGridBoundaryBelongsToCurrentEndingInterval() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:03:00", TEMPERATURE, 36.0),
                    m("10:05:00", TEMPERATURE, 37.0)
            );

            // when
            var temps = sampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).containsExactly(m("10:05:00", TEMPERATURE, 37.0));
        }

        @Test
        @DisplayName("a measurement one second past the boundary starts a new interval")
        void oneSecondPastBoundaryStartsNewInterval() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:05:00", TEMPERATURE, 37.0),
                    m("10:05:01", TEMPERATURE, 38.0)
            );

            // when
            var temps = sampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).containsExactly(
                    m("10:05:00", TEMPERATURE, 37.0),
                    m("10:10:00", TEMPERATURE, 38.0)
            );
        }

        @Test
        @DisplayName("a measurement at the sampling start time belongs to the first interval")
        void measurementAtStartBelongsToFirstInterval() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:00:00", HEART_RATE, 72.0),
                    m("10:03:00", HEART_RATE, 75.0)
            );

            // when
            var rates = sampler.sample(start, input).get(HEART_RATE);

            // then
            assertThat(rates).containsExactly(m("10:05:00", HEART_RATE, 75.0));
        }
    }

    @Nested
    @DisplayName("sorting and ordering")
    class SortingAndOrdering {

        @Test
        @DisplayName("unsorted input produces time-sorted output")
        void unsortedInputProducesSortedOutput() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:14:59", TEMPERATURE, 36.3),
                    m("10:02:00", TEMPERATURE, 36.1),
                    m("10:07:00", TEMPERATURE, 36.2)
            );

            // when
            var temps = sampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps)
                    .extracting(Measurement::measurementTime)
                    .isSortedAccordingTo(Instant::compareTo);
        }

        @Test
        @DisplayName("multiple values in same interval: only latest survives")
        void onlyLatestValuePerIntervalSurvives() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:01:00", SPO2, 95.0),
                    m("10:04:59", SPO2, 97.0),
                    m("10:02:30", SPO2, 96.0)
            );

            // when
            var spo2 = sampler.sample(start, input).get(SPO2);

            // then
            assertThat(spo2).containsExactly(m("10:05:00", SPO2, 97.0));
        }
    }

    @Nested
    @DisplayName("multiple measurement types")
    class MultipleTypes {

        @Test
        @DisplayName("each type is sampled independently")
        void eachTypeSampledIndependently() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:02:00", TEMPERATURE, 36.5),
                    m("10:04:00", TEMPERATURE, 36.8),
                    m("10:02:00", SPO2, 94.0),
                    m("10:04:00", SPO2, 96.0),
                    m("10:06:00", HEART_RATE, 80.0)
            );

            // when
            var result = sampler.sample(start, input);

            // then
            assertThat(result.get(TEMPERATURE))
                    .containsExactly(m("10:05:00", TEMPERATURE, 36.8));

            assertThat(result.get(SPO2))
                    .containsExactly(m("10:05:00", SPO2, 96.0));

            assertThat(result.get(HEART_RATE))
                    .containsExactly(m("10:10:00", HEART_RATE, 80.0));
        }

        @Test
        @DisplayName("result map contains only types present in the input")
        void resultMapContainsOnlyPresentTypes() {
            // given
            var start = at("10:00:00");
            var input = List.of(m("10:02:00", TEMPERATURE, 36.5));

            // when
            var result = sampler.sample(start, input);

            // then
            assertThat(result).containsOnlyKeys(TEMPERATURE);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty input returns empty map")
        void emptyInputReturnsEmptyMap() {
            // given
            var start = at("10:00:00");

            // when
            var result = sampler.sample(start, List.of());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("single measurement is returned unchanged")
        void singleMeasurementReturnedUnchanged() {
            // given
            var start = at("10:00:00");
            var single = m("10:03:00", TEMPERATURE, 37.2);

            // when
            var result = sampler.sample(start, List.of(single));

            // then
            assertThat(result.get(TEMPERATURE)).containsExactly(m("10:05:00", TEMPERATURE, 37.2));
        }

        @Test
        @DisplayName("measurements spanning many intervals are all represented")
        void measurementsAcrossManyIntervalsAllRepresented() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:02:00", HEART_RATE, 60.0),
                    m("10:07:00", HEART_RATE, 61.0),
                    m("10:12:00", HEART_RATE, 62.0),
                    m("10:17:00", HEART_RATE, 63.0)
            );

            // when
            var rates = sampler.sample(start, input).get(HEART_RATE);

            // then
            assertThat(rates)
                    .hasSize(4)
                    .extracting(Measurement::measurementValue)
                    .containsExactly(60.0, 61.0, 62.0, 63.0);
        }

        @Test
        @DisplayName("duplicate timestamps: first encountered wins ties")
        void duplicateTimestampsHandled() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:03:00", SPO2, 95.0),
                    m("10:03:00", SPO2, 96.0)
            );

            // when
            var spo2 = sampler.sample(start, input).get(SPO2);

            // then
            assertThat(spo2).hasSize(1).containsExactly(m("10:05:00", SPO2, 95.0));
        }

        @Test
        @DisplayName("measurements before start of sampling are ignored")
        void measurementsBeforeStartAreIgnored() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("09:58:00", TEMPERATURE, 35.0),
                    m("09:59:59", TEMPERATURE, 36.0),
                    m("10:00:00", TEMPERATURE, 37.0),
                    m("10:03:00", TEMPERATURE, 38.0)
            );

            // when
            var temps = sampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).containsExactly(m("10:05:00", TEMPERATURE, 38.0));
        }

        @Test
        @DisplayName("large number of measurements in one interval: only the latest survives")
        void largeNumberOfMeasurementsInOneIntervalOnlyLatestSurvives() {
            // given
            var start = at("10:00:00");
            var noise = nMeasurementsInOneInterval(start, TEMPERATURE, 10_000);
            var latest = new Measurement(start.plusSeconds(FIVE_MINUTES_SECONDS), 999.9, TEMPERATURE);
            var input = Stream.concat(noise.stream(), Stream.of(latest)).toList();

            // when
            var temps = sampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).containsExactly(latest);
        }

        @Test
        @DisplayName("measurements with null value are stored as-is (value is not interpreted)")
        void nullMeasurementValueIsStoredAsIs() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    new Measurement(at("10:02:00"), null, TEMPERATURE)
            );

            // when
            var temps = sampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).hasSize(1);
            assertThat(temps.getFirst().measurementValue()).isNull();
        }
    }

    @Nested
    @DisplayName("grid boundary coverage")
    class GridBoundaryCoverage {

        @Test
        @DisplayName("single measurement exactly on each of several grid boundaries is kept per interval")
        void singleMeasurementOnEachGridBoundaryIsKeptPerInterval() {
            // given
            var start = at("10:00:00");
            var input = List.of(
                    m("10:05:00", SPO2, 91.0),
                    m("10:10:00", SPO2, 92.0),
                    m("10:15:00", SPO2, 93.0)
            );

            // when
            var spo2 = sampler.sample(start, input).get(SPO2);

            // then
            assertThat(spo2).containsExactly(
                    m("10:05:00", SPO2, 91.0),
                    m("10:10:00", SPO2, 92.0),
                    m("10:15:00", SPO2, 93.0)
            );
        }
    }

    @Nested
    @DisplayName("MergeStrategy variants")
    class MergeStrategyVariants {

        @Test
        @DisplayName("MergeStrategy.last() keeps the measurement with the later timestamp")
        void lastStrategyKeepsLaterTimestamp() {
            // given
            var lastSampler = new MeasurementSampler(MergeStrategy.last());
            var start = at("10:00:00");
            var input = List.of(
                    m("10:01:00", TEMPERATURE, 36.0),
                    m("10:04:00", TEMPERATURE, 37.0),
                    m("10:02:00", TEMPERATURE, 35.0)
            );

            // when
            var temps = lastSampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).containsExactly(m("10:05:00", TEMPERATURE, 37.0));
        }

        @Test
        @DisplayName("MergeStrategy.first() keeps the measurement with the earlier timestamp")
        void firstStrategyKeepsEarlierTimestamp() {
            // given
            var firstSampler = new MeasurementSampler(MergeStrategy.first());
            var start = at("10:00:00");
            var input = List.of(
                    m("10:04:00", TEMPERATURE, 37.0),
                    m("10:01:00", TEMPERATURE, 36.0),
                    m("10:02:00", TEMPERATURE, 35.0)
            );

            // when
            var temps = firstSampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).containsExactly(m("10:05:00", TEMPERATURE, 36.0));
        }

        @Test
        @DisplayName("MergeStrategy.first() with duplicate timestamps keeps the first encountered")
        void firstStrategyTieKeepsExisting() {
            // given
            var firstSampler = new MeasurementSampler(MergeStrategy.first());
            var start = at("10:00:00");
            var input = List.of(
                    m("10:03:00", SPO2, 95.0),
                    m("10:03:00", SPO2, 99.0)
            );

            // when
            var spo2 = firstSampler.sample(start, input).get(SPO2);

            // then
            assertThat(spo2).hasSize(1);
            assertThat(spo2.getFirst().measurementValue()).isEqualTo(95.0);
        }

        @Test
        @DisplayName("MergeStrategy.average() returns the arithmetic mean of two values")
        void averageStrategyReturnsMean() {
            // given
            var averageSampler = new MeasurementSampler(MergeStrategy.average());
            var start = at("10:00:00");
            var input = List.of(
                    m("10:01:00", TEMPERATURE, 36.0),
                    m("10:03:00", TEMPERATURE, 38.0)
            );

            // when
            var temps = averageSampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).hasSize(1);
            assertThat(temps.getFirst().measurementValue()).isEqualTo(37.0);
        }

        @Test
        @DisplayName("MergeStrategy.average() uses the later timestamp for the result")
        void averageStrategyUsesLaterTimestamp() {
            // given
            var averageSampler = new MeasurementSampler(MergeStrategy.average());
            var start = at("10:00:00");
            var input = List.of(
                    m("10:01:00", TEMPERATURE, 36.0),
                    m("10:03:00", TEMPERATURE, 38.0)
            );

            // when
            var temps = averageSampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps.getFirst().measurementTime()).isEqualTo(at("10:05:00"));
        }

        @Test
        @DisplayName("MergeStrategy.average() accumulates correctly across three values in one interval")
        void averageStrategyAccumulatesAcrossMultipleValues() {
            // given
            var averageSampler = new MeasurementSampler(MergeStrategy.average());
            var start = at("10:00:00");

            var input = List.of(
                    m("10:01:00", TEMPERATURE, 36.0),
                    m("10:02:00", TEMPERATURE, 38.0),
                    m("10:03:00", TEMPERATURE, 37.0)
            );

            // when
            var temps = averageSampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).hasSize(1);
        }

        @Test
        @DisplayName("MergeStrategy.average() across two intervals computes means independently")
        void averageStrategyComputesMeansPerIntervalIndependently() {
            // given
            var averageSampler = new MeasurementSampler(MergeStrategy.average());
            var start = at("10:00:00");
            var input = List.of(
                    m("10:01:00", TEMPERATURE, 30.0),
                    m("10:03:00", TEMPERATURE, 40.0),
                    m("10:06:00", TEMPERATURE, 20.0),
                    m("10:08:00", TEMPERATURE, 60.0)
            );

            // when
            var temps = averageSampler.sample(start, input).get(TEMPERATURE);

            // then
            assertThat(temps).hasSize(2);
            assertThat(temps.get(0).measurementValue()).isEqualTo(35.0);
            assertThat(temps.get(1).measurementValue()).isEqualTo(40.0);
        }

        @Test
        @DisplayName("null mergeStrategy throws IllegalArgumentException")
        void nullMergeStrategyThrows() {
            // then
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new MeasurementSampler(null)
            );
        }

        @Test
        @DisplayName("custom lambda MergeStrategy (always higher value) is applied correctly")
        void customLambdaMergeStrategyApplied() {
            // given
            MergeStrategy highest = (existing, candidate) ->
                    candidate.measurementValue() > existing.measurementValue() ? candidate : existing;
            var highValueSampler = new MeasurementSampler(highest);
            var start = at("10:00:00");
            var input = List.of(
                    m("10:01:00", HEART_RATE, 55.0),
                    m("10:04:00", HEART_RATE, 90.0),
                    m("10:03:00", HEART_RATE, 70.0)
            );

            // when
            var rates = highValueSampler.sample(start, input).get(HEART_RATE);

            // then
            assertThat(rates).containsExactly(m("10:05:00", HEART_RATE, 90.0));
        }
    }
}
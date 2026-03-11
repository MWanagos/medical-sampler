package com.medical.sampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Instant at(String time) {
        return Instant.parse("2026-03-12T" + time + "Z");
    }

    private static Measurement m(String time, MeasurementType type, double value) {
        return new Measurement(at(time), value, type);
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

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
                            m("10:04:45", TEMPERATURE, 35.79),
                            m("10:09:07", TEMPERATURE, 35.01)
                    );

            assertThat(result.get(SPO2))
                    .containsExactly(
                            m("10:05:00", SPO2, 97.17),
                            m("10:05:01", SPO2, 95.08)
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
                    m("10:05:01", TEMPERATURE, 38.0)
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
            assertThat(rates).containsExactly(m("10:03:00", HEART_RATE, 75.0));
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
            assertThat(spo2).containsExactly(m("10:04:59", SPO2, 97.0));
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
                    .containsExactly(m("10:04:00", TEMPERATURE, 36.8));

            assertThat(result.get(SPO2))
                    .containsExactly(m("10:04:00", SPO2, 96.0));

            assertThat(result.get(HEART_RATE))
                    .containsExactly(m("10:06:00", HEART_RATE, 80.0));
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
            assertThat(result.get(TEMPERATURE)).containsExactly(single);
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
            assertThat(spo2).hasSize(1);
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
            assertThat(temps).containsExactly(m("10:03:00", TEMPERATURE, 38.0));
        }
    }
}
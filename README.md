# Medical Device Measurement Sampler

A Java 21 library that down-samples second-precision medical measurements onto a 5-minute time grid.

## Problem

Medical devices produce continuous streams of measurements (temperature, SpO2, heart rate, etc.)
at second-level precision. This library reduces that data to one representative value per
5-minute window per measurement type. The default strategy keeps the **last** value observed
in each window, but this is fully configurable via `MergeStrategy`.

## Sampling Rules

| Rule              | Description                                                                       |
|-------------------|-----------------------------------------------------------------------------------|
| Per-type sampling | Each measurement type (TEMPERATURE, SPO2, HEART_RATE) is sampled independently    |
| Last value wins   | Within a 5-minute interval, only the latest measurement is kept (default)         |
| Boundary rule     | A value exactly on the grid boundary belongs to the **current** (ending) interval |
| Unsorted input    | Input does not need to be sorted — the algorithm handles any order                |
| Sorted output     | Results are returned sorted ascending by time                                     |
| Before-start skip | Measurements strictly before `startOfSampling` are discarded                     |

## Example

```
startOfSampling = 2026-03-12T10:00:00Z

Input (unsorted):
  2026-03-12T10:04:45Z  TEMPERATURE  35.79
  2026-03-12T10:01:18Z  SPO2         98.78
  2026-03-12T10:09:07Z  TEMPERATURE  35.01
  2026-03-12T10:03:34Z  SPO2         96.49
  2026-03-12T10:02:01Z  TEMPERATURE  35.82
  2026-03-12T10:05:00Z  SPO2         97.17   ← exactly on boundary → current interval
  2026-03-12T10:05:01Z  SPO2         95.08

Output (sampled, sorted):
  TEMPERATURE:
    2026-03-12T10:04:45Z  35.79   (last TEMP in [10:00 – 10:05])
    2026-03-12T10:09:07Z  35.01   (last TEMP in (10:05 – 10:10])
  SPO2:
    2026-03-12T10:05:00Z  97.17   (last SPO2 in [10:00 – 10:05])
    2026-03-12T10:05:01Z  95.08   (last SPO2 in (10:05 – 10:10])
```

## Project Structure

```
medical-sampler/
├── pom.xml
└── src/
    ├── main/java/com/medical/sampler/
    │   ├── MeasurementType.java       # Enum of supported measurement types
    │   ├── Measurement.java           # Immutable value object (time, value, type)
    │   ├── MeasurementSampler.java    # Core sampling algorithm
    │   └── MergeStrategy.java         # Strategy interface for resolving interval conflicts
    └── test/java/com/medical/sampler/
        └── MeasurementSamplerTest.java  # Tests across boundary, sorting, types, edge cases, and merge strategies
```

## API

### `MeasurementSampler`

```java
// Default constructor — uses MergeStrategy.last()
new MeasurementSampler()

// Custom strategy constructor
new MeasurementSampler(MergeStrategy mergeStrategy)

public Map<MeasurementType, List<Measurement>> sample(
        Instant startOfSampling,
        List<Measurement> unsampledMeasurements);
```

- **`startOfSampling`** — reference point for the grid (grid ticks at +5 min, +10 min, …); measurements strictly before this instant are discarded
- **`unsampledMeasurements`** — raw measurements in any order
- **Returns** — map from each present type to its time-sorted sampled measurements; types with no qualifying measurements are absent from the map

### `Measurement`

```java
public record Measurement(
    Instant measurementTime,
    Double  measurementValue,
    MeasurementType type
) {}
```

An immutable value object. `measurementValue` may be `null` — the sampler stores it as-is without interpreting the value.

### `MeasurementType`

```java
public enum MeasurementType {
   TEMPERATURE,
   SPO2,
   HEART_RATE
}
```

Add new device measurement categories here as required.

### `MergeStrategy`

A `@FunctionalInterface` that resolves two `Measurement` objects competing for the same `(type, interval)` bucket. It extends `BinaryOperator<Measurement>`, so any lambda or method reference is a valid strategy.

Three built-in factory methods are provided:

```java
MergeStrategy.last()     // keeps the measurement with the later timestamp  (default)
MergeStrategy.first()    // keeps the measurement with the earlier timestamp
MergeStrategy.average()  // returns a synthetic measurement whose value is the arithmetic mean;
// the later timestamp is used for the result
```

Custom strategies are supported via lambda:

```java
// Example: always keep the higher measured value
MergeStrategy keepHighest = (existing, candidate) ->
                candidate.measurementValue() > existing.measurementValue() ? candidate : existing;

new MeasurementSampler(keepHighest).sample(start, measurements);
```

## How It Works

The algorithm runs in two passes:

1. **Group & reduce** — each measurement is assigned an interval index using:
   ```
   offsetSeconds = measurementTime - startOfSampling   (in seconds)

   if offsetSeconds > 0  →  intervalIndex = (offsetSeconds - 1) / INTERVAL_SECONDS
   else                  →  intervalIndex = 0
   ```
   The `- 1` shift ensures boundary-exact timestamps fall into the ending (current) interval.
   Measurements at exactly `startOfSampling` (`offsetSeconds == 0`) are placed in interval `0`.
   Within each `(type, intervalIndex)` bucket, `Map.merge()` invokes the configured `MergeStrategy`
   with `(existing, candidate)` — the already-stored value is always the first argument, which
   determines tie-breaking behaviour in `first()` and `last()`.

2. **Sort & collect** — surviving measurements per type are sorted ascending by time.

Time complexity: **O(n log n)** — dominated by the final sort.  
Space complexity: **O(n)**.

## Running

```bash
# Run all tests
mvn test
```

## Requirements

- Java 21
- Maven 3.8+
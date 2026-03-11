# Medical Device Measurement Sampler

A Java 21 library that down-samples second-precision medical measurements onto a 5-minute time grid.

## Problem

Medical devices produce continuous streams of measurements (temperature, SpO2, heart rate, etc.)
at second-level precision. This library reduces that data to one representative value per
5-minute window per measurement type — specifically, the **last** value observed in each window.

## Sampling Rules

| Rule              | Description                                                                       |
|-------------------|-----------------------------------------------------------------------------------|
| Per-type sampling | Each measurement type (TEMPERATURE, SPO2, HEART_RATE) is sampled independently    |
| Last value wins   | Within a 5-minute interval, only the chronologically latest measurement is kept   |
| Boundary rule     | A value exactly on the grid boundary belongs to the **current** (ending) interval |
| Pre-start discard | Measurements strictly before `startOfSampling` are silently ignored               |
| Unsorted input    | Input does not need to be sorted — the algorithm handles any order                |
| Sorted output     | Results are returned sorted ascending by time                                     |

## Interval Model

Given start `S` and interval length `L = 5 min`, interval `n` covers the half-open range:

```
(S + n·L , S + (n+1)·L]
```

The boundary point closes the **current** (ending) interval. `startOfSampling` itself belongs
to interval `0`. For second-precision timestamps the index is computed as:

```
intervalIndex = (offsetSeconds - 1) / L    for offset > 0
intervalIndex = 0                           for offset = 0  (exactly at start)
```

The `- 1` shift ensures boundary-exact timestamps fall into the ending interval.

## Example

```
startOfSampling = 2026-03-12T10:00:00Z

Input (unsorted):
  2026-03-12T10:04:45Z  TEMPERATURE  35.79
  2026-03-12T10:01:18Z  SPO2         98.78
  2026-03-12T10:09:07Z  TEMPERATURE  35.01
  2026-03-12T10:03:34Z  SPO2         96.49
  2026-03-12T10:02:01Z  TEMPERATURE  35.82
  2026-03-12T10:05:00Z  SPO2         97.17   ← exactly on boundary → current interval (10:00–10:05]
  2026-03-12T10:05:01Z  SPO2         95.08

Output (sampled, sorted):
  TEMPERATURE:
    2026-03-12T10:04:45Z  35.79   (last TEMP in (10:00:00 – 10:05:00])
    2026-03-12T10:09:07Z  35.01   (last TEMP in (10:05:00 – 10:10:00])
  SPO2:
    2026-03-12T10:05:00Z  97.17   (last SPO2 in (10:00:00 – 10:05:00])
    2026-03-12T10:05:01Z  95.08   (last SPO2 in (10:05:00 – 10:10:00])
```

## Project Structure

```
medical-sampler/
├── pom.xml
└── src/
    ├── main/java/com/medical/sampler/
    │   ├── MeasurementType.java       # Enum of supported measurement types
    │   ├── Measurement.java           # Immutable value object (time, value, type)
    │   └── MeasurementSampler.java    # Core sampling algorithm
    └── test/java/com/medical/sampler/
        └── MeasurementSamplerTest.java  # 13 tests across 5 scenarios
```

## API

```java
public Map<MeasurementType, List<Measurement>> sample(
    Instant startOfSampling,
    List<Measurement> unsampledMeasurements);
```

- **`startOfSampling`** — reference point for the grid (grid ticks at +5min, +10min, …);
  measurements strictly before this instant are discarded
- **`unsampledMeasurements`** — raw measurements in any order; may be empty
- **Returns** — map from each present type to its time-sorted sampled measurements;
  types not present in the input are absent from the result map

## How It Works

The algorithm runs in two steps:

1. **Group & reduce** — iterate once over the input; for each measurement compute its interval
   index and insert it into a `(type, intervalIndex)` bucket, keeping only the later timestamp
   on collision via `Map.merge`. Measurements before `startOfSampling` are skipped.

2. **Sort & collect** — for each type, stream the bucket values and sort ascending by time.

Time complexity: **O(n log n)** — dominated by the final sort.
Space complexity: **O(n)**.

Both outer maps (`type → intervals` and the result) use `EnumMap` for efficient,
enum-keyed access. The inner `intervalIndex → Measurement` map uses `HashMap`.

## Running

```bash
# Run all tests
mvn test
```

## Requirements

- Java 21
- Maven 3.8+
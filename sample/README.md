# Sample Program Import

This folder contains a sample JSON payload for importing a workout program.

## Files

- `program_emanuel_mazzilli_5.json`: structured import payload.
- `program_emanuel_mazzilli_5.extracted.txt`: raw text extracted from source PDF for verification.

## JSON shape

```json
{
  "program": {
    "name": "string",
    "prehab_markdown": "string",
    "number_of_weeks": 7,
    "unload_week": false,
    "trainings": [
      {
        "name": "string",
        "exercises": [
          {
            "exercise_type": {
              "name": "string",
              "youtube_video": "string|null",
              "notes": "string|null"
            },
            "intensity_type": "none|rest_pause_2x|stripping_2x|negativa_3s",
            "rest_seconds": 90,
            "weeks": [
              {
                "week": 1,
                "set_repetitions": [
                  {
                    "set_count": 3,
                    "repetitions_min": 8,
                    "repetitions_max": 10
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}
```

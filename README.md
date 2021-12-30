# ClimateAdjuster

Allows patching biome climate data with a custom configuration. Schema for config/climateadjuster/climate_data.json is:

```typescript
{
    "namespace:biomename": { // optional
        "temperature": number | null, // optional. See <https://minecraft.gamepedia.com/Biome#Temperature>. Range: [-0.5, 2.0]
        "temperatureModifier": "none" | "frozen", // optional.
        "downfall": number | null, // optional. Range: [0.0, 1.0]
        "precipitation": "none" | "rain" | "snow" | null // optional.
    }
}
```

If using Serene Seasons, prefer the "rain" precipitation type.

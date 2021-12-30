package cofl.climateadjuster;

import com.google.common.collect.Maps;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Mod(ClimateAdjuster.CLIMATEADJUSTER)
public final class ClimateAdjuster
{
    private static final Logger LOGGER = LogManager.getLogger();
    static final String CLIMATEADJUSTER = "climateadjuster";

    private final Map<ResourceLocation, ClimateData> climateData;
    public ClimateAdjuster() {
        LOGGER.info("Instantiating ClimateAdjuster.");

        var configPath = FMLPaths.CONFIGDIR.get();
        var modConfigPath = Paths.get(configPath.toAbsolutePath().toString(), CLIMATEADJUSTER);
        if(!Files.exists(modConfigPath)) {
            try {
                Files.createDirectory(modConfigPath);
            } catch (IOException e) {
                LOGGER.error("Failed to create climateadjuster config directory.", e);
            }
        }

        var modConfigFile = Paths.get(modConfigPath.toAbsolutePath().toString(), "climate_data.json");
        climateData = getConfig(modConfigFile.toFile());

        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::patchBiomeData);
    }

    private void patchBiomeData(final BiomeLoadingEvent event) {
        var biomeName = event.getName();
        if(climateData.containsKey(biomeName)){
            var data = climateData.get(biomeName);
            if(data.hasChanges())
                LOGGER.info("Patching climate data for " + biomeName);
            else {
                LOGGER.info("Skipped empty climate data for " + biomeName);
                return;
            }

            event.setClimate(data.toClimate(event.getClimate()));
        } else {
            LOGGER.debug("No ClimateAdjuster configuration for biome " + biomeName);
        }
    }

    private Map<ResourceLocation, ClimateData> getConfig(File configFile){
        Map<ResourceLocation, ClimateData> climateData = Maps.newHashMap();
        var gson = new GsonBuilder()
                .registerTypeAdapter(Biome.Precipitation.class, new RainTypeAdapter())
                .registerTypeAdapter(Biome.TemperatureModifier.class, new TemperatureModifierAdapter())
                .setPrettyPrinting()
                .create();
        try {
            if(!configFile.exists())
                FileUtils.write(configFile, gson.toJson(new HashMap<String, ClimateData>()), StandardCharsets.UTF_8);
            var data = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            var type = new TypeToken<Map<String,ClimateData>>(){}.getType();
            Map<String, ClimateData> map = gson.fromJson(data, type);
            if(null != map && !map.isEmpty())
                for (var entry: map.entrySet())
                    climateData.put(new ResourceLocation(entry.getKey()), entry.getValue().clamp());
        } catch (IOException e){
            LOGGER.error("Error with config: " + configFile.getAbsolutePath(), e);
            return null;
        }
        return climateData;
    }
}

final class ClimateData {
    @SerializedName("temperature")
    public Float temperature;

    @SerializedName("downfall")
    public Float downfall;

    @SerializedName("temperatureModifier")
    public Biome.TemperatureModifier temperatureModifier;

    @SerializedName("precipitation")
    public Biome.Precipitation precipitation;

    public ClimateData(Biome.Precipitation precipitation, Float temperature, Biome.TemperatureModifier temperatureModifier, Float downfall) {
        this.precipitation = precipitation;
        this.temperature = temperature;
        this.temperatureModifier = temperatureModifier;
        this.downfall = downfall;
    }

    boolean hasChanges(){
        return null != temperature || null != downfall || null != precipitation;
    }
    ClimateData clamp(){
        if(null != temperature)
            temperature = Math.min(2.0F, Math.max(-0.5F, temperature));
        if(null != downfall)
            downfall = Math.min(0.0F, Math.max(1.0F, downfall));
        return this;
    }
    Biome.ClimateSettings toClimate(Biome.ClimateSettings defaultClimate) {
        return new Biome.ClimateSettings(
                null == precipitation ? defaultClimate.precipitation : precipitation,
                null == temperature ? defaultClimate.temperature : temperature,
                null == temperatureModifier ? defaultClimate.temperatureModifier : temperatureModifier,
                null == downfall ? defaultClimate.downfall : downfall
        );
    }
}

final class RainTypeAdapter implements JsonSerializer<Biome.Precipitation>, JsonDeserializer<Biome.Precipitation> {
    public Biome.Precipitation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Biome.Precipitation.byName(json.getAsString());
    }

    public JsonElement serialize(Biome.Precipitation src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getName());
    }
}

final class TemperatureModifierAdapter implements JsonSerializer<Biome.TemperatureModifier>, JsonDeserializer<Biome.TemperatureModifier> {
    public Biome.TemperatureModifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Biome.TemperatureModifier.byName(json.getAsString());
    }

    public JsonElement serialize(Biome.TemperatureModifier src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getName());
    }
}

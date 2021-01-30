package cofl.climateadjuster;

import com.google.common.collect.Maps;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Mod("climateadjuster")
public final class ClimateAdjuster
{
    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<ResourceLocation, ClimateData> climateData;
    public ClimateAdjuster() {
        LOGGER.info("Instantiating ClimateAdjuster.");

        Path configPath = FMLPaths.CONFIGDIR.get();
        Path modConfigPath = Paths.get(configPath.toAbsolutePath().toString(), "climateadjuster");
        if(!Files.exists(modConfigPath)) {
            try {
                Files.createDirectory(modConfigPath);
            } catch (IOException e) {
                LOGGER.error("Failed to create climateadjuster config directory.", e);
            }
        }

        Path modConfigFile = Paths.get(modConfigPath.toAbsolutePath().toString(), "climate_data.json");
        climateData = getConfig(modConfigFile.toFile());

        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::patchBiomeData);
    }

    private void patchBiomeData(final BiomeLoadingEvent event) {
        ResourceLocation biomeName = event.getName();
        if(climateData.containsKey(biomeName)){
            ClimateData data = climateData.get(biomeName);
            if(data.noChanges()){
                LOGGER.info("Skipped empty climate data for " + biomeName);
                return;
            } else {
                LOGGER.info("Patching climate data for " + biomeName);
            }

            Biome.Climate currentClimate = event.getClimate();
            Biome.Climate newClimate = new Biome.Climate(
                    null == data.precipitation ? currentClimate.precipitation : data.precipitation,
                    null == data.temperature ? currentClimate.temperature : data.temperature,
                    currentClimate.temperatureModifier,
                    null == data.downfall ? currentClimate.downfall : data.downfall);
            event.setClimate(newClimate);
        } else {
            LOGGER.debug("No ClimateAdjuster configuration for biome " + biomeName);
        }
    }

    private Map<ResourceLocation, ClimateData> getConfig(File configFile){
        Map<ResourceLocation, ClimateData> climateData = Maps.newHashMap();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Biome.RainType.class, new RainTypeAdapter())
                .setPrettyPrinting()
                .create();
        try {
            if(!configFile.exists())
                FileUtils.write(configFile, gson.toJson(new HashMap<String, ClimateData>()), StandardCharsets.UTF_8);
            String data = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String,ClimateData>>(){}.getType();
            Map<String, ClimateData> tmp = gson.fromJson(data, type);
            if(tmp != null && !tmp.isEmpty())
                for (Map.Entry<String, ClimateData> entry: tmp.entrySet())
                    climateData.put(new ResourceLocation(entry.getKey()), entry.getValue().clamp());
        } catch (IOException e){
            LOGGER.error("Error with config: " + configFile.getAbsolutePath(), e);
            return null;
        }
        return climateData;
    }
}

class ClimateData {
    @SerializedName("temperature")
    public Float temperature;

    @SerializedName("downfall")
    public Float downfall;

    @SerializedName("precipitation")
    public Biome.RainType precipitation;

    public ClimateData(Biome.RainType precipitation, Float temperature, Float downfall) {
        this.temperature = temperature;
        this.downfall = downfall;
        this.precipitation = precipitation;
    }

    boolean noChanges() {
        return temperature == null && downfall == null && precipitation == null;
    }
    ClimateData clamp(){
        if(null != temperature)
            temperature = Math.min(2.0F, Math.max(-0.5F, temperature));
        if(null != downfall)
            downfall = Math.min(0.0F, Math.max(1.0F, downfall));
        return this;
    }
}

class RainTypeAdapter implements JsonSerializer<Biome.RainType>, JsonDeserializer<Biome.RainType> {
    public Biome.RainType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return Biome.RainType.getRainType(json.getAsJsonPrimitive().getAsString());
    }

    public JsonElement serialize(Biome.RainType src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getName());
    }
}

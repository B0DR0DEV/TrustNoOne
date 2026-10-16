package ru.bodrodev.trustnoone.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.bodrodev.trustnoone.TrustNoOneMod;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON settings for the complete TrustNoOne gameplay loop. */
public final class TrustNoOneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("trustnoone.json");

    public Lives lives = new Lives();
    public Imposter imposter = new Imposter();
    public Names names = new Names();
    public Darkness darkness = new Darkness();
    public Sins sins = new Sins();
    public EyeOfEnder eyeOfEnder = new EyeOfEnder();

    public static TrustNoOneConfig load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    TrustNoOneConfig loaded = GSON.fromJson(reader, TrustNoOneConfig.class);
                    if (loaded != null) {
                        loaded.sanitize();
                        return loaded;
                    }
                }
            }
        } catch (Exception e) {
            TrustNoOneMod.LOGGER.error("Could not read config {}; using defaults", FILE, e);
        }
        TrustNoOneConfig defaults = new TrustNoOneConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            TrustNoOneMod.LOGGER.error("Could not write config {}", FILE, e);
        }
    }

    private void sanitize() {
        if (lives == null) lives = new Lives();
        if (imposter == null) imposter = new Imposter();
        if (names == null) names = new Names();
        if (darkness == null) darkness = new Darkness();
        if (sins == null) sins = new Sins();
        if (eyeOfEnder == null) eyeOfEnder = new EyeOfEnder();
        lives.startingLives = clamp(lives.startingLives, 1, 99);
        lives.partnerHealthLoss = clamp(lives.partnerHealthLoss, 0, 20);
        lives.reviveHealth = clamp(lives.reviveHealth, 1, 20);
        lives.reviveTicks = clamp(lives.reviveTicks, 1, 200);
        imposter.spawnIntervalTicks = clamp(imposter.spawnIntervalTicks, 100, 24000);
        imposter.spawnChancePercent = clamp(imposter.spawnChancePercent, 0, 100);
        imposter.minDistance = clamp(imposter.minDistance, 6, 64);
        imposter.maxDistance = clamp(imposter.maxDistance, imposter.minDistance, 96);
        imposter.noticeDot = clamp(imposter.noticeDot, 0.5, 0.999);
        imposter.despawnDistance = clamp(imposter.despawnDistance, 32, 512);
        darkness.lightThreshold = clamp(darkness.lightThreshold, 0, 15);
        darkness.intensity = clamp(darkness.intensity, 0, 3);
        darkness.visibilityRadiusChunks = clamp(darkness.visibilityRadiusChunks, 2, 32);
        sins.mobBuffThreshold = clamp(sins.mobBuffThreshold, 1, 1000);
        sins.unluckThreshold = clamp(sins.unluckThreshold, 1, 1000);
        sins.armorWearThreshold = clamp(sins.armorWearThreshold, 1, 1000);
        sins.armorWearIntervalTicks = clamp(sins.armorWearIntervalTicks, 20, 24000);
        eyeOfEnder.endermanDropChance = clamp(eyeOfEnder.endermanDropChance, 0.0, 1.0);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    public static final class Lives {
        public int startingLives = 3;
        /** How many health points the surviving partner loses when someone dies. */
        public int partnerHealthLoss = 2;
        public int reviveHealth = 2;
        public int reviveTicks = 40;
    }

    public static final class Imposter {
        public boolean enabled = true;
        public int spawnIntervalTicks = 1200;
        public int spawnChancePercent = 18;
        public int minDistance = 12;
        public int maxDistance = 26;
        /** Dot product between view direction and direction to the imposter required to freeze it. */
        public double noticeDot = 0.94;
        public boolean openDoors = true;
        public boolean copyCrouchingAndSprinting = true;
        public boolean copyJumping = true;
        public int despawnDistance = 160;
    }

    public static final class Names {
        public boolean hidePlayerNames = true;
        public boolean hideImposterNames = true;
    }

    public static final class Darkness {
        public boolean enabled = true;
        public boolean darkenNights = true;
        public boolean darkenCaves = true;
        public int lightThreshold = 7;
        /** 0 = normal Darkness effect; greater values intensify duration/amplifier behaviour. */
        public int intensity = 0;
        public int visibilityRadiusChunks = 8;
        public boolean showSunAndMoon = true;
    }

    public static final class Sins {
        public boolean enabled = true;
        public int mobBuffThreshold = 3;
        public int unluckThreshold = 2;
        public int armorWearThreshold = 4;
        public int armorWearIntervalTicks = 600;
    }

    public static final class EyeOfEnder {
        public boolean enabled = true;
        public double endermanDropChance = 0.02;
    }
}

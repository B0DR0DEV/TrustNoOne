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
    public NightHunters nightHunters = new NightHunters();
    public GhostPlayer ghostPlayer = new GhostPlayer();
    public StrangeChat strangeChat = new StrangeChat();
    public RandomLightning randomLightning = new RandomLightning();
    public SinGod sinGod = new SinGod();
    public Observer observer = new Observer();
    public MimicFootsteps mimicFootsteps = new MimicFootsteps();
    public HomeHaunting homeHaunting = new HomeHaunting();
    public FakeFriendMessages fakeFriendMessages = new FakeFriendMessages();
    public GodPremonitions godPremonitions = new GodPremonitions();

    /** Serializes the current settings for server-to-client synchronization in LAN games. */
    public String toJson() {
        return GSON.toJson(this);
    }

    /** Parses and sanitizes server-provided settings without writing them to the local config file. */
    public static TrustNoOneConfig fromJson(String json) {
        try {
            TrustNoOneConfig loaded = GSON.fromJson(json, TrustNoOneConfig.class);
            if (loaded == null) loaded = new TrustNoOneConfig();
            loaded.sanitize();
            return loaded;
        } catch (Exception e) {
            TrustNoOneMod.LOGGER.warn("Could not parse synchronized TrustNoOne config; using defaults", e);
            return new TrustNoOneConfig();
        }
    }

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
        if (nightHunters == null) nightHunters = new NightHunters();
        if (ghostPlayer == null) ghostPlayer = new GhostPlayer();
        if (strangeChat == null) strangeChat = new StrangeChat();
        if (randomLightning == null) randomLightning = new RandomLightning();
        if (sinGod == null) sinGod = new SinGod();
        if (observer == null) observer = new Observer();
        if (mimicFootsteps == null) mimicFootsteps = new MimicFootsteps();
        if (homeHaunting == null) homeHaunting = new HomeHaunting();
        if (fakeFriendMessages == null) fakeFriendMessages = new FakeFriendMessages();
        if (godPremonitions == null) godPremonitions = new GodPremonitions();
        lives.startingLives = clamp(lives.startingLives, 1, 99);
        lives.partnerHealthLoss = clamp(lives.partnerHealthLoss, 0, 20);
        lives.reviveHealth = clamp(lives.reviveHealth, 1, 20);
        lives.reviveTicks = clamp(lives.reviveTicks, 1, 200);
        lives.reviveLookDot = clamp(lives.reviveLookDot, 0.2, 0.99);
        imposter.spawnIntervalTicks = clamp(imposter.spawnIntervalTicks, 100, 24000);
        imposter.spawnChancePercent = clamp(imposter.spawnChancePercent, 0, 100);
        imposter.minDistance = clamp(imposter.minDistance, 6, 64);
        imposter.maxDistance = clamp(imposter.maxDistance, imposter.minDistance, 96);
        imposter.noticeDot = clamp(imposter.noticeDot, 0.5, 0.999);
        imposter.despawnDistance = clamp(imposter.despawnDistance, 32, 512);
        imposter.huntDelayTicks = clamp(imposter.huntDelayTicks, 40, 2400);
        imposter.fakeEatDurationTicks = clamp(imposter.fakeEatDurationTicks, 8, 100);
        darkness.lightThreshold = clamp(darkness.lightThreshold, 0, 15);
        darkness.visibilityRadiusChunks = clamp(darkness.visibilityRadiusChunks, 2, 32);
        darkness.nightOverlayAlpha = clamp(darkness.nightOverlayAlpha, 0, 80);
        darkness.caveOverlayAlpha = clamp(darkness.caveOverlayAlpha, 0, 80);
        sins.mobBuffThreshold = clamp(sins.mobBuffThreshold, 1, 1000);
        sins.unluckThreshold = clamp(sins.unluckThreshold, 1, 1000);
        sins.armorWearThreshold = clamp(sins.armorWearThreshold, 1, 1000);
        sins.armorWearIntervalTicks = clamp(sins.armorWearIntervalTicks, 20, 24000);
        eyeOfEnder.endermanDropChance = clamp(eyeOfEnder.endermanDropChance, 0.0, 1.0);
        nightHunters.spawnIntervalTicks = clamp(nightHunters.spawnIntervalTicks, 100, 24000);
        nightHunters.spawnChancePercent = clamp(nightHunters.spawnChancePercent, 0, 100);
        nightHunters.minDistance = clamp(nightHunters.minDistance, 6, 64);
        nightHunters.maxDistance = clamp(nightHunters.maxDistance, nightHunters.minDistance, 96);
        nightHunters.maxNearby = clamp(nightHunters.maxNearby, 1, 12);
        nightHunters.health = clamp(nightHunters.health, 10.0, 80.0);
        nightHunters.movementSpeed = clamp(nightHunters.movementSpeed, 0.20, 0.60);
        ghostPlayer.firstDelayTicks = clamp(ghostPlayer.firstDelayTicks, 200, 24000);
        ghostPlayer.minIntervalTicks = clamp(ghostPlayer.minIntervalTicks, 600, 24000);
        ghostPlayer.maxIntervalTicks = clamp(ghostPlayer.maxIntervalTicks, ghostPlayer.minIntervalTicks, 48000);
        ghostPlayer.chancePercent = clamp(ghostPlayer.chancePercent, 0, 100);
        strangeChat.minIntervalTicks = clamp(strangeChat.minIntervalTicks, 200, 24000);
        strangeChat.maxIntervalTicks = clamp(strangeChat.maxIntervalTicks, strangeChat.minIntervalTicks, 48000);
        strangeChat.chancePercent = clamp(strangeChat.chancePercent, 0, 100);
        randomLightning.intervalTicks = clamp(randomLightning.intervalTicks, 200, 24000);
        randomLightning.chancePercent = clamp(randomLightning.chancePercent, 0, 100);
        sinGod.sinThreshold = clamp(sinGod.sinThreshold, 1, 1000);
        sinGod.baseHealth = clamp(sinGod.baseHealth, 100.0, 2000.0);
        sinGod.healthPerSin = clamp(sinGod.healthPerSin, 0.0, 500.0);
        sinGod.maxHealth = clamp(sinGod.maxHealth, sinGod.baseHealth, 5000.0);
        sinGod.escapeDistance = clamp(sinGod.escapeDistance, 16.0, 256.0);
        sinGod.escapeDamage = clamp(sinGod.escapeDamage, 2.0, 40.0);
        sinGod.escapeCooldownTicks = clamp(sinGod.escapeCooldownTicks, 20, 1200);
        observer.intervalTicks = clamp(observer.intervalTicks, 400, 24000);
        observer.chancePercent = clamp(observer.chancePercent, 0, 100);
        observer.minDistance = clamp(observer.minDistance, 12, 48);
        observer.maxDistance = clamp(observer.maxDistance, observer.minDistance, 64);
        mimicFootsteps.checkIntervalTicks = clamp(mimicFootsteps.checkIntervalTicks, 200, 24000);
        mimicFootsteps.chancePercent = clamp(mimicFootsteps.chancePercent, 0, 100);
        mimicFootsteps.minSteps = clamp(mimicFootsteps.minSteps, 1, 10);
        mimicFootsteps.maxSteps = clamp(mimicFootsteps.maxSteps, mimicFootsteps.minSteps, 14);
        homeHaunting.intervalTicks = clamp(homeHaunting.intervalTicks, 600, 48000);
        homeHaunting.chancePercent = clamp(homeHaunting.chancePercent, 0, 100);
        homeHaunting.radius = clamp(homeHaunting.radius, 4, 16);
        fakeFriendMessages.intervalTicks = clamp(fakeFriendMessages.intervalTicks, 600, 48000);
        fakeFriendMessages.chancePercent = clamp(fakeFriendMessages.chancePercent, 0, 100);
        godPremonitions.omenDurationTicks = clamp(godPremonitions.omenDurationTicks, 20, 200);
    }


    /** A distant player-shaped observer that vanishes after someone notices it. */
    public static final class Observer {
        public boolean enabled = true;
        public int intervalTicks = 2200;
        public int chancePercent = 38;
        public int minDistance = 22;
        public int maxDistance = 32;
    }

    /** Footsteps that pace behind a moving player and stop when that player stops. */
    public static final class MimicFootsteps {
        public boolean enabled = true;
        public int checkIntervalTicks = 1000;
        public int chancePercent = 38;
        public int minSteps = 3;
        public int maxSteps = 5;
    }

    /** Strange house events are only considered near a bed in the Overworld. */
    public static final class HomeHaunting {
        public boolean enabled = true;
        public int intervalTicks = 6000;
        public int chancePercent = 42;
        public int radius = 8;
        public boolean openNearbyDoor = true;
    }

    /** Role-play chat messages that imitate a friend, followed by an unsettling correction. */
    public static final class FakeFriendMessages {
        public boolean enabled = true;
        public int intervalTicks = 3600;
        public int chancePercent = 38;
    }

    /** Ominous hints and a brief distant Wither silhouette before the fifth sin. */
    public static final class GodPremonitions {
        public boolean enabled = true;
        public int omenDurationTicks = 70;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    public static final class Lives {
        public int startingLives = 3;
        /** How many health points the surviving partner loses when someone dies. */
        public int partnerHealthLoss = 2;
        public int reviveHealth = 2;
        public int reviveTicks = 40;
        /** Lets a one-player world test revival and the Mimic. Multiplayer always requires a partner. */
        public boolean singleplayerTestingMode = true;
        /** Minimum look-direction dot product used by the server to validate the aimed body. */
        public double reviveLookDot = 0.72;
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
        /** How long the player must leave the Mimic unseen before it stops imitating and hunts. */
        public int huntDelayTicks = 240;
        /** How long the fake food remains in its hand during a visual eating animation. */
        public int fakeEatDurationTicks = 32;
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
        /** Subtle, static black overlay; alpha is clamped to a gentle maximum of 80. */
        public int nightOverlayAlpha = 20;
        public int caveOverlayAlpha = 36;
        public int visibilityRadiusChunks = 10;
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

    /** Fast hostile mobs that begin hunting players during the Overworld night. */
    public static final class NightHunters {
        public boolean enabled = true;
        public int spawnIntervalTicks = 900;
        public int spawnChancePercent = 45;
        public int minDistance = 12;
        public int maxDistance = 23;
        public int maxNearby = 3;
        public double health = 24.0;
        public double movementSpeed = 0.34;
    }

    /** A Wither boss summoned by the fifth sin and strengthened by every later sin. */
    public static final class SinGod {
        public boolean enabled = true;
        public int sinThreshold = 5;
        public double baseHealth = 600.0;
        public double healthPerSin = 80.0;
        public double maxHealth = 1200.0;
        public double escapeDistance = 48.0;
        public double escapeDamage = 8.0;
        public int escapeCooldownTicks = 100;
    }

    /** Fake join/leave announcements followed by a short in-world horror story. */
    public static final class GhostPlayer {
        public boolean enabled = true;
        public int firstDelayTicks = 1200;
        public int minIntervalTicks = 2400;
        public int maxIntervalTicks = 4800;
        public int chancePercent = 55;
    }

    /** Unreadable red chat whispers. */
    public static final class StrangeChat {
        public boolean enabled = true;
        public int minIntervalTicks = 1000;
        public int maxIntervalTicks = 2200;
        public int chancePercent = 60;
    }

    /** Rare cosmetic lightning flashes near, but never on, a player. */
    public static final class RandomLightning {
        public boolean enabled = true;
        public int intervalTicks = 900;
        public int chancePercent = 35;
    }
}

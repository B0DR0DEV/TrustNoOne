package ru.bodrodev.trustnoone.world;

import net.minecraft.datafixer.DataFixTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/** Saved round state. It lives in the world save, rather than in process memory. */
public final class TrustNoOneWorldState extends PersistentState {
    public static final Codec<TrustNoOneWorldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("lives", 3).forGetter(s -> s.lives),
            Codec.INT.optionalFieldOf("sin_level", 0).forGetter(s -> s.sinLevel),
            Codec.BOOL.optionalFieldOf("game_over", false).forGetter(s -> s.gameOver),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(s -> s.initialized)
    ).apply(instance, TrustNoOneWorldState::new));

    public static final PersistentStateType<TrustNoOneWorldState> TYPE = new PersistentStateType<>(
            "trustnoone_round", TrustNoOneWorldState::new, CODEC, DataFixTypes.LEVEL);

    private int lives = 3;
    private int sinLevel = 0;
    private boolean gameOver = false;
    private boolean initialized = false;

    public TrustNoOneWorldState() { }

    private TrustNoOneWorldState(int lives, int sinLevel, boolean gameOver, boolean initialized) {
        this.lives = Math.max(0, lives);
        this.sinLevel = Math.max(0, sinLevel);
        this.gameOver = gameOver;
        this.initialized = initialized;
    }

    public void initializeIfNeeded(int startingLives) {
        if (!initialized) {
            lives = Math.max(1, startingLives);
            initialized = true;
            markDirty();
        }
    }

    public int getLives() { return lives; }
    public int getSinLevel() { return sinLevel; }
    public boolean isGameOver() { return gameOver; }

    public void setLives(int value) {
        lives = Math.max(0, value);
        markDirty();
    }

    public void addSin() {
        sinLevel = Math.min(100_000, sinLevel + 1);
        markDirty();
    }

    public void setGameOver(boolean value) {
        gameOver = value;
        markDirty();
    }
}

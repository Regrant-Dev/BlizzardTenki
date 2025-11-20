package com.regrantdev.blizzardwizard;

import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

// Persistent storage for BlizzAura block positions
// Saves to world/data folder so positions persist across server restarts
public class BlizzAuraSavedData extends SavedData {

    // Codec for serializing the set of BlockPos
    private static final Codec<Set<BlockPos>> POSITIONS_CODEC =
        Codec.list(BlockPos.CODEC).xmap(HashSet::new, list -> list.stream().toList());

    // SavedDataType for this saved data
    public static final SavedDataType<BlizzAuraSavedData> TYPE = new SavedDataType<>(
        "blizzardtenki_blizzaura_positions",  // Identifier
        BlizzAuraSavedData::new,  // Default constructor
        RecordCodecBuilder.create(instance -> instance.group(
            POSITIONS_CODEC.fieldOf("positions").forGetter(data -> data.positions)
        ).apply(instance, BlizzAuraSavedData::new))  // Codec
    );

    private Set<BlockPos> positions;

    // Default constructor (for new data)
    public BlizzAuraSavedData() {
        this.positions = new HashSet<>();
    }

    // Constructor from codec (for loading)
    public BlizzAuraSavedData(Set<BlockPos> positions) {
        this.positions = new HashSet<>(positions);
    }

    // Get positions
    public Set<BlockPos> getPositions() {
        return new HashSet<>(positions);
    }

    // Add a position
    public void addPosition(BlockPos pos) {
        positions.add(pos);
        setDirty(); // Mark as needing save
    }

    // Remove a position
    public void removePosition(BlockPos pos) {
        positions.remove(pos);
        setDirty(); // Mark as needing save
    }

    // Get saved data for a world (or create new if doesn't exist)
    public static BlizzAuraSavedData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }
}

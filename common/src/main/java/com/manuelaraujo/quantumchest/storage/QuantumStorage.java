package com.manuelaraujo.quantumchest.storage;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One storage in the world: the items plus who owns them. Several chests can
 * point at the same storage (that is how linking will work), and the storage
 * survives when its chest is broken.
 */
public final class QuantumStorage {
    public static final Codec<QuantumStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(QuantumStorage::id),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(s -> Optional.ofNullable(s.owner)),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(QuantumStorage::ownerName),
            AccessMode.CODEC.optionalFieldOf("access", AccessMode.PRIVATE).forGetter(QuantumStorage::accessMode),
            UUIDUtil.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(s -> List.copyOf(s.members)),
            ChestTier.CODEC.optionalFieldOf("tier", ChestTier.BASIC).forGetter(QuantumStorage::tier),
            QuantumInventory.Entry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(s -> s.inventory.entryList())
    ).apply(i, QuantumStorage::new));

    private final UUID id;
    private final QuantumInventory inventory = new QuantumInventory();
    private final Set<UUID> members = new HashSet<>();
    private UUID owner;
    private String ownerName;
    private AccessMode accessMode;
    // Tier of the chest that last showed this storage, so a recovered chest comes back at the same tier
    private ChestTier tier;
    private Runnable onChange = () -> {
    };

    QuantumStorage(UUID id) {
        this(id, Optional.empty(), "", AccessMode.PRIVATE, List.of(), ChestTier.BASIC, List.of());
    }

    private QuantumStorage(UUID id, Optional<UUID> owner, String ownerName, AccessMode accessMode,
                           List<UUID> members, ChestTier tier, List<QuantumInventory.Entry> items) {
        this.id = id;
        this.owner = owner.orElse(null);
        this.ownerName = ownerName;
        this.accessMode = accessMode;
        this.members.addAll(members);
        this.tier = tier;
        this.inventory.load(items);
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Call after touching the inventory so the world saves it. */
    public void markChanged() {
        onChange.run();
    }

    public UUID id() {
        return id;
    }

    public QuantumInventory inventory() {
        return inventory;
    }

    public boolean hasOwner() {
        return owner != null;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getName().getString();
        markChanged();
    }

    public AccessMode accessMode() {
        return accessMode;
    }

    public void setAccessMode(AccessMode accessMode) {
        this.accessMode = accessMode;
        markChanged();
    }

    public ChestTier tier() {
        return tier;
    }

    public void setTier(ChestTier tier) {
        if (this.tier != tier) {
            this.tier = tier;
            markChanged();
        }
    }

    public boolean isPublic() {
        return accessMode == AccessMode.PUBLIC;
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    public void addMember(UUID player) {
        if (members.add(player)) markChanged();
    }

    public void removeMember(UUID player) {
        if (members.remove(player)) markChanged();
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** Open the chest, put things in and take them out. */
    public boolean canUse(Player player) {
        return canUse(owner, accessMode == AccessMode.PUBLIC, members, player.getUUID()) || isOperator(player);
    }

    /**
     * Break the chest and carry it off. Same people who can use it: the
     * contents always travel inside the dropped chest, nothing spills.
     */
    public boolean canBreak(Player player) {
        return canUse(player);
    }

    /** Change permissions (and later: upgrade, link). Owner and operators only. */
    public boolean canManage(Player player) {
        return owner == null || isOwner(player) || isOperator(player);
    }

    /** Shared with the client, which only has the synced copy of these fields. */
    public static boolean canUse(UUID owner, boolean isPublic, Set<UUID> members, UUID player) {
        return owner == null || owner.equals(player) || isPublic || members.contains(player);
    }

    private static boolean isOperator(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && Commands.LEVEL_GAMEMASTERS.check(serverPlayer.permissions());
    }
}

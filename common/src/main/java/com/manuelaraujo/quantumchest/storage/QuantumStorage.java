package com.manuelaraujo.quantumchest.storage;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One storage in the world: the items, who owns them, who else may use them
 * and the custom tabs. Several chests can point at the same storage (that is
 * how linking will work), and the storage survives when its chest is broken.
 */
public final class QuantumStorage {
    public static final int MAX_MEMBERS = 12;

    public static final Codec<QuantumStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(QuantumStorage::id),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(s -> Optional.ofNullable(s.owner)),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(QuantumStorage::ownerName),
            AccessMode.CODEC.optionalFieldOf("access", AccessMode.PRIVATE).forGetter(QuantumStorage::accessMode),
            Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(QuantumStorage::memberList),
            ChestTier.CODEC.optionalFieldOf("tier", ChestTier.BASIC).forGetter(QuantumStorage::tier),
            CustomTab.CODEC.listOf().optionalFieldOf("tabs", List.of()).forGetter(QuantumStorage::tabs),
            QuantumInventory.Entry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(s -> s.inventory.entryList())
    ).apply(i, QuantumStorage::new));

    private final UUID id;
    private final QuantumInventory inventory = new QuantumInventory();
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final List<CustomTab> tabs = new ArrayList<>();
    private UUID owner;
    private String ownerName;
    private AccessMode accessMode;
    // Tier of the chest that last showed this storage, so a recovered chest comes back at the same tier
    private ChestTier tier;
    // Bumped when owner, access, members, tier or tabs change, so open screens resync them
    private int stateVersion;
    private Runnable onChange = () -> {
    };

    QuantumStorage(UUID id) {
        this(id, Optional.empty(), "", AccessMode.PRIVATE, List.of(), ChestTier.BASIC, List.of(), List.of());
    }

    private QuantumStorage(UUID id, Optional<UUID> owner, String ownerName, AccessMode accessMode,
                           List<Member> members, ChestTier tier, List<CustomTab> tabs,
                           List<QuantumInventory.Entry> items) {
        this.id = id;
        this.owner = owner.orElse(null);
        this.ownerName = ownerName;
        this.accessMode = accessMode;
        members.forEach(m -> this.members.put(m.id(), m.name()));
        this.tier = tier;
        this.tabs.addAll(tabs);
        this.inventory.load(items);
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Call after touching the inventory so the world saves it. */
    public void markChanged() {
        onChange.run();
    }

    private void stateChanged() {
        stateVersion++;
        markChanged();
    }

    public int stateVersion() {
        return stateVersion;
    }

    public UUID id() {
        return id;
    }

    public QuantumInventory inventory() {
        return inventory;
    }

    // ---- owner and access --------------------------------------------------

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
        stateChanged();
    }

    public AccessMode accessMode() {
        return accessMode;
    }

    public void setAccessMode(AccessMode accessMode) {
        if (this.accessMode != accessMode) {
            this.accessMode = accessMode;
            stateChanged();
        }
    }

    public boolean isPublic() {
        return accessMode == AccessMode.PUBLIC;
    }

    public ChestTier tier() {
        return tier;
    }

    public void setTier(ChestTier tier) {
        if (this.tier != tier) {
            this.tier = tier;
            stateChanged();
        }
    }

    // ---- members -----------------------------------------------------------

    public Set<UUID> members() {
        return Set.copyOf(members.keySet());
    }

    public List<Member> memberList() {
        return members.entrySet().stream().map(e -> new Member(e.getKey(), e.getValue())).toList();
    }

    public List<String> memberNames() {
        return List.copyOf(members.values());
    }

    /** False if the list is full or the player was already on it. */
    public boolean addMember(Player player) {
        if (members.size() >= MAX_MEMBERS || members.containsKey(player.getUUID())) return false;
        members.put(player.getUUID(), player.getName().getString());
        stateChanged();
        return true;
    }

    public boolean removeMember(String name) {
        boolean removed = members.values().removeIf(n -> n.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)));
        if (removed) stateChanged();
        return removed;
    }

    // ---- custom tabs -------------------------------------------------------

    public List<CustomTab> tabs() {
        return List.copyOf(tabs);
    }

    public void setTabs(List<CustomTab> newTabs) {
        tabs.clear();
        for (CustomTab tab : newTabs) {
            if (tabs.size() >= CustomTab.MAX_TABS) break;
            tabs.add(tab.sanitized());
        }
        stateChanged();
    }

    // ---- permissions -------------------------------------------------------

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** Open the chest, put things in and take them out. */
    public boolean canUse(Player player) {
        return canUse(owner, isPublic(), members.keySet(), player.getUUID()) || isOperator(player);
    }

    /**
     * Break the chest and carry it off. Same people who can use it: the
     * contents always travel inside the dropped chest, nothing spills.
     */
    public boolean canBreak(Player player) {
        return canUse(player);
    }

    /**
     * Create and edit custom tabs. The owner and the invited players; in a
     * public chest, strangers can use it but not rearrange it.
     */
    public boolean canEditTabs(Player player) {
        return canManage(player) || members.containsKey(player.getUUID());
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

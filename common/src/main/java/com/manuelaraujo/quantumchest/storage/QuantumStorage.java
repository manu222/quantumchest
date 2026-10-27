package com.manuelaraujo.quantumchest.storage;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One storage in the world: the items, who owns them, who else may use them
 * and the custom tabs. Several chests can point at the same storage (that is
 * how linking will work), and the storage survives when its chest is broken.
 */
public final class QuantumStorage {
    public static final int MAX_MEMBERS = 12;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public static final Codec<QuantumStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(QuantumStorage::id),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(s -> Optional.ofNullable(s.owner)),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(QuantumStorage::ownerName),
            AccessMode.CODEC.optionalFieldOf("access", AccessMode.PRIVATE).forGetter(QuantumStorage::accessMode),
            Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(QuantumStorage::memberList),
            ChestTier.CODEC.optionalFieldOf("tier", ChestTier.BASIC).forGetter(QuantumStorage::tier),
            CustomTab.CODEC.listOf().optionalFieldOf("tabs", List.of()).forGetter(QuantumStorage::tabs),
            ItemStackWithSlot.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(QuantumStorage::upgradeList),
            QuantumInventory.Entry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(s -> s.inventory.entryList())
    ).apply(i, QuantumStorage::new));

    private final UUID id;
    private final QuantumInventory inventory = new QuantumInventory();
    private final List<Member> members = new ArrayList<>();
    private final List<CustomTab> tabs = new ArrayList<>();
    // Upgrade slots. All seven always exist; the tier decides how many can be used.
    private final SimpleContainer upgrades = new SimpleContainer(ChestTier.QUANTUM.upgradeSlots()) {
        @Override
        public void setChanged() {
            super.setChanged();
            markChanged();
        }
    };
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
        this(id, Optional.empty(), "", AccessMode.PRIVATE, List.of(), ChestTier.BASIC, List.of(), List.of(), List.of());
    }

    private QuantumStorage(UUID id, Optional<UUID> owner, String ownerName, AccessMode accessMode,
                           List<Member> members, ChestTier tier, List<CustomTab> tabs,
                           List<ItemStackWithSlot> upgrades, List<QuantumInventory.Entry> items) {
        this.id = id;
        this.owner = owner.orElse(null);
        this.ownerName = ownerName;
        this.accessMode = accessMode;
        this.members.addAll(members);
        this.tier = tier;
        this.tabs.addAll(tabs);
        for (ItemStackWithSlot upgrade : upgrades) {
            if (upgrade.slot() >= 0 && upgrade.slot() < this.upgrades.getContainerSize()) {
                this.upgrades.setItem(upgrade.slot(), upgrade.stack());
            }
        }
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

    /** UUIDs of the members that have already used the chest at least once. */
    public Set<UUID> members() {
        Set<UUID> ids = new HashSet<>();
        for (Member member : members) {
            if (member.id() != null) ids.add(member.id());
        }
        return ids;
    }

    public List<Member> memberList() {
        return List.copyOf(members);
    }

    public List<String> memberNames() {
        return members.stream().map(Member::name).toList();
    }

    public enum AddResult { ADDED, INVALID_NAME, IS_OWNER, ALREADY_MEMBER, FULL }

    /**
     * Invites a player by name. If they are online right now they are stored
     * with their UUID straight away; otherwise just the name, and the UUID is
     * filled in the first time they use the chest.
     */
    public AddResult addMember(String name, ServerPlayer online) {
        String clean = name.strip();
        if (!VALID_NAME.matcher(clean).matches()) return AddResult.INVALID_NAME;
        if (ownerName.equalsIgnoreCase(clean) || online != null && isOwner(online)) return AddResult.IS_OWNER;
        for (Member member : members) {
            if (member.hasName(clean) || online != null && online.getUUID().equals(member.id())) return AddResult.ALREADY_MEMBER;
        }
        if (members.size() >= MAX_MEMBERS) return AddResult.FULL;
        members.add(online != null ? new Member(online.getUUID(), online.getName().getString()) : new Member(null, clean));
        stateChanged();
        return AddResult.ADDED;
    }

    public boolean removeMember(String name) {
        boolean removed = members.removeIf(member -> member.hasName(name.strip()));
        if (removed) stateChanged();
        return removed;
    }

    /**
     * True if the player is on the list. A name-only entry that matches gets
     * the player's UUID from now on (and the name updated to its exact case).
     */
    private boolean isMember(Player player) {
        String name = player.getName().getString();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            if (player.getUUID().equals(member.id())) return true;
            if (member.id() == null && member.hasName(name)) {
                members.set(i, new Member(player.getUUID(), name));
                stateChanged();
                return true;
            }
        }
        return false;
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

    // ---- upgrades ----------------------------------------------------------

    /** The seven upgrade slots, shared by every chest that shows this storage. */
    public SimpleContainer upgrades() {
        return upgrades;
    }

    private List<ItemStackWithSlot> upgradeList() {
        List<ItemStackWithSlot> list = new ArrayList<>();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) list.add(new ItemStackWithSlot(i, upgrades.getItem(i)));
        }
        return list;
    }

    // ---- permissions -------------------------------------------------------

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** Open the chest, put things in and take them out. */
    public boolean canUse(Player player) {
        return owner == null || isOwner(player) || isPublic() || isMember(player) || isOperator(player);
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
        return canManage(player) || isMember(player);
    }

    /** Change permissions (and later: upgrade, link). Owner and operators only. */
    public boolean canManage(Player player) {
        return owner == null || isOwner(player) || isOperator(player);
    }

    /**
     * Same check for the client, which only has a synced copy of these fields.
     * {@code memberNames} are lower case.
     */
    public static boolean canUse(UUID owner, boolean isPublic, Set<UUID> members, Set<String> memberNames,
                                 UUID player, String playerName) {
        return owner == null || owner.equals(player) || isPublic || members.contains(player)
                || memberNames.contains(playerName.toLowerCase(Locale.ROOT));
    }

    private static boolean isOperator(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && Commands.LEVEL_GAMEMASTERS.check(serverPlayer.permissions());
    }
}

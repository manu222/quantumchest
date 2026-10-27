package com.manuelaraujo.quantumchest.storage;

import com.manuelaraujo.quantumchest.block.ChestTier;
import com.manuelaraujo.quantumchest.item.QuantumTunnelItem;
import com.manuelaraujo.quantumchest.registry.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One storage in the world: the items, who owns them, who else may use them,
 * the custom tabs and the upgrades. Entangled chests all point at the same
 * storage, and the storage survives when its chests are broken.
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
            QuantumInventory.Entry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(s -> s.inventory.entryList()),
            GlobalPos.CODEC.listOf().optionalFieldOf("chests", List.of()).forGetter(QuantumStorage::anchorList)
    ).apply(i, QuantumStorage::new));

    private final UUID id;
    private final QuantumInventory inventory = new QuantumInventory();
    private final List<Member> members = new ArrayList<>();
    private final List<CustomTab> tabs = new ArrayList<>();
    // Where the placed chests of this storage are. More than one means they are entangled.
    private final Set<GlobalPos> anchors = new LinkedHashSet<>();
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
    // Tier of the whole network. A kit used on any of its chests raises it, and every chest follows.
    private ChestTier tier;
    // Bumped when owner, access, members, tier or tabs change, so open screens resync them
    private int stateVersion;
    // Game time of the last compacting pass. Not saved: a network compacts at most once a second.
    private long compactedAt = Long.MIN_VALUE;
    private Runnable onChange = () -> {
    };

    QuantumStorage(UUID id) {
        this(id, Optional.empty(), "", AccessMode.PRIVATE, List.of(), ChestTier.BASIC, List.of(), List.of(), List.of(), List.of());
    }

    private QuantumStorage(UUID id, Optional<UUID> owner, String ownerName, AccessMode accessMode,
                           List<Member> members, ChestTier tier, List<CustomTab> tabs,
                           List<ItemStackWithSlot> upgrades, List<QuantumInventory.Entry> items,
                           List<GlobalPos> anchors) {
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
        this.anchors.addAll(anchors);
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

    /** True if an upgrade slot the current tier unlocks holds something matching. */
    public boolean hasUpgrade(Class<?> type) {
        return countUpgrades(type) > 0;
    }

    private int countUpgrades(Class<?> type) {
        int found = 0;
        for (int i = 0; i < tier.upgradeSlots(); i++) {
            if (type.isInstance(upgrades.getItem(i).getItem())) found++;
        }
        return found;
    }

    /** True if an unlocked slot holds this exact upgrade item. */
    public boolean hasUpgrade(Item item) {
        for (int i = 0; i < tier.upgradeSlots(); i++) {
            if (upgrades.getItem(i).is(item)) return true;
        }
        return false;
    }

    public boolean hasTunnel() {
        return hasUpgrade(QuantumTunnelItem.class);
    }

    /**
     * Whether an upgrade may come out of its slot. The last tunnel stays while
     * the network has chests in more than one dimension, because without it
     * those chests would stop working.
     */
    public boolean canRemoveUpgrade(ItemStack stack) {
        if (!(stack.getItem() instanceof QuantumTunnelItem)) return true;
        return !spansDimensions() || countUpgrades(QuantumTunnelItem.class) > 1;
    }

    private List<ItemStack> installedUpgrades() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) list.add(upgrades.getItem(i));
        }
        return list;
    }

    /** True if any upgrade slot already holds this kind of upgrade. One of each is enough. */
    private boolean hasInstalled(ItemStack upgrade) {
        return installedUpgrades().stream().anyMatch(s -> s.is(upgrade.getItem()));
    }

    /** Upgrades of {@code other} that this storage doesn't have yet, one of each kind. */
    private List<ItemStack> newUpgradesFrom(QuantumStorage other) {
        List<ItemStack> fresh = new ArrayList<>();
        for (ItemStack upgrade : other.installedUpgrades()) {
            boolean known = hasInstalled(upgrade) || fresh.stream().anyMatch(s -> s.is(upgrade.getItem()));
            if (!known) fresh.add(upgrade);
        }
        return fresh;
    }

    private int freeUpgradeSlots() {
        int free = 0;
        for (int i = 0; i < tier.upgradeSlots(); i++) {
            if (upgrades.getItem(i).isEmpty()) free++;
        }
        return free;
    }

    // ---- automatic input ---------------------------------------------------

    /** What an automatic insert did: how many were stored and how many destroyed. */
    public record Accepted(long stored, long destroyed) {
        public static final Accepted NONE = new Accepted(0, 0);

        public long total() {
            return stored + destroyed;
        }
    }

    /** False if there are whitelist tabs and the item is in none of them. */
    public boolean admits(ItemStack stack) {
        boolean anyWhitelist = false;
        for (CustomTab tab : tabs) {
            if (tab.role() != TabRole.WHITELIST) continue;
            anyWhitelist = true;
            if (tab.matches(stack)) return true;
        }
        return !anyWhitelist;
    }

    /** How many of this item to keep before destroying the rest, or -1 if it's never destroyed. */
    private long destroyLimit(ItemStack stack) {
        if (!hasUpgrade(ModItems.VOID_UPGRADE.get())) return -1;
        long limit = -1;
        for (CustomTab tab : tabs) {
            if (tab.role() == TabRole.VOID && tab.matches(stack)) {
                limit = limit < 0 ? tab.limit() : Math.min(limit, tab.limit());
            }
        }
        return limit;
    }

    /**
     * The one way in for everything automatic: magnet, hoppers, pipes and the
     * I/O upgrade. Applies the whitelist, then the destroy tabs, then the
     * capacity. Items a player puts in by hand don't come through here.
     */
    public Accepted acceptAutomatic(ItemStack template, long amount, boolean simulate) {
        if (template.isEmpty() || amount <= 0 || !admits(template)) return Accepted.NONE;
        long toStore = amount;
        long destroyed = 0;
        long limit = destroyLimit(template);
        if (limit >= 0) {
            long room = Math.max(0, limit - inventory.count(ItemKey.of(template)));
            toStore = Math.min(amount, room);
            destroyed = amount - toStore;
        }
        long stored;
        if (simulate) {
            stored = Math.max(0, Math.min(toStore, tier.capacity() - inventory.totalItems()));
        } else {
            stored = inventory.insert(template, toStore, tier.capacity());
            if (stored > 0 || destroyed > 0) markChanged();
        }
        return new Accepted(stored, destroyed);
    }

    /** True at most once a second, for the compactor. Shared by every chest of the network. */
    public boolean compactDue(long gameTime) {
        if (gameTime - compactedAt < 20) return false;
        compactedAt = gameTime;
        return true;
    }

    /** The custom tab with that name, or null. Faces of the I/O upgrade refer to tabs by name. */
    public CustomTab tabNamed(String name) {
        for (CustomTab tab : tabs) {
            if (tab.name().equals(name)) return tab;
        }
        return null;
    }

    // ---- placed chests -----------------------------------------------------

    public Set<GlobalPos> anchors() {
        return Set.copyOf(anchors);
    }

    private List<GlobalPos> anchorList() {
        return List.copyOf(anchors);
    }

    public void addAnchor(GlobalPos pos) {
        if (anchors.add(pos)) markChanged();
    }

    public void removeAnchor(GlobalPos pos) {
        if (anchors.remove(pos)) markChanged();
    }

    public boolean spansDimensions() {
        return anchors.stream().map(GlobalPos::dimension).distinct().count() > 1;
    }

    /**
     * The dimension the network started in: that of its oldest chest. Without a
     * tunnel, chests placed in any other dimension don't open.
     */
    public ResourceKey<Level> homeDimension() {
        return anchors.isEmpty() ? null : anchors.iterator().next().dimension();
    }

    /** False for a chest that sits outside the home dimension of a network without a tunnel. */
    public boolean worksIn(ResourceKey<Level> dimension) {
        ResourceKey<Level> home = homeDimension();
        return home == null || home.equals(dimension) || hasTunnel();
    }

    // ---- entangling --------------------------------------------------------

    public enum MergeCheck { OK, NO_ROOM, NO_UPGRADE_ROOM }

    /** Can {@code other} be poured into this storage? Nothing changes yet. */
    public MergeCheck checkAbsorb(QuantumStorage other) {
        long room = tier.capacity() - inventory.totalItems();
        if (other.inventory.totalItems() > room) return MergeCheck.NO_ROOM;
        if (newUpgradesFrom(other).size() > freeUpgradeSlots()) return MergeCheck.NO_UPGRADE_ROOM;
        return MergeCheck.OK;
    }

    /**
     * Moves everything from {@code other} into this storage: items, upgrades,
     * tabs and chest positions. Owner, access and members stay as they are here.
     * Call {@link #checkAbsorb} first; {@code other} ends up empty.
     *
     * @return upgrades this storage already had, which would only take up a
     *         slot twice. The caller hands them back to the player.
     */
    List<ItemStack> absorb(QuantumStorage other) {
        for (QuantumInventory.Entry entry : other.inventory.entryList()) {
            inventory.insert(entry.stack(), entry.count(), Long.MAX_VALUE);
        }
        other.inventory.clear();
        List<ItemStack> spare = new ArrayList<>();
        for (int from = 0; from < other.upgrades.getContainerSize(); from++) {
            ItemStack upgrade = other.upgrades.getItem(from).copy();
            if (upgrade.isEmpty()) continue;
            other.upgrades.setItem(from, ItemStack.EMPTY);
            int to = hasInstalled(upgrade) ? -1 : firstFreeUpgradeSlot();
            if (to < 0) {
                spare.add(upgrade);
            } else {
                upgrades.setItem(to, upgrade);
            }
        }
        for (CustomTab tab : other.tabs) {
            boolean taken = tabs.stream().anyMatch(t -> t.name().equalsIgnoreCase(tab.name()));
            if (!taken && tabs.size() < CustomTab.MAX_TABS) tabs.add(tab);
        }
        anchors.addAll(other.anchors);
        other.anchors.clear();
        stateChanged();
        return spare;
    }

    private int firstFreeUpgradeSlot() {
        for (int i = 0; i < tier.upgradeSlots(); i++) {
            if (upgrades.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Owner, access, members, tabs and tier of {@code other}, for a chest split off a network. */
    void copySettingsFrom(QuantumStorage other) {
        owner = other.owner;
        ownerName = other.ownerName;
        accessMode = other.accessMode;
        tier = other.tier;
        members.clear();
        members.addAll(other.members);
        tabs.clear();
        tabs.addAll(other.tabs);
        stateChanged();
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

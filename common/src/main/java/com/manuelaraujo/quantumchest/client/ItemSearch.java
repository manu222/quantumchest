package com.manuelaraujo.quantumchest.client;

import com.manuelaraujo.quantumchest.storage.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Turns the search box text into a filter. Words are combined with AND:
 * <pre>
 *   iron        name contains "iron"
 *   @create     item comes from the mod "create"
 *   #logs       item has a tag containing "logs"
 *   $sharp      tooltip contains "sharp" (enchantments, lore...)
 * </pre>
 */
public final class ItemSearch {
    // Tooltips are slow to build, so keep them while the screen is open
    private final Map<ItemKey, String> tooltipCache = new HashMap<>();

    public Predicate<ItemKey> parse(String query) {
        List<Predicate<ItemKey>> parts = new ArrayList<>();
        for (String raw : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (raw.isEmpty()) continue;
            char prefix = raw.charAt(0);
            String text = raw.substring(1);
            if (prefix == '@') {
                parts.add(key -> SortMode.namespace(key).contains(text));
            } else if (prefix == '#') {
                parts.add(key -> key.template().tags().anyMatch(tag -> tag.location().toString().contains(text)));
            } else if (prefix == '$') {
                parts.add(key -> tooltip(key).contains(text));
            } else {
                parts.add(key -> SortMode.name(key).contains(raw));
            }
        }
        return key -> {
            for (Predicate<ItemKey> part : parts) {
                if (!part.test(key)) return false;
            }
            return true;
        };
    }

    private String tooltip(ItemKey key) {
        return tooltipCache.computeIfAbsent(key, k -> {
            Minecraft mc = Minecraft.getInstance();
            StringBuilder text = new StringBuilder();
            for (Component line : k.template().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL)) {
                text.append(line.getString().toLowerCase(Locale.ROOT)).append('\n');
            }
            return text.toString();
        });
    }
}

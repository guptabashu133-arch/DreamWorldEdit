package org.mydreamduels.dreamworldedit.zone;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.regex.Pattern;

/**
 * "You can't do that here" messages in chat. Paper with MiniMessage: the coloured text, and on
 * 1.21.9+ the real item icon in front of it (a sprite from the game's own textures - no resource
 * pack). Spigot: the same text in plain colours.
 */
final class ZoneChat {

    private static final boolean MINI = has("net.kyori.adventure.text.minimessage.MiniMessage") && hasMethod();
    private static final boolean SPRITES = MINI && has("net.kyori.adventure.text.object.ObjectContents") && atLeast(1, 21, 9);
    /** 1.21.11 moved item textures into their own atlas. */
    private static final boolean ITEMS_ATLAS = atLeast(1, 21, 11);
    private static final Pattern TAGS = Pattern.compile("<[^<>]*>");

    private ZoneChat() {
    }

    /** text = MiniMessage; icon = a texture like "item/diamond_sword" or "block/barrel_top" (null = none). */
    static void send(Player p, String text, String icon, boolean sprites) {
        if (text == null || text.isBlank()) return;
        if (MINI) {
            try {
                Paper.send(p, text, sprites && SPRITES ? icon : null, ITEMS_ATLAS);
                return;
            } catch (Throwable ignored) {
                // fall through to plain text
            }
        }
        p.sendMessage(Msg.color(legacy(text)));
    }

    /** A MiniMessage line as &-colours for Spigot: the common colours kept, every other tag dropped. */
    static String legacy(String mini) {
        String s = mini.replaceAll("(?i)<(red|#ff[0-9a-f]{4})>", "&c").replaceAll("(?i)<(gray|grey)>", "&7")
                .replaceAll("(?i)<(dark_gray|dark_grey)>", "&8").replaceAll("(?i)<white>", "&f")
                .replaceAll("(?i)<(gold|yellow|#ff[b-d][0-9a-f]{3})>", "&6").replaceAll("(?i)<(b|bold)>", "&l");
        return TAGS.matcher(s).replaceAll("");
    }

    private static boolean has(String c) {
        try {
            Class.forName(c);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Player#sendMessage(Component) - Paper (Spigot can't send Adventure components). */
    private static boolean hasMethod() {
        try {
            Player.class.getMethod("sendMessage", Class.forName("net.kyori.adventure.text.Component"));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean atLeast(int a, int b, int c) {
        try {
            String[] v = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
            int x = Integer.parseInt(v[0]), y = v.length > 1 ? Integer.parseInt(v[1]) : 0, z = v.length > 2 ? Integer.parseInt(v[2]) : 0;
            if (x != a) return x > a;
            if (y != b) return y > b;
            return z >= c;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Only touched on Paper - keeps the Adventure classes out of Spigot's way. */
    private static final class Paper {
        static void send(Player p, String text, String icon, boolean itemsAtlas) {
            var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            net.kyori.adventure.text.Component line = mm.deserialize(text);
            if (icon != null && !icon.isBlank()) {
                boolean block = icon.startsWith("block/");
                var atlas = net.kyori.adventure.key.Key.key("minecraft", block || !itemsAtlas ? "blocks" : "items");
                var sprite = net.kyori.adventure.key.Key.key("minecraft", icon);
                net.kyori.adventure.text.Component ic = net.kyori.adventure.text.Component.object(
                        b -> b.contents(net.kyori.adventure.text.object.ObjectContents.sprite(atlas, sprite)));
                line = ic.append(net.kyori.adventure.text.Component.text(" ")).append(line);
            }
            p.sendMessage(line);
        }
    }
}

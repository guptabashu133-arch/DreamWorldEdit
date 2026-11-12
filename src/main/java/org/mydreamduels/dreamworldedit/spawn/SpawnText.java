package org.mydreamduels.dreamworldedit.spawn;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.util.Msg;

/**
 * Titles for the /spawn countdown with an item picture in front: a real sprite for Java 1.21.9+
 * and the glyph from the TaraashMC-Icons pack for Bedrock (Geyser) players. Plain Spigot gets
 * the same text without the picture.
 */
final class SpawnText {

    private static final boolean PAPER = has("net.kyori.adventure.title.Title") && hasShowTitle();
    private static final boolean SPRITES = PAPER && has("net.kyori.adventure.text.object.ObjectContents") && atLeast(1, 21, 9);
    private static final boolean ITEMS_ATLAS = atLeast(1, 21, 11);

    private SpawnText() {
    }

    /** Floodgate gives Bedrock players ids starting with 00000000-0000-0000. */
    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0;
    }

    /**
     * @param icon   texture for Java ("item/clock_00"), null = none
     * @param glyph  TaraashMC-Icons pack codepoint for Bedrock (0 = none)
     */
    @SuppressWarnings("deprecation")
    static void title(Player p, String title, String subtitle, String icon, int glyph, int fadeIn, int stay, int fadeOut) {
        if (PAPER) {
            Paper.title(p, title, subtitle, bedrock(p) ? null : (SPRITES ? icon : null), bedrock(p) ? glyph : 0, fadeIn, stay, fadeOut);
            return;
        }
        p.sendTitle(Msg.color(title.replace("{icon}", "")), Msg.color(subtitle.replace("{icon}", "")), fadeIn, stay, fadeOut);
    }

    /** One line in the action bar (live countdown). */
    @SuppressWarnings("deprecation")
    static void actionBar(Player p, String text, String icon, int glyph) {
        if (PAPER) {
            p.sendActionBar(Paper.text(text, bedrock(p) ? null : (SPRITES ? icon : null), bedrock(p) ? glyph : 0));
            return;
        }
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Msg.color(text.replace("{icon}", ""))));
    }

    /** One line in chat. */
    static void chat(Player p, String text, String icon, int glyph) {
        if (PAPER) {
            p.sendMessage(Paper.text(text, bedrock(p) ? null : (SPRITES ? icon : null), bedrock(p) ? glyph : 0));
            return;
        }
        p.sendMessage(Msg.color(text.replace("{icon}", "")));
    }

    private static boolean has(String c) {
        try {
            Class.forName(c);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasShowTitle() {
        try {
            Player.class.getMethod("showTitle", Class.forName("net.kyori.adventure.title.Title"));
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

    /** Only touched on Paper. */
    private static final class Paper {
        static net.kyori.adventure.text.Component text(String legacy, String icon, int glyph) {
            var ser = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
            net.kyori.adventure.text.Component pic;
            if (icon != null) {
                boolean block = icon.startsWith("block/");
                var atlas = net.kyori.adventure.key.Key.key("minecraft", block || !ITEMS_ATLAS ? "blocks" : "items");
                pic = net.kyori.adventure.text.Component.object(
                        b -> b.contents(net.kyori.adventure.text.object.ObjectContents.sprite(atlas, net.kyori.adventure.key.Key.key("minecraft", icon))));
            } else if (glyph > 0) {
                pic = net.kyori.adventure.text.Component.text(new String(Character.toChars(glyph)),
                        net.kyori.adventure.text.format.NamedTextColor.WHITE);
            } else {
                pic = net.kyori.adventure.text.Component.empty();
            }
            net.kyori.adventure.text.Component out = ser.deserialize(legacy.replace("{icon}", "\u0000"));
            net.kyori.adventure.text.Component finalPic = pic;
            return out.replaceText(r -> r.matchLiteral("\u0000").replacement(finalPic));
        }

        static void title(Player p, String title, String subtitle, String icon, int glyph, int fadeIn, int stay, int fadeOut) {
            var times = net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(fadeIn * 50L),
                    java.time.Duration.ofMillis(stay * 50L), java.time.Duration.ofMillis(fadeOut * 50L));
            p.showTitle(net.kyori.adventure.title.Title.title(text(title, icon, glyph), text(subtitle, icon, glyph), times));
        }
    }
}

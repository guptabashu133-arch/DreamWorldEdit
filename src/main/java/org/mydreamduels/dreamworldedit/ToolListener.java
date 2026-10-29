package org.mydreamduels.dreamworldedit;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.mydreamduels.dreamworldedit.brush.Brush;
import org.mydreamduels.dreamworldedit.command.SelectionCommands;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Msg;

/** Wand clicks (selection) and brush use. Runs on the player's thread. */
public final class ToolListener implements Listener {

    private final DreamWorldEdit plugin;

    public ToolListener(DreamWorldEdit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getItem() == null) return;
        Player p = event.getPlayer();
        Material item = event.getItem().getType();
        Action action = event.getAction();

        Brush brush = plugin.sessions().get(p).brushes().get(item);
        if (brush != null && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (!p.hasPermission("dreamworldedit.brush.use") && !p.hasPermission("dreamworldedit.*")) {
                Msg.err(p, "You don't have permission to use brushes.");
                return;
            }
            Block target;
            try {
                target = p.getTargetBlockExact(brush.range, FluidCollisionMode.NEVER);
            } catch (RuntimeException e) {
                target = null; // Folia: the ray left the region this thread owns
            }
            if (target == null) {
                Msg.err(p, "No block in range.");
                return;
            }
            brush.use(plugin, p, BlockVec.of(target.getLocation()));
            return;
        }

        if (item != SelectionCommands.wand(plugin) || event.getClickedBlock() == null) return;
        if (!p.hasPermission("dreamworldedit.command.wand") && !p.hasPermission("dreamworldedit.*")) return;
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            SelectionCommands.setPos(plugin, p, 1, BlockVec.of(event.getClickedBlock().getLocation()));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            SelectionCommands.setPos(plugin, p, 2, BlockVec.of(event.getClickedBlock().getLocation()));
        }
    }
}

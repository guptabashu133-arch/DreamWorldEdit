package org.mydreamduels.dreamworldedit.zone;

// PAPER-ONLY: registered only when Paper's knockback event exists.

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** The "push" rule: hits, wind charges, maces and shoves don't move players inside the zone. */
public final class ZoneKnockback implements Listener {

    private final ZoneManager zones;

    public ZoneKnockback(ZoneManager zones) {
        this.zones = zones;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        zones.knockback(victim, e instanceof EntityPushedByEntityAttackEvent by ? by.getPushedBy() : null, e);
    }
}

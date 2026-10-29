package org.mydreamduels.dreamworldedit.session;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session get(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
    }

    public void clear() {
        sessions.clear();
    }
}

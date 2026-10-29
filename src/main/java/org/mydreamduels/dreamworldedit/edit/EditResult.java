package org.mydreamduels.dreamworldedit.edit;

/** changed = blocks actually changed; record may be null for read-only/undo runs; error = first failure, if any. */
public record EditResult(long changed, EditRecord record, long millis, String error) {
}

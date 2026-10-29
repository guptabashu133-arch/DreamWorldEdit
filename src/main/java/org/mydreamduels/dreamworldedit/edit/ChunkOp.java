package org.mydreamduels.dreamworldedit.edit;

/** The part of an edit that runs inside one chunk job. */
@FunctionalInterface
public interface ChunkOp {
    void apply(ChunkContext ctx);
}

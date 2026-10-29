package org.mydreamduels.dreamworldedit.edit;

@FunctionalInterface
public interface PosVisitor {
    void visit(ChunkContext ctx, int x, int y, int z);
}

package com.mucifex.pathfinding.internal.pathfind.main.path.impl;

import net.minecraft.util.Vec3;
import com.mucifex.pathfinding.internal.util.Util;
import com.mucifex.pathfinding.internal.pathfind.main.path.Node;
import com.mucifex.pathfinding.internal.pathfind.main.path.PathElm;

public class JumpNode extends Node implements PathElm {

    public JumpNode(int x, int y, int z) {
        super(x, y, z);
    }

    @Override
    public boolean playerOn(Vec3 playerPos) {
        return Util.toBlockPos(playerPos).equals(getBlockPos());
    }
}

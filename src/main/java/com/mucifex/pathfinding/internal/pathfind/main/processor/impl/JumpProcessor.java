package com.mucifex.pathfinding.internal.pathfind.main.processor.impl;

import com.mucifex.pathfinding.internal.pathfind.main.path.PathElm;
import com.mucifex.pathfinding.internal.pathfind.main.processor.Processor;

import java.util.List;

//TODO add more optimisations. Connect nodes if the jump is a slab or stair, which means we can just walk up it instead of jumping.

public class JumpProcessor extends Processor {

    @Override
    public void process(List<PathElm> elms) {
    }
}

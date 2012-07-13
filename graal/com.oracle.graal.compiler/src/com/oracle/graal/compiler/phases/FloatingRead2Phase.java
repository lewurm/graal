/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.compiler.phases;

import java.util.*;

import com.oracle.graal.compiler.graph.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.extended.*;

public class FloatingRead2Phase extends Phase {

    private IdentityHashMap<LoopBeginNode, List<MemoryMap>> loopEndStatesMap;

    private static class LoopState {
        public LoopBeginNode loopBegin;
        public MemoryMap state;
        public IdentityHashMap<PhiNode, Object> loopPhiLocations = new IdentityHashMap<>();
        public ValueNode loopEntryAnyLocation;
        public LoopState(LoopBeginNode loopBegin, MemoryMap state, ValueNode loopEntryAnyLocation) {
            this.loopBegin = loopBegin;
            this.state = state;
            this.loopEntryAnyLocation = loopEntryAnyLocation;
        }
    }

    private class MemoryMap implements MergeableState<MemoryMap> {
        private IdentityHashMap<Object, ValueNode> lastMemorySnapshot;
        private LinkedList<LoopState> loops;

        public MemoryMap(MemoryMap memoryMap) {
            lastMemorySnapshot = new IdentityHashMap<>(memoryMap.lastMemorySnapshot);
            loops = new LinkedList<>(memoryMap.loops);
        }

        public MemoryMap() {
            lastMemorySnapshot = new IdentityHashMap<>();
            loops = new LinkedList<>();
        }

        @Override
        public boolean merge(MergeNode merge, List<MemoryMap> withStates) {
            if (withStates.size() == 0) {
                return true;
            }
            Debug.log("Merge with %s", lastMemorySnapshot);
            IdentityHashMap<Object, Object> visitedKeys = new IdentityHashMap<>();
            for (MemoryMap other : withStates) {
                Debug.log("  other: %s", other.lastMemorySnapshot);
                for (Map.Entry<Object, ValueNode> entry : lastMemorySnapshot.entrySet()) {
                    Object key = entry.getKey();
                    visitedKeys.put(key, key);
                    ValueNode localNode = entry.getValue();
                    ValueNode otherNode = other.lastMemorySnapshot.get(key);
                    if (otherNode == null) {
                        otherNode = other.lastMemorySnapshot.get(LocationNode.ANY_LOCATION);
                    }
                    assert localNode != null;
                    if (localNode != otherNode && !merge.isPhiAtMerge(localNode)) {
                        PhiNode phi = merge.graph().add(new PhiNode(PhiType.Memory, merge));
                        phi.addInput(localNode);
                        lastMemorySnapshot.put(key, phi);
                    }
                }
            }
            for (Map.Entry<Object, ValueNode> entry : lastMemorySnapshot.entrySet()) {
                ValueNode localNode = entry.getValue();
                PhiNode phi = null;
                if (merge.isPhiAtMerge(localNode)) {
                    phi = (PhiNode) localNode;
                } else if (!visitedKeys.containsKey(entry.getKey())) {
                    phi = merge.graph().add(new PhiNode(PhiType.Memory, merge));
                    phi.addInput(localNode);
                    lastMemorySnapshot.put(entry.getKey(), phi);
                }
                if (phi != null) {
                    Debug.log("Phi @ %s for %s", merge, entry.getKey());
                    for (MemoryMap other : withStates) {
                        ValueNode otherNode = other.lastMemorySnapshot.get(entry.getKey());
                        if (otherNode == null) {
                            otherNode = other.lastMemorySnapshot.get(LocationNode.ANY_LOCATION);
                        }
                        assert otherNode != null;
                        phi.addInput(otherNode);
                    }
                }
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            LoopState loopState = new LoopState(loopBegin, this, lastMemorySnapshot.get(LocationNode.ANY_LOCATION));
            for (Map.Entry<Object, ValueNode> entry : lastMemorySnapshot.entrySet()) {
                PhiNode phi = loopBegin.graph().add(new PhiNode(PhiType.Memory, loopBegin));
                phi.addInput(entry.getValue());
                entry.setValue(phi);
                loopState.loopPhiLocations.put(phi, entry.getKey());
            }
            loops.push(loopState);
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<MemoryMap> loopEndStates) {
            loopEndStatesMap.put(loopBegin, loopEndStates);
            tryFinishLoopPhis(this, loopBegin);
        }

        @Override
        public void afterSplit(FixedNode node) {
            // nothing
        }

        @Override
        public MemoryMap clone() {
            return new MemoryMap(this);
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        loopEndStatesMap = new IdentityHashMap<>();
        new PostOrderNodeIterator<MemoryMap>(graph.start(), new MemoryMap()) {
            @Override
            protected void node(FixedNode node) {
                processNode(node, state);
            }
        }.apply();
    }

    private void processNode(FixedNode node, MemoryMap state) {
        if (node instanceof ReadNode) {
            processRead((ReadNode) node, state);
        } else if (node instanceof WriteNode) {
            processWrite((WriteNode) node, state);
        } else if (node instanceof MemoryCheckpoint) {
            processCheckpoint((MemoryCheckpoint) node, state);
        } else if (node instanceof LoopExitNode) {
            processLoopExit((LoopExitNode) node, state);
        }
    }

    private static void processCheckpoint(MemoryCheckpoint checkpoint, MemoryMap state) {
        for (Map.Entry<Object, ValueNode> entry : state.lastMemorySnapshot.entrySet()) {
            entry.setValue((ValueNode) checkpoint);
        }
        state.lastMemorySnapshot.put(LocationNode.ANY_LOCATION, (ValueNode) checkpoint);
    }

    private static void processWrite(WriteNode writeNode, MemoryMap state) {
        if (writeNode.location().locationIdentity() == LocationNode.ANY_LOCATION) {
            state.lastMemorySnapshot.clear();
        }
        state.lastMemorySnapshot.put(writeNode.location().locationIdentity(), writeNode);
    }

    private void processRead(ReadNode readNode, MemoryMap state) {
        StructuredGraph graph = (StructuredGraph) readNode.graph();
        assert readNode.getNullCheck() == false;
        Object locationIdentity = readNode.location().locationIdentity();
        ValueNode lastLocationAccess = getLastLocationAccessForRead(state, locationIdentity);
        FloatingReadNode floatingRead = graph.unique(new FloatingReadNode(readNode.object(), readNode.location(), lastLocationAccess, readNode.stamp(), readNode.dependencies()));
        floatingRead.setNullCheck(readNode.getNullCheck());
        ValueAnchorNode anchor = null;
        for (GuardNode guard : readNode.dependencies().filter(GuardNode.class)) {
            if (anchor == null) {
                anchor = graph.add(new ValueAnchorNode());
            }
            anchor.addAnchoredNode(guard);
        }
        if (anchor != null) {
            graph.addAfterFixed(readNode, anchor);
        }
        graph.replaceFixedWithFloating(readNode, floatingRead);
    }

    private ValueNode getLastLocationAccessForRead(MemoryMap state, Object locationIdentity) {
        ValueNode lastLocationAccess;
        if (locationIdentity == LocationNode.FINAL_LOCATION) {
            lastLocationAccess = null;
        } else {
            lastLocationAccess = state.lastMemorySnapshot.get(locationIdentity);
            if (lastLocationAccess == null) {
                LoopState lastLoop = state.loops.peek();
                if (lastLoop == null) {
                    lastLocationAccess = state.lastMemorySnapshot.get(LocationNode.ANY_LOCATION);
                    Debug.log("getLastLocationAccessForRead(%s, %s) -> unknown, not in a loop : %s", state, locationIdentity, lastLocationAccess);
                } else {
                    ValueNode phiInit;
                    if (state.loops.size() > 1) {
                        Debug.log("getLastLocationAccessForRead(%s, %s) -> unknown, in a non-outtermost loop, getting state in 2nd loop", state, locationIdentity);
                        phiInit = getLastLocationAccessForRead(state.loops.get(1).state, locationIdentity);
                    } else {
                        phiInit = lastLoop.loopEntryAnyLocation;
                        Debug.log("getLastLocationAccessForRead(%s, %s) -> unknown, in a outtermost loop : Phi(%s,...)", state, locationIdentity, phiInit);
                    }
                    PhiNode phi = lastLoop.loopBegin.graph().add(new PhiNode(PhiType.Memory, lastLoop.loopBegin));
                    phi.addInput(phiInit);
                    lastLoop.state.lastMemorySnapshot.put(locationIdentity, phi);
                    lastLoop.loopPhiLocations.put(phi, locationIdentity);
                    tryFinishLoopPhis(lastLoop.state, lastLoop.loopBegin);
                    lastLocationAccess = phi;
                }
                state.lastMemorySnapshot.put(locationIdentity, lastLocationAccess);
            } else {
                Debug.log("getLastLocationAccessForRead(%s, %s) -> directly : %s", state, locationIdentity, lastLocationAccess);
            }
        }
        return lastLocationAccess;
    }

    private static void processLoopExit(LoopExitNode exit, MemoryMap state) {
        for (Map.Entry<Object, ValueNode> entry : state.lastMemorySnapshot.entrySet()) {
            entry.setValue(exit.graph().unique(new ValueProxyNode(entry.getValue(), exit, PhiType.Memory)));
        }
        LoopState poped = state.loops.pop();
        assert poped.loopBegin == exit.loopBegin();
    }

    private void tryFinishLoopPhis(MemoryMap loopMemory, LoopBeginNode loopBegin) {
        List<MemoryMap> loopEndStates = loopEndStatesMap.get(loopBegin);
        if (loopEndStates == null) {
            return;
        }
        LoopState loopState = loopMemory.loops.get(0);
        int i = 0;
        while (loopState.loopBegin != loopBegin) {
            loopState = loopMemory.loops.get(++i);
        }
        for (PhiNode phi : loopBegin.phis()) {
            if (phi.type() == PhiType.Memory && phi.valueCount() == 1) {
                Object location = loopState.loopPhiLocations.get(phi);
                assert location != null : "unknown location for " + phi;
                for (MemoryMap endState : loopEndStates) {
                    ValueNode otherNode = endState.lastMemorySnapshot.get(location);
                    if (otherNode == null) {
                        otherNode = endState.lastMemorySnapshot.get(LocationNode.ANY_LOCATION);
                    }
                    phi.addInput(otherNode);
                }
            }
        }
    }
}

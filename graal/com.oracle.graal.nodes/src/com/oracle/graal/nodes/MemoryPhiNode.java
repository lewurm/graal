/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.extended.*;

/**
 * Memory {@code PhiNode}s merge memory dependencies at control flow merges.
 */
@NodeInfo(nameTemplate = "MemoryPhi({i#values}) {p#locationIdentity/s}", allowedUsageTypes = {InputType.Memory})
public class MemoryPhiNode extends PhiNode implements MemoryNode {

    @Input(InputType.Memory) NodeInputList<ValueNode> values;
    protected final LocationIdentity locationIdentity;

    public static MemoryPhiNode create(MergeNode merge, LocationIdentity locationIdentity) {
        return new MemoryPhiNode(merge, locationIdentity);
    }

    protected MemoryPhiNode(MergeNode merge, LocationIdentity locationIdentity) {
        super(StampFactory.forVoid(), merge);
        this.locationIdentity = locationIdentity;
        this.values = new NodeInputList<>(this);
    }

    public static MemoryPhiNode create(MergeNode merge, LocationIdentity locationIdentity, ValueNode[] values) {
        return new MemoryPhiNode(merge, locationIdentity, values);
    }

    protected MemoryPhiNode(MergeNode merge, LocationIdentity locationIdentity, ValueNode[] values) {
        super(StampFactory.forVoid(), merge);
        this.locationIdentity = locationIdentity;
        this.values = new NodeInputList<>(this, values);
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public NodeInputList<ValueNode> values() {
        return values;
    }
}

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
package com.oracle.graal.nodes;

import static com.oracle.graal.nodeinfo.InputType.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;

@NodeInfo(allowedUsageTypes = {Condition})
public abstract class LogicNode extends FloatingNode {

    public LogicNode() {
        super(StampFactory.forVoid());
    }

    public static LogicNode and(LogicNode a, LogicNode b, double shortCircuitProbability) {
        return and(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode and(LogicNode a, boolean negateA, LogicNode b, boolean negateB, double shortCircuitProbability) {
        StructuredGraph graph = a.graph();
        ShortCircuitOrNode notAorNotB = graph.unique(ShortCircuitOrNode.create(a, !negateA, b, !negateB, shortCircuitProbability));
        return graph.unique(LogicNegationNode.create(notAorNotB));
    }

    public static LogicNode or(LogicNode a, LogicNode b, double shortCircuitProbability) {
        return or(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode or(LogicNode a, boolean negateA, LogicNode b, boolean negateB, double shortCircuitProbability) {
        return a.graph().unique(ShortCircuitOrNode.create(a, negateA, b, negateB, shortCircuitProbability));
    }
}

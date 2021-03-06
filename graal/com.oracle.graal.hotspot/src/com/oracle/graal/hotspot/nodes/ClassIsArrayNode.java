/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * {@link MacroNode Macro node} for {@link Class#isArray()}.
 *
 * @see ClassSubstitutions#isArray(Class)
 */
@NodeInfo
public class ClassIsArrayNode extends MacroNode implements Canonicalizable {

    public static ClassIsArrayNode create(Invoke invoke) {
        return new ClassIsArrayNode(invoke);
    }

    protected ClassIsArrayNode(Invoke invoke) {
        super(invoke);
    }

    private ValueNode getJavaClass() {
        return arguments.get(0);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode javaClass = getJavaClass();
        if (javaClass.isConstant()) {
            Class<?> c = (Class<?>) HotSpotObjectConstant.asObject(javaClass.asConstant());
            if (c != null) {
                return ConstantNode.forBoolean(c.isArray());
            }
        }
        return this;
    }
}

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

import com.oracle.graal.nodes.type.*;

public abstract class FixedNode extends ValueNode {

    private double probability;

    public FixedNode(Stamp stamp) {
        super(stamp);
    }

    public FixedNode(Stamp stamp, ValueNode... dependencies) {
        super(stamp, dependencies);
    }

    public double probability() {
        return probability;
    }

    public void setProbability(double probability) {
        assert probability >= 0 : String.format("Invalid argument %f, because the probability of a node must not be negative.", probability);
        this.probability = probability;
        assert !Double.isNaN(probability);
    }

    protected void copyInto(FixedNode newNode) {
        newNode.setProbability(probability);
    }

    @Override
    public boolean verify() {
        assertTrue(this.successors().isNotEmpty() || this.predecessor() != null, "FixedNode should not float");
        return super.verify();
    }
}
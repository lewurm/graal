/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.oracle.graal.graph.*;
import com.sun.c1x.debug.*;
import com.sun.cri.ci.*;

/**
 * The {@code ExceptionObject} instruction represents the incoming exception object to an exception handler.
 */
public final class ExceptionObject extends Instruction {

    private static final int INPUT_COUNT = 0;
    private static final int SUCCESSOR_COUNT = 0;

    /**
     * Constructs a new ExceptionObject instruction.
     * @param stateBefore TODO
     * @param graph
     */
    public ExceptionObject(Graph graph) {
        super(CiKind.Object, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setNonNull(true);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitExceptionObject(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("incoming exception");
    }
}

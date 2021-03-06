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
package com.oracle.graal.hsail;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.nio.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Register.RegisterCategory;
import com.oracle.graal.api.meta.*;

/**
 * Represents the HSAIL architecture.
 */
public class HSAIL extends Architecture {

    private static final int CPU_CONTROL_OFFSET = 0;
    private static final int CPU_CONTROL_SHIFT = 0;
    private static final int CPU_CONTROL_NUM = 8;

    private static final int CPU32_OFFSET = CPU_CONTROL_NUM << CPU_CONTROL_SHIFT;
    private static final int CPU32_SHIFT = 0;
    private static final int CPU32_NUM = 40;

    private static final int CPU64_OFFSET = CPU32_OFFSET + CPU32_NUM << CPU32_SHIFT;
    private static final int CPU64_SHIFT = 0;
    private static final int CPU64_NUM = 21;

    private static final int CPU128_OFFSET = CPU64_OFFSET + CPU64_NUM << CPU64_SHIFT;
    private static final int CPU128_SHIFT = 1;
    private static final int CPU128_NUM = 16;

    public static final RegisterCategory CPU_CONTROL = new RegisterCategory("CPU_CONTROL", CPU_CONTROL_OFFSET, CPU_CONTROL_SHIFT);
    public static final RegisterCategory CPU32 = new RegisterCategory("CPU32", CPU32_OFFSET, CPU32_SHIFT);
    public static final RegisterCategory CPU64 = new RegisterCategory("CPU64", CPU64_OFFSET, CPU64_SHIFT);
    public static final RegisterCategory CPU128 = new RegisterCategory("CPU128", CPU128_OFFSET, CPU128_SHIFT);

    // Control registers.
    public static final Register c0 = new Register(0, 0, "c0", CPU_CONTROL);
    public static final Register c1 = new Register(1, 1, "c1", CPU_CONTROL);
    public static final Register c2 = new Register(2, 2, "c2", CPU_CONTROL);
    public static final Register c3 = new Register(3, 3, "c3", CPU_CONTROL);
    public static final Register c4 = new Register(4, 4, "c4", CPU_CONTROL);
    public static final Register c5 = new Register(5, 5, "c5", CPU_CONTROL);
    public static final Register c6 = new Register(6, 6, "c6", CPU_CONTROL);
    public static final Register c7 = new Register(7, 7, "c7", CPU_CONTROL);

    // 32 bit registers.
    public static final Register s0 = new Register(8, 0, "s0", CPU32);
    public static final Register s1 = new Register(9, 1, "s1", CPU32);
    public static final Register s2 = new Register(10, 2, "s2", CPU32);
    public static final Register s3 = new Register(11, 3, "s3", CPU32);
    public static final Register s4 = new Register(12, 4, "s4", CPU32);
    public static final Register s5 = new Register(13, 5, "s5", CPU32);
    public static final Register s6 = new Register(14, 6, "s6", CPU32);
    public static final Register s7 = new Register(15, 7, "s7", CPU32);
    public static final Register s8 = new Register(16, 8, "s8", CPU32);
    public static final Register s9 = new Register(17, 9, "s9", CPU32);
    public static final Register s10 = new Register(18, 10, "s10", CPU32);
    public static final Register s11 = new Register(19, 11, "s11", CPU32);
    public static final Register s12 = new Register(20, 12, "s12", CPU32);
    public static final Register s13 = new Register(21, 13, "s13", CPU32);
    public static final Register s14 = new Register(22, 14, "s14", CPU32);
    public static final Register s15 = new Register(23, 15, "s15", CPU32);
    public static final Register s16 = new Register(24, 16, "s16", CPU32);
    public static final Register s17 = new Register(25, 17, "s17", CPU32);
    public static final Register s18 = new Register(26, 18, "s18", CPU32);
    public static final Register s19 = new Register(27, 19, "s19", CPU32);
    public static final Register s20 = new Register(28, 20, "s20", CPU32);
    public static final Register s21 = new Register(29, 21, "s21", CPU32);
    public static final Register s22 = new Register(30, 22, "s22", CPU32);
    public static final Register s23 = new Register(31, 23, "s23", CPU32);
    public static final Register s24 = new Register(32, 24, "s24", CPU32);
    public static final Register s25 = new Register(33, 25, "s25", CPU32);
    public static final Register s26 = new Register(34, 26, "s26", CPU32);
    public static final Register s27 = new Register(35, 27, "s27", CPU32);
    public static final Register s28 = new Register(36, 28, "s28", CPU32);
    public static final Register s29 = new Register(37, 29, "s29", CPU32);
    public static final Register s30 = new Register(38, 30, "s30", CPU32);
    public static final Register s31 = new Register(39, 31, "s31", CPU32);

    // 64 bit registers.
    public static final Register d0 = new Register(40, 0, "d0", CPU64);
    public static final Register d1 = new Register(41, 1, "d1", CPU64);
    public static final Register d2 = new Register(42, 2, "d2", CPU64);
    public static final Register d3 = new Register(43, 3, "d3", CPU64);
    public static final Register d4 = new Register(44, 4, "d4", CPU64);
    public static final Register d5 = new Register(45, 5, "d5", CPU64);
    public static final Register d6 = new Register(46, 6, "d6", CPU64);
    public static final Register d7 = new Register(47, 7, "d7", CPU64);
    public static final Register d8 = new Register(48, 8, "d8", CPU64);
    public static final Register d9 = new Register(49, 9, "d9", CPU64);
    public static final Register d10 = new Register(50, 10, "d10", CPU64);
    public static final Register d11 = new Register(51, 11, "d11", CPU64);
    public static final Register d12 = new Register(52, 12, "d12", CPU64);
    public static final Register d13 = new Register(53, 13, "d13", CPU64);
    public static final Register d14 = new Register(54, 14, "d14", CPU64);
    public static final Register d15 = new Register(55, 15, "d15", CPU64);

    // 128 bit registers.
    public static final Register q0 = new Register(56, 0, "q0", CPU128);
    public static final Register q1 = new Register(57, 1, "q1", CPU128);
    public static final Register q2 = new Register(58, 2, "q2", CPU128);
    public static final Register q3 = new Register(59, 3, "q3", CPU128);
    public static final Register q4 = new Register(60, 4, "q4", CPU128);
    public static final Register q5 = new Register(61, 5, "q5", CPU128);
    public static final Register q6 = new Register(62, 6, "q6", CPU128);
    public static final Register q7 = new Register(63, 7, "q7", CPU128);
    public static final Register q8 = new Register(64, 8, "q8", CPU128);
    public static final Register q9 = new Register(65, 9, "q9", CPU128);
    public static final Register q10 = new Register(66, 10, "q10", CPU128);
    public static final Register q11 = new Register(67, 11, "q11", CPU128);
    public static final Register q12 = new Register(68, 12, "q12", CPU128);
    public static final Register q13 = new Register(69, 13, "q13", CPU128);
    public static final Register q14 = new Register(70, 14, "q14", CPU128);
    public static final Register q15 = new Register(71, 15, "q15", CPU128);

    // non-allocatable registers used for deopt
    public static final Register s32 = new Register(72, 32, "s32", CPU32);
    public static final Register s33 = new Register(73, 33, "s33", CPU32);
    public static final Register s34 = new Register(74, 34, "s34", CPU32);
    public static final Register s35 = new Register(75, 35, "s35", CPU32);
    public static final Register s36 = new Register(76, 36, "s36", CPU32);
    public static final Register s37 = new Register(77, 37, "s37", CPU32);
    public static final Register s38 = new Register(78, 38, "s38", CPU32);
    public static final Register s39 = new Register(79, 39, "s39", CPU32);
    public static final Register d16 = new Register(80, 16, "d16", CPU64);
    public static final Register d17 = new Register(81, 17, "d17", CPU64);
    public static final Register d18 = new Register(82, 18, "d18", CPU64);
    public static final Register d19 = new Register(83, 19, "d19", CPU64);
    public static final Register d20 = new Register(84, 20, "d20", CPU64);

    public static final Register threadRegister = d20;
    public static final Register actionAndReasonReg = s32;
    public static final Register codeBufferOffsetReg = s33;

    // @formatter:off
    public static final Register[] cRegisters = {
        c0, c1, c2, c3, c4, c5, c6, c7
    };

    public static final Register[] sRegisters = {
        s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10,
        s11, s12, s13, s14, s15, s16, s17, s18, s19,
        s20, s21, s22, s23, s24, s25, s26, s27, s28,
        s29, s30, s31
    };

    public static final Register[] dRegisters = {
        d0, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, threadRegister
    };

    public static final Register[] qRegisters = {
        q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11, q12, q13, q14, q15
    };

    public static final Register[] allRegisters = {
        c0, c1, c2, c3, c4, c5, c6, c7, s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15,
        s16, s17, s18, s19, s20, s21, s22, s23, s24, s25, s26, s27, s28, s29, s30, s31,
        d0, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13,
        d14, d15, q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11,
        q12, q13, q14, q15,
        s32, s33, s34, s35, s36, s37, s38, s39,
        d16, d17, d18, d19, threadRegister
    };

    // @formatter:on

    public HSAIL() {
        super("HSAIL", 8, ByteOrder.LITTLE_ENDIAN, false, allRegisters, 0, 1, CPU128_OFFSET + CPU128_NUM << CPU128_SHIFT, 8);
    }

    public static int getStackOffset(Value reg) {
        return -(((StackSlot) reg).getRawOffset());
    }

    /**
     * The mapping to stack slots is always relative to the beginning of the spillseg.
     * {@link #getStackOffset(Value)} returns the positive version of the originally negative
     * offset. Then we back up from that by {@code argSize} in bytes. This ensures that slots of
     * different size do not overlap, even though we have converted from negative to positive
     * offsets.
     */
    public static int getStackOffsetStart(Value reg, int argSize) {
        int argSizeInBytes = argSize / 8;
        return getStackOffset(reg) - argSizeInBytes;
    }

    public static String mapRegister(Value arg) {
        return "$" + asRegister(arg).name;
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {
        if (!(platformKind instanceof Kind)) {
            return false;
        }
        Kind kind = (Kind) platformKind;
        if (category == CPU32) {
            switch (kind) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Float:
                    return true;
                case Long:
                case Double:
                case Object:
                    return false;
            }
        } else if (category == CPU64 || category == CPU128) {
            switch (kind) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Float:
                case Long:
                case Double:
                case Object:
                    return true;
            }
        }
        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        if (category == CPU32) {
            return Kind.Int;
        } else if (category == CPU64 || category == CPU128) {
            return Kind.Long;
        } else {
            return Kind.Illegal;
        }
    }
}

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
package com.oracle.graal.hotspot.snippets;
import static com.oracle.graal.hotspot.snippets.ArrayCopySnippets.*;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.Counter.*;
import static com.oracle.graal.snippets.Snippet.Arguments.*;
import static com.oracle.graal.snippets.Snippet.Multiple.*;

import java.io.*;
import java.util.*;

import sun.misc.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Fold;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.ri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.Arguments;
import com.oracle.graal.snippets.Snippet.Constant;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.SnippetTemplate.Cache;
import com.oracle.graal.snippets.nodes.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;

/**
 * Snippets used for implementing the type test of a checkcast instruction.
 *
 * The test first checks against the profiled types (if any) and then implements the
 * checks described in the paper <a href="http://dl.acm.org/citation.cfm?id=583821">
 * Fast subtype checking in the HotSpot JVM</a> by Cliff Click and John Rose.
 */
public class CheckCastSnippets implements SnippetsInterface {

    /**
     * Type test used when the type being tested against is a final type.
     */
    @Snippet
    public static Object checkcastExact(@Parameter("object") Object object, @Parameter("exactHub") Object exactHub, @Constant("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        if (objectHub != exactHub) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    /**
     * Type test used when the type being tested against is a restricted primary type.
     *
     * This test ignores use of hints altogether as the display-based type check only
     * involves one extra load where the second load should hit the same cache line as the
     * first.
     */
    @Snippet
    public static Object checkcastPrimary(@Parameter("hub") Object hub, @Parameter("object") Object object, @Constant("checkNull") boolean checkNull, @Constant("superCheckOffset") int superCheckOffset) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        if (UnsafeLoadNode.loadObject(objectHub, 0, superCheckOffset, true) != hub) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    /**
     * Type test used when the type being tested against is a restricted secondary type.
     */
    @Snippet
    public static Object checkcastSecondary(@Parameter("hub") Object hub, @Parameter("object") Object object, @Parameter(value = "hints", multiple = true) Object[] hints, @Constant("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Object hintHub = hints[i];
            if (hintHub == objectHub) {
                return object;
            }
        }
        if (!checkSecondarySubType(hub, objectHub)) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    /**
     * Type test used when the type being tested against is not known at compile time (e.g. the type test
     * in an object array store check).
     */
    @Snippet
    public static Object checkcastUnknown(@Parameter("hub") Object hub, @Parameter("object") Object object, @Parameter(value = "hints", multiple = true) Object[] hints, @Constant("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Object hintHub = hints[i];
            if (hintHub == objectHub) {
                return object;
            }
        }
        if (!checkUnknownSubType(hub, objectHub)) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    //This is used instead of a Java array read to avoid the array bounds check.
    static Object loadNonNullObjectElement(Object array, int index) {
        return UnsafeLoadNode.loadObject(array, arrayBaseOffset(CiKind.Object), index * arrayIndexScale(CiKind.Object), true);
    }

    static boolean checkSecondarySubType(Object t, Object s) {
        // if (S.cache == T) return true
        if (UnsafeLoadNode.loadObject(s, 0, secondarySuperCacheOffset(), true) == t) {
            return true;
        }

        // if (T == S) return true
        if (s == t) {
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Object[] secondarySupers = UnsafeCastNode.cast(UnsafeLoadNode.loadObject(s, 0, secondarySupersOffset(), true), Object[].class);

        for (int i = 0; i < secondarySupers.length; i++) {
            if (t == loadNonNullObjectElement(secondarySupers, i)) {
                DirectObjectStoreNode.store(s, secondarySuperCacheOffset(), 0, t);
                return true;
            }
        }

        return false;
    }

    static boolean checkUnknownSubType(Object t, Object s) {
        // int off = T.offset
        int superCheckOffset = UnsafeLoadNode.load(t, 0, superCheckOffsetOffset(), CiKind.Int);

        // if (T = S[off]) return true
        if (UnsafeLoadNode.loadObject(s, 0, superCheckOffset, true) == t) {
            return true;
        }

        // if (off != &cache) return false
        if (superCheckOffset != secondarySuperCacheOffset()) {
            return false;
        }

        // if (T == S) return true
        if (s == t) {
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Object[] secondarySupers = UnsafeCastNode.cast(UnsafeLoadNode.loadObject(s, 0, secondarySupersOffset(), true), Object[].class);
        for (int i = 0; i < secondarySupers.length; i++) {
            if (t == loadNonNullObjectElement(secondarySupers, i)) {
                DirectObjectStoreNode.store(s, secondarySuperCacheOffset(), 0, t);
                return true;
            }
        }

        return false;
    }

    /**
     * Counters for the various code paths through a type check.
     */
    public enum Counter {
        hintsHit("hit a hint type"),
        hintsMissed("missed the hint types"),
        exactType("tested type is (statically) final"),
        noHints("profile information is not used"),
        isNull("object tested is null"),
        exception("type test failed with a ClassCastException");

        final String description;
        final int index;
        long count;

        private Counter(String desc) {
            this.description = desc;
            this.index = ordinal();
        }

        @Fold
        static int countOffset() {
            try {
                return (int) Unsafe.getUnsafe().objectFieldOffset(Counter.class.getDeclaredField("count"));
            } catch (Exception e) {
                throw new GraalInternalError(e);
            }
        }

        /**
         * Increments this counter if counters are enabled. The body of this method has been carefully crafted
         * such that it contains no safepoints and no calls, neither of which are permissible in a snippet.
         * Also, increments are not guaranteed to be atomic which is acceptable for a counter.
         */
        void inc() {
            if (ENABLED) {
                DirectObjectStoreNode.store(this, countOffset(), 0, count + 1);
            }
        }

        static final Counter[] VALUES = values();
        static final boolean ENABLED = GraalOptions.CheckcastCounters;
    }

    /**
     * Type test used when {@link GraalOptions#CheckcastCounters} is enabled.
     */
    @Snippet
    public static Object checkcastCounters(@Parameter("hub") Object hub, @Parameter("object") Object object, @Parameter(value = "hints", multiple = true) Object[] hints, @Constant("hintsAreExact") boolean hintsAreExact) {
        if (object == null) {
            isNull.inc();
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        if (hints.length == 0) {
            noHints.inc();
            if (!checkUnknownSubType(hub, objectHub)) {
                exception.inc();
                DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
            }
        } else {
            // if we get an exact match: succeed immediately
            ExplodeLoopNode.explodeLoop();
            for (int i = 0; i < hints.length; i++) {
                Object hintHub = hints[i];
                if (hintHub == objectHub) {
                    if (hintsAreExact) {
                        exactType.inc();
                    } else {
                        hintsHit.inc();
                    }
                    return object;
                }
            }
            if (!hintsAreExact) {
                if (!checkUnknownSubType(hub, objectHub)) {
                    exception.inc();
                    DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
                } else {
                    hintsMissed.inc();
                }
            } else {
                exception.inc();
                DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
            }
        }
        return object;
    }

    @Fold
    private static int superCheckOffsetOffset() {
        return CompilerImpl.getInstance().getConfig().superCheckOffsetOffset;
    }

    @Fold
    private static int secondarySuperCacheOffset() {
        return CompilerImpl.getInstance().getConfig().secondarySuperCacheOffset;
    }

    @Fold
    private static int secondarySupersOffset() {
        return CompilerImpl.getInstance().getConfig().secondarySupersOffset;
    }

    @Fold
    private static int hubOffset() {
        return CompilerImpl.getInstance().getConfig().hubOffset;
    }

    public static void printCounter(PrintStream out, Counter c, long total) {
        double percent = total == 0D ? 0D : ((double) (c.count * 100)) / total;
        out.println(String.format("%16s: %5.2f%%%10d  // %s", c.name(), percent, c.count, c.description));
    }

    public static void printCounters(PrintStream out) {
        if (!Counter.ENABLED) {
            return;
        }
        Counter[] counters = Counter.values();
        Arrays.sort(counters, new Comparator<Counter>() {
            @Override
            public int compare(Counter o1, Counter o2) {
                if (o1.count > o2.count) {
                    return -1;
                } else if (o2.count > o1.count) {
                    return 1;
                }
                return 0;
            }

        });

        long total = 0;
        for (Counter c : counters) {
            total += c.count;
        }

        out.println();
        out.println("** Checkcast counters **");
        for (Counter c : counters) {
            printCounter(out, c, total);
        }
    }

    public static class Templates {

        private final Cache cache;
        private final RiResolvedMethod exact;
        private final RiResolvedMethod primary;
        private final RiResolvedMethod secondary;
        private final RiResolvedMethod unknown;
        private final RiResolvedMethod counters;
        private final RiRuntime runtime;

        public Templates(RiRuntime runtime) {
            this.runtime = runtime;
            this.cache = new Cache(runtime);
            try {
                exact = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastExact", Object.class, Object.class, boolean.class));
                primary = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastPrimary", Object.class, Object.class, boolean.class, int.class));
                secondary = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastSecondary", Object.class, Object.class, Object[].class, boolean.class));
                unknown = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastUnknown", Object.class, Object.class, Object[].class, boolean.class));
                counters = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastCounters", Object.class, Object.class, Object[].class, boolean.class));
            } catch (NoSuchMethodException e) {
                throw new GraalInternalError(e);
            }
        }

        /**
         * Lowers a checkcast node.
         */
        public void lower(CheckCastNode checkcast, CiLoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) checkcast.graph();
            ValueNode hub = checkcast.targetClassInstruction();
            ValueNode object = checkcast.object();
            TypeCheckHints hintInfo = new TypeCheckHints(checkcast.targetClass(), checkcast.profile(), tool.assumptions(), GraalOptions.CheckcastMinHintHitProbability, GraalOptions.CheckcastMaxHints);
            final HotSpotTypeResolvedImpl target = (HotSpotTypeResolvedImpl) checkcast.targetClass();
            boolean checkNull = !object.stamp().nonNull();
            Arguments arguments;
            SnippetTemplate.Key key;

            if (GraalOptions.CheckcastCounters) {
                HotSpotKlassOop[] hints = createHints(hintInfo);
                key = new SnippetTemplate.Key(counters).add("hints", multiple(Object.class, hints.length)).add("hintsAreExact", hintInfo.exact);
                arguments = arguments("hub", hub).add("object", object).add("hints", hints);
            } else if (target == null) {
                HotSpotKlassOop[] hints = createHints(hintInfo);
                key = new SnippetTemplate.Key(unknown).add("hints", multiple(Object.class, hints.length)).add("checkNull", checkNull);
                arguments = arguments("hub", hub).add("object", object).add("hints", hints);
            } else if (hintInfo.exact) {
                HotSpotKlassOop[] hints = createHints(hintInfo);
                assert hints.length == 1;
                key = new SnippetTemplate.Key(exact).add("checkNull", checkNull);
                arguments = arguments("object", object).add("exactHub", hints[0]);
            } else if (target.isPrimaryType()) {
                key = new SnippetTemplate.Key(primary).add("checkNull", checkNull).add("superCheckOffset", target.superCheckOffset());
                arguments = arguments("hub", hub).add("object", object);
            } else {
                HotSpotKlassOop[] hints = createHints(hintInfo);
                key = new SnippetTemplate.Key(secondary).add("hints", multiple(Object.class, hints.length)).add("checkNull", checkNull);
                arguments = arguments("hub", hub).add("object", object).add("hints", hints);
            }

            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering checkcast in %s: node=%s, template=%s, arguments=%s", graph, checkcast, template, arguments);
            template.instantiate(runtime, checkcast, checkcast, arguments);
            new DeadCodeEliminationPhase().apply(graph);
        }

        private static HotSpotKlassOop[] createHints(TypeCheckHints hints) {
            HotSpotKlassOop[] hintHubs = new HotSpotKlassOop[hints.types.length];
            for (int i = 0; i < hintHubs.length; i++) {
                hintHubs[i] = ((HotSpotType) hints.types[i]).klassOop();
            }
            return hintHubs;
        }
    }
}

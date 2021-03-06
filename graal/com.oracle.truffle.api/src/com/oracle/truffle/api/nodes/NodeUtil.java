/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.nodes;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;

import sun.misc.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.source.*;

/**
 * Utility class that manages the special access methods for node instances.
 */
public final class NodeUtil {

    /**
     * Interface that allows the customization of field offsets used for {@link Unsafe} field
     * accesses.
     */
    public interface FieldOffsetProvider {

        long objectFieldOffset(Field field);

        int getTypeSize(Class<?> clazz);
    }

    private static final FieldOffsetProvider unsafeFieldOffsetProvider = new FieldOffsetProvider() {

        @Override
        public long objectFieldOffset(Field field) {
            return unsafe.objectFieldOffset(field);
        }

        @Override
        public int getTypeSize(Class<?> clazz) {
            if (!clazz.isPrimitive()) {
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            } else if (clazz == int.class) {
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            } else {
                throw new UnsupportedOperationException("unsupported field type: " + clazz);
            }
        }
    };

    public static enum NodeFieldKind {
        /** The single {@link Node#getParent() parent} field. */
        PARENT,
        /** A field annotated with {@link Child}. */
        CHILD,
        /** A field annotated with {@link Children}. */
        CHILDREN,
        /** A normal non-child data field of the node. */
        DATA
    }

    /**
     * Information about a field in a {@link Node} class.
     */
    public static final class NodeField {

        private final NodeFieldKind kind;
        private final Class<?> type;
        private final String name;
        private long offset;

        protected NodeField(NodeFieldKind kind, Class<?> type, String name, long offset) {
            this.kind = kind;
            this.type = type;
            this.name = name;
            this.offset = offset;
        }

        public NodeFieldKind getKind() {
            return kind;
        }

        public Class<?> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public long getOffset() {
            return offset;
        }

        public Object loadValue(Node node) {
            if (type == boolean.class) {
                return unsafe.getBoolean(node, offset);
            } else if (type == byte.class) {
                return unsafe.getByte(node, offset);
            } else if (type == short.class) {
                return unsafe.getShort(node, offset);
            } else if (type == char.class) {
                return unsafe.getChar(node, offset);
            } else if (type == int.class) {
                return unsafe.getInt(node, offset);
            } else if (type == long.class) {
                return unsafe.getLong(node, offset);
            } else if (type == float.class) {
                return unsafe.getFloat(node, offset);
            } else if (type == double.class) {
                return unsafe.getDouble(node, offset);
            } else {
                return unsafe.getObject(node, offset);
            }
        }

        @Override
        public int hashCode() {
            return kind.hashCode() | type.hashCode() | name.hashCode() | ((Long) offset).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NodeField) {
                NodeField other = (NodeField) obj;
                return offset == other.offset && name.equals(other.name) && type.equals(other.type) && kind.equals(other.kind);
            }
            return false;
        }
    }

    /**
     * Information about a {@link Node} class. A single instance of this class is allocated for
     * every subclass of {@link Node} that is used.
     */
    public static final class NodeClass {
        private static final ClassValue<NodeClass> nodeClasses = new ClassValue<NodeClass>() {
            @SuppressWarnings("unchecked")
            @Override
            protected NodeClass computeValue(final Class<?> clazz) {
                assert Node.class.isAssignableFrom(clazz);
                return AccessController.doPrivileged(new PrivilegedAction<NodeClass>() {
                    public NodeClass run() {
                        return new NodeClass((Class<? extends Node>) clazz, unsafeFieldOffsetProvider);
                    }
                });
            }
        };

        // The comprehensive list of all fields.
        private final NodeField[] fields;
        // Separate arrays for the frequently accessed field offsets.
        private final long parentOffset;
        private final long[] childOffsets;
        private final long[] childrenOffsets;
        private final Class<? extends Node> clazz;

        public static NodeClass get(Class<? extends Node> clazz) {
            return nodeClasses.get(clazz);
        }

        public NodeClass(Class<? extends Node> clazz, FieldOffsetProvider fieldOffsetProvider) {
            List<NodeField> fieldsList = new ArrayList<>();
            long parentFieldOffset = -1;
            List<Long> childOffsetsList = new ArrayList<>();
            List<Long> childrenOffsetsList = new ArrayList<>();

            for (Field field : getAllFields(clazz)) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                NodeFieldKind kind;
                if (field.getDeclaringClass() == Node.class && field.getName().equals("parent")) {
                    assert Node.class.isAssignableFrom(field.getType());
                    kind = NodeFieldKind.PARENT;
                    parentFieldOffset = fieldOffsetProvider.objectFieldOffset(field);
                } else if (field.getAnnotation(Child.class) != null) {
                    checkChildField(field);
                    kind = NodeFieldKind.CHILD;
                    childOffsetsList.add(fieldOffsetProvider.objectFieldOffset(field));
                } else if (field.getAnnotation(Children.class) != null) {
                    checkChildrenField(field);
                    kind = NodeFieldKind.CHILDREN;
                    childrenOffsetsList.add(fieldOffsetProvider.objectFieldOffset(field));
                } else {
                    kind = NodeFieldKind.DATA;
                }
                fieldsList.add(new NodeField(kind, field.getType(), field.getName(), fieldOffsetProvider.objectFieldOffset(field)));
            }

            if (parentFieldOffset < 0) {
                throw new AssertionError("parent field not found");
            }

            this.fields = fieldsList.toArray(new NodeField[fieldsList.size()]);
            this.parentOffset = parentFieldOffset;
            this.childOffsets = toLongArray(childOffsetsList);
            this.childrenOffsets = toLongArray(childrenOffsetsList);
            this.clazz = clazz;
        }

        private static void checkChildField(Field field) {
            if (!(Node.class.isAssignableFrom(field.getType()) || field.getType().isInterface())) {
                throw new AssertionError("@Child field type must be a subclass of Node or an interface (" + field + ")");
            }
            if (Modifier.isFinal(field.getModifiers())) {
                throw new AssertionError("@Child field must not be final (" + field + ")");
            }
        }

        private static void checkChildrenField(Field field) {
            if (!(field.getType().isArray() && (Node.class.isAssignableFrom(field.getType().getComponentType()) || field.getType().getComponentType().isInterface()))) {
                throw new AssertionError("@Children field type must be an array of a subclass of Node or an interface (" + field + ")");
            }
            if (!Modifier.isFinal(field.getModifiers())) {
                throw new AssertionError("@Children field must be final (" + field + ")");
            }
        }

        public NodeField[] getFields() {
            return fields;
        }

        public long getParentOffset() {
            return parentOffset;
        }

        public long[] getChildOffsets() {
            return childOffsets;
        }

        public long[] getChildrenOffsets() {
            return childrenOffsets;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(fields) ^ Arrays.hashCode(childOffsets) ^ Arrays.hashCode(childrenOffsets) ^ ((Long) parentOffset).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NodeClass) {
                NodeClass other = (NodeClass) obj;
                return Arrays.equals(fields, other.fields) && Arrays.equals(childOffsets, other.childOffsets) && Arrays.equals(childrenOffsets, other.childrenOffsets) &&
                                parentOffset == other.parentOffset;
            }
            return false;
        }

        public Iterator<Node> makeIterator(Node node) {
            assert clazz.isInstance(node);
            return new NodeIterator(node);
        }

        private final class NodeIterator implements Iterator<Node> {
            private final Node node;
            private final int childrenCount;
            private int index;

            protected NodeIterator(Node node) {
                this.node = node;
                this.index = 0;
                this.childrenCount = childrenCount();
            }

            private int childrenCount() {
                int nodeCount = childOffsets.length;
                for (long fieldOffset : childrenOffsets) {
                    Object[] children = ((Object[]) unsafe.getObject(node, fieldOffset));
                    if (children != null) {
                        nodeCount += children.length;
                    }
                }
                return nodeCount;
            }

            private Node nodeAt(int idx) {
                int nodeCount = childOffsets.length;
                if (idx < nodeCount) {
                    return (Node) unsafe.getObject(node, childOffsets[idx]);
                } else {
                    for (long fieldOffset : childrenOffsets) {
                        Object[] nodeArray = (Object[]) unsafe.getObject(node, fieldOffset);
                        if (idx < nodeCount + nodeArray.length) {
                            return (Node) nodeArray[idx - nodeCount];
                        }
                        nodeCount += nodeArray.length;
                    }
                }
                return null;
            }

            private void forward() {
                if (index < childrenCount) {
                    index++;
                }
            }

            public boolean hasNext() {
                return index < childrenCount;
            }

            public Node next() {
                try {
                    return nodeAt(index);
                } finally {
                    forward();
                }
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        }
    }

    static Iterator<Node> makeIterator(Node node) {
        return NodeClass.get(node.getClass()).makeIterator(node);
    }

    public static Iterator<Node> makeRecursiveIterator(Node node) {
        return new RecursiveNodeIterator(node);
    }

    private static final class RecursiveNodeIterator implements Iterator<Node> {
        private final List<Iterator<Node>> iteratorStack = new ArrayList<>();

        public RecursiveNodeIterator(final Node node) {
            iteratorStack.add(new Iterator<Node>() {

                private boolean visited;

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                public Node next() {
                    if (visited) {
                        throw new NoSuchElementException();
                    }
                    visited = true;
                    return node;
                }

                public boolean hasNext() {
                    return !visited;
                }
            });
        }

        public boolean hasNext() {
            return peekIterator() != null;
        }

        public Node next() {
            Iterator<Node> iterator = peekIterator();
            if (iterator == null) {
                throw new NoSuchElementException();
            }

            Node node = iterator.next();
            if (node != null) {
                Iterator<Node> childIterator = makeIterator(node);
                if (childIterator.hasNext()) {
                    iteratorStack.add(childIterator);
                }
            }
            return node;
        }

        private Iterator<Node> peekIterator() {
            int tos = iteratorStack.size() - 1;
            while (tos >= 0) {
                Iterator<Node> iterable = iteratorStack.get(tos);
                if (iterable.hasNext()) {
                    return iterable;
                } else {
                    iteratorStack.remove(tos--);
                }
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static long[] toLongArray(List<Long> list) {
        long[] array = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static final Unsafe unsafe = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Node> T cloneNode(T orig) {
        final Node clone = orig.copy();
        NodeClass nodeClass = NodeClass.get(clone.getClass());

        unsafe.putObject(clone, nodeClass.parentOffset, null);

        for (long fieldOffset : nodeClass.childOffsets) {
            Node child = (Node) unsafe.getObject(orig, fieldOffset);
            if (child != null) {
                Node clonedChild = cloneNode(child);
                unsafe.putObject(clonedChild, nodeClass.parentOffset, clone);
                unsafe.putObject(clone, fieldOffset, clonedChild);
            }
        }
        for (long fieldOffset : nodeClass.childrenOffsets) {
            Object[] children = (Object[]) unsafe.getObject(orig, fieldOffset);
            if (children != null) {
                Object[] clonedChildren = (Object[]) Array.newInstance(children.getClass().getComponentType(), children.length);
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        Node clonedChild = cloneNode((Node) children[i]);
                        clonedChildren[i] = clonedChild;
                        unsafe.putObject(clonedChild, nodeClass.parentOffset, clone);
                    }
                }
                unsafe.putObject(clone, fieldOffset, clonedChildren);
            }
        }
        return (T) clone;
    }

    public static List<Node> findNodeChildren(Node node) {
        List<Node> nodes = new ArrayList<>();
        NodeClass nodeClass = NodeClass.get(node.getClass());

        for (long fieldOffset : nodeClass.childOffsets) {
            Object child = unsafe.getObject(node, fieldOffset);
            if (child != null) {
                nodes.add((Node) child);
            }
        }
        for (long fieldOffset : nodeClass.childrenOffsets) {
            Object[] children = (Object[]) unsafe.getObject(node, fieldOffset);
            if (children != null) {
                for (Object child : children) {
                    if (child != null) {
                        nodes.add((Node) child);
                    }
                }
            }
        }

        return nodes;
    }

    public static boolean replaceChild(Node parent, Node oldChild, Node newChild) {
        NodeClass nodeClass = NodeClass.get(parent.getClass());

        for (long fieldOffset : nodeClass.getChildOffsets()) {
            if (unsafe.getObject(parent, fieldOffset) == oldChild) {
                assert assertAssignable(nodeClass, fieldOffset, newChild);
                unsafe.putObject(parent, fieldOffset, newChild);
                return true;
            }
        }

        for (long fieldOffset : nodeClass.getChildrenOffsets()) {
            Object arrayObject = unsafe.getObject(parent, fieldOffset);
            if (arrayObject != null) {
                Object[] array = (Object[]) arrayObject;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == oldChild) {
                        assert assertAssignable(nodeClass, fieldOffset, newChild);
                        array[i] = newChild;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean assertAssignable(NodeClass clazz, long fieldOffset, Object newValue) {
        if (newValue == null) {
            return true;
        }
        for (NodeField field : clazz.getFields()) {
            if (field.getOffset() == fieldOffset) {
                if (field.getKind() == NodeFieldKind.CHILD) {
                    if (field.getType().isAssignableFrom(newValue.getClass())) {
                        return true;
                    } else {
                        assert false : "Child class " + newValue.getClass().getName() + " is not assignable to field \"" + field.getName() + "\" of type " + field.getType().getName();
                        return false;
                    }
                } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                    if (field.getType().getComponentType().isAssignableFrom(newValue.getClass())) {
                        return true;
                    } else {
                        assert false : "Child class " + newValue.getClass().getName() + " is not assignable to field \"" + field.getName() + "\" of type " + field.getType().getName();
                        return false;
                    }
                }
            }
        }
        throw new IllegalArgumentException();
    }

    /** Returns all declared fields in the class hierarchy. */
    private static Field[] getAllFields(Class<? extends Object> clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        if (clazz.getSuperclass() != null) {
            return concat(getAllFields(clazz.getSuperclass()), declaredFields);
        }
        return declaredFields;
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Get the nth parent of a node, where the 0th parent is the node itself. Returns null if there
     * are less than n ancestors.
     */
    public static Node getNthParent(Node node, int n) {
        Node parent = node;

        for (int i = 0; i < n; i++) {
            parent = parent.getParent();

            if (parent == null) {
                return null;
            }
        }

        return parent;
    }

    /** find annotation in class/interface hierarchy. */
    public static <T extends Annotation> T findAnnotation(Class<?> clazz, Class<T> annotationClass) {
        if (clazz.getAnnotation(annotationClass) != null) {
            return clazz.getAnnotation(annotationClass);
        } else {
            for (Class<?> intf : clazz.getInterfaces()) {
                if (intf.getAnnotation(annotationClass) != null) {
                    return intf.getAnnotation(annotationClass);
                }
            }
            if (clazz.getSuperclass() != null) {
                return findAnnotation(clazz.getSuperclass(), annotationClass);
            }
        }
        return null;
    }

    public static <T> T findParent(Node start, Class<T> clazz) {
        Node parent = start.getParent();
        if (parent == null) {
            return null;
        } else if (clazz.isInstance(parent)) {
            return clazz.cast(parent);
        } else {
            return findParent(parent, clazz);
        }
    }

    public static <T> List<T> findAllParents(Node start, Class<T> clazz) {
        List<T> parents = new ArrayList<>();
        T parent = findParent(start, clazz);
        while (parent != null) {
            parents.add(parent);
            parent = findParent((Node) parent, clazz);
        }
        return parents;
    }

    public static List<Node> collectNodes(Node parent, Node child) {
        List<Node> nodes = new ArrayList<>();
        Node current = child;
        while (current != null) {
            nodes.add(current);
            if (current == parent) {
                return nodes;
            }
            current = current.getParent();
        }
        throw new IllegalArgumentException("Node " + parent + " is not a parent of " + child + ".");
    }

    public static <T> T findFirstNodeInstance(Node root, Class<T> clazz) {
        for (Node childNode : findNodeChildren(root)) {
            if (clazz.isInstance(childNode)) {
                return clazz.cast(childNode);
            } else {
                T node = findFirstNodeInstance(childNode, clazz);
                if (node != null) {
                    return node;
                }
            }
        }
        return null;
    }

    public static <T> List<T> findAllNodeInstances(final Node root, final Class<T> clazz) {
        final List<T> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add(clazz.cast(node));
                }
                return true;
            }
        });
        return nodeList;
    }

    /**
     * Like {@link #findAllNodeInstances(Node, Class)} but do not visit children of found nodes.
     */
    public static <T> List<T> findNodeInstancesShallow(final Node root, final Class<T> clazz) {
        final List<T> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add(clazz.cast(node));
                    return false;
                }
                return true;
            }
        });
        return nodeList;
    }

    public static int countNodes(Node root) {
        Iterator<Node> nodeIterator = makeRecursiveIterator(root);
        int count = 0;
        while (nodeIterator.hasNext()) {
            nodeIterator.next();
            count++;
        }
        return count;
    }

    public static int countNodes(Node root, NodeCountFilter filter) {
        Iterator<Node> nodeIterator = makeRecursiveIterator(root);
        int count = 0;
        while (nodeIterator.hasNext()) {
            Node node = nodeIterator.next();
            if (node != null && filter.isCounted(node)) {
                count++;
            }
        }
        return count;
    }

    public interface NodeCountFilter {

        boolean isCounted(Node node);

    }

    public static String printCompactTreeToString(Node node) {
        StringWriter out = new StringWriter();
        printCompactTree(new PrintWriter(out), null, node, 1);
        return out.toString();
    }

    public static void printCompactTree(OutputStream out, Node node) {
        printCompactTree(new PrintWriter(out), null, node, 1);
    }

    private static void printCompactTree(PrintWriter p, Node parent, Node node, int level) {
        if (node == null) {
            return;
        }
        for (int i = 0; i < level; i++) {
            p.print("  ");
        }
        if (parent == null) {
            p.println(nodeName(node));
        } else {
            p.print(getNodeFieldName(parent, node, "unknownField"));
            p.print(" = ");
            p.println(nodeName(node));
        }

        for (Node child : node.getChildren()) {
            printCompactTree(p, node, child, level + 1);
        }
        p.flush();
    }

    public static String printSourceAttributionTree(Node node) {
        StringWriter out = new StringWriter();
        printSourceAttributionTree(new PrintWriter(out), null, node, 1);
        return out.toString();
    }

    public static void printSourceAttributionTree(OutputStream out, Node node) {
        printSourceAttributionTree(new PrintWriter(out), null, node, 1);
    }

    private static void printSourceAttributionTree(PrintWriter p, Node parent, Node node, int level) {
        if (node == null) {
            return;
        }
        if (parent == null) {
            // Add some preliminary information before starting with the root node
            final SourceSection sourceSection = node.getSourceSection();
            if (sourceSection != null) {
                final String txt = sourceSection.getSource().getCode();
                p.println("Full source len=(" + txt.length() + ")  ___" + txt + "___");
                p.println("AST source attribution:");
            }
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("| ");
        }

        if (parent != null) {
            sb.append(getNodeFieldName(parent, node, ""));
        }

        sb.append("  (" + node.getClass().getSimpleName() + ")  ");
        sb.append(displaySourceAttribution(node));
        p.println(sb.toString());

        for (Node child : node.getChildren()) {
            printSourceAttributionTree(p, node, child, level + 1);
        }
        p.flush();
    }

    private static String getNodeFieldName(Node parent, Node node, String defaultName) {
        NodeField[] fields = NodeClass.get(parent.getClass()).fields;
        for (NodeField field : fields) {
            Object value = field.loadValue(parent);
            if (field.getKind() == NodeFieldKind.CHILD && value == node) {
                return field.getName();
            } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                int index = 0;
                for (Object arrayNode : (Object[]) value) {
                    if (arrayNode == node) {
                        return field.getName() + "[" + index + "]";
                    }
                    index++;
                }
            }
        }
        return defaultName;
    }

    /**
     * Prints a human readable form of a {@link Node} AST to the given {@link PrintStream}. This
     * print method does not check for cycles in the node structure.
     *
     * @param out the stream to print to.
     * @param node the root node to write
     */
    public static void printTree(OutputStream out, Node node) {
        printTree(new PrintWriter(out), node);
    }

    public static String printTreeToString(Node node) {
        StringWriter out = new StringWriter();
        printTree(new PrintWriter(out), node);
        return out.toString();
    }

    public static void printTree(PrintWriter p, Node node) {
        printTree(p, node, 1);
        p.println();
        p.flush();
    }

    private static void printTree(PrintWriter p, Node node, int level) {
        if (node == null) {
            p.print("null");
            return;
        }

        p.print(nodeName(node));

        ArrayList<NodeField> childFields = new ArrayList<>();
        String sep = "";
        p.print("(");
        for (NodeField field : NodeClass.get(node.getClass()).fields) {
            if (field.getKind() == NodeFieldKind.CHILD || field.getKind() == NodeFieldKind.CHILDREN) {
                childFields.add(field);
            } else if (field.getKind() == NodeFieldKind.DATA) {
                p.print(sep);
                sep = ", ";

                p.print(field.getName());
                p.print(" = ");
                p.print(field.loadValue(node));
            }
        }
        p.print(")");

        if (childFields.size() != 0) {
            p.print(" {");
            for (NodeField field : childFields) {
                printNewLine(p, level);
                p.print(field.getName());

                Object value = field.loadValue(node);
                if (value == null) {
                    p.print(" = null ");
                } else if (field.getKind() == NodeFieldKind.CHILD) {
                    p.print(" = ");
                    printTree(p, (Node) value, level + 1);
                } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                    printChildren(p, level, value);
                }
            }
            printNewLine(p, level - 1);
            p.print("}");
        }
    }

    private static void printChildren(PrintWriter p, int level, Object value) {
        String sep;
        Object[] children = (Object[]) value;
        p.print(" = [");
        sep = "";
        for (Object child : children) {
            p.print(sep);
            sep = ", ";
            printTree(p, (Node) child, level + 1);
        }
        p.print("]");
    }

    private static void printNewLine(PrintWriter p, int level) {
        p.println();
        for (int i = 0; i < level; i++) {
            p.print("    ");
        }
    }

    private static String nodeName(Node node) {
        return node.getClass().getSimpleName();
    }

    private static String displaySourceAttribution(Node node) {
        final SourceSection section = node.getSourceSection();
        if (section instanceof NullSourceSection) {
            return "source: " + section.getShortDescription();
        }
        if (section != null) {
            final String srcText = section.getCode();
            final StringBuilder sb = new StringBuilder();
            sb.append("source:");
            sb.append(" (" + section.getCharIndex() + "," + (section.getCharEndIndex() - 1) + ")");
            sb.append(" len=" + srcText.length());
            sb.append(" text=\"" + srcText + "\"");
            return sb.toString();
        }
        return "";
    }

    public static boolean verify(Node root) {
        Iterable<Node> children = root.getChildren();
        for (Node child : children) {
            if (child != null) {
                if (child.getParent() != root) {
                    throw new AssertionError(toStringWithClass(child) + ": actual parent=" + toStringWithClass(child.getParent()) + " expected parent=" + toStringWithClass(root));
                }
                verify(child);
            }
        }
        return true;
    }

    private static String toStringWithClass(Object obj) {
        return obj == null ? "null" : obj + "(" + obj.getClass().getName() + ")";
    }

    static void traceRewrite(Node oldNode, Node newNode, CharSequence reason) {
        if (TruffleOptions.TraceRewritesFilterFromCost != null) {
            if (filterByKind(oldNode, TruffleOptions.TraceRewritesFilterFromCost)) {
                return;
            }
        }

        if (TruffleOptions.TraceRewritesFilterToCost != null) {
            if (filterByKind(newNode, TruffleOptions.TraceRewritesFilterToCost)) {
                return;
            }
        }

        String filter = TruffleOptions.TraceRewritesFilterClass;
        Class<? extends Node> from = oldNode.getClass();
        Class<? extends Node> to = newNode.getClass();
        if (filter != null && (filterByContainsClassName(from, filter) || filterByContainsClassName(to, filter))) {
            return;
        }

        final SourceSection reportedSourceSection = oldNode.getEncapsulatingSourceSection();

        PrintStream out = System.out;
        out.printf("[truffle]   rewrite %-50s |From %-40s |To %-40s |Reason %s%s%n", oldNode.toString(), formatNodeInfo(oldNode), formatNodeInfo(newNode),
                        reason != null && reason.length() > 0 ? reason : "unknown", reportedSourceSection != null ? " at " + reportedSourceSection.getShortDescription() : "");
    }

    private static String formatNodeInfo(Node node) {
        String cost = "?";
        switch (node.getCost()) {
            case NONE:
                cost = "G";
                break;
            case MONOMORPHIC:
                cost = "M";
                break;
            case POLYMORPHIC:
                cost = "P";
                break;
            case MEGAMORPHIC:
                cost = "G";
                break;
            default:
                cost = "?";
                break;
        }
        return cost + " " + node.getClass().getSimpleName();
    }

    private static boolean filterByKind(Node node, NodeCost cost) {
        return node.getCost() == cost;
    }

    private static boolean filterByContainsClassName(Class<? extends Node> from, String filter) {
        Class<?> currentFrom = from;
        while (currentFrom != null) {
            if (currentFrom.getName().contains(filter)) {
                return false;
            }
            currentFrom = currentFrom.getSuperclass();
        }
        return true;
    }
}

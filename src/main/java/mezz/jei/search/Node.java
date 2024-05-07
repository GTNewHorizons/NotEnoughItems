/*
 * Copyright 2012 Alessandro Bahgat Shehata Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package mezz.jei.search;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.Set;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMaps;
import mezz.jei.util.Substring;

/**
 * Represents a node of the generalized suffix tree graph
 *
 * @see GeneralizedSuffixTree
 */
public class Node<T> {

    /**
     * The payload array used to store the data (indexes) associated with this node. In this case, it is used to store
     * all property indexes.
     */
    @Nullable
    private T[] data;

    /**
     * The set of edges starting from this node
     */
    @Nullable
    private Char2ObjectMap<Edge<T>> edges;

    /**
     * The suffix link as described in Ukkonen's paper. if str is the string denoted by the path from the root to this,
     * this.suffix is the node denoted by the path that corresponds to str without the first char.
     */
    @Nullable
    private Node<T> suffix;

    Node() {
        this.data = null;
        this.edges = null;
        this.suffix = null;
    }

    /**
     * Gets data from the payload of both this node and its children, the string representation of the path to this node
     * is a substring of the one of the children nodes.
     */
    void getData(final Set<T> ret) {
        if (data != null) {
            ret.addAll(Arrays.asList(data));
        }
        if (edges != null) {
            for (Edge<T> e : edges.values()) {
                e.getDest().getData(ret);
            }
        }
    }

    /**
     * Adds the given <tt>index</tt> to the set of indexes associated with <tt>this</tt> returns false if this node
     * already contains the ref
     */
    boolean addRef(T index) {
        if (contains(index)) {
            return false;
        }
        addValue(index);
        Node<T> iter = this.suffix;
        while (iter != null) {
            if (!iter.contains(index)) {
                iter.addValue(index);
                iter = iter.suffix;
            } else {
                break;
            }
        }
        return true;
    }

    /**
     * Tests whether a node contains a reference to the given index.
     *
     * @param index the index to look for
     * @return true <tt>this</tt> contains a reference to index
     */
    protected boolean contains(T index) {
        if (data == null) {
            return false;
        }
        for (T id : data) {
            if (id == index) {
                return true;
            }
        }
        return false;
    }

    protected void addEdge(Edge<T> e) {
        if (this.edges == null) {
            this.edges = Char2ObjectMaps.singleton(e.charAt(0), e);
        } else if (this.edges instanceof Char2ObjectMaps.Singleton) {
            Char2ObjectMap.Entry<Edge<T>> existingEdge = edges.char2ObjectEntrySet().iterator().next();
            this.edges = new Char2ObjectArrayMap<>(2);
            this.edges.put(existingEdge.getCharKey(), existingEdge.getValue());
            this.edges.put(e.charAt(0), e);
        } else {
            this.edges.put(e.charAt(0), e);
        }
    }

    @Nullable
    Edge<T> getEdge(char ch) {
        return edges == null ? null : edges.get(ch);
    }

    @Nullable
    Edge<T> getEdge(Substring substring) {
        if (substring.isEmpty()) {
            return null;
        }
        return edges == null ? null : edges.get(substring.charAt(0));
    }

    @Nullable
    Node<T> getSuffix() {
        return suffix;
    }

    void setSuffix(Node<T> suffix) {
        this.suffix = suffix;
    }

    protected void addValue(T index) {
        if (this.data == null) {
            this.data = (T[]) new Object[] { index };
        } else {
            this.data = ArrayUtils.add(this.data, index);
        }
    }

    @Override
    public String toString() {
        return "Node: size:" + (data == null ? "nil" : data.length) + " Edges: " + edges;
    }

    public IntSummaryStatistics nodeSizeStats() {
        return nodeSizes().summaryStatistics();
    }

    private IntStream nodeSizes() {
        return IntStream.concat(
                IntStream.of(data == null ? 0 : data.length),
                edges == null ? IntStream.of(0) : edges.values().stream().flatMapToInt(e -> e.getDest().nodeSizes()));
    }

    public String nodeEdgeStats() {
        IntSummaryStatistics edgeCounts = nodeEdgeCounts().summaryStatistics();
        IntSummaryStatistics edgeLengths = nodeEdgeLengths().summaryStatistics();
        return "Edge counts: " + edgeCounts + "\nEdge lengths: " + edgeLengths;
    }

    private IntStream nodeEdgeCounts() {
        return edges == null ? IntStream.of()
                : IntStream.concat(
                        IntStream.of(edges.size()),
                        edges.values().stream().map(Edge::getDest).flatMapToInt(Node::nodeEdgeCounts));
    }

    private IntStream nodeEdgeLengths() {
        return edges == null ? IntStream.of()
                : IntStream.concat(
                        edges.values().stream().mapToInt(Edge::length),
                        edges.values().stream().map(Edge::getDest).flatMapToInt(Node::nodeEdgeLengths));
    }

    public void printTree(PrintWriter out, boolean includeSuffixLinks) {
        out.println("digraph {");
        out.println("\trankdir = LR;");
        out.println("\tordering = out;");
        out.println("\tedge [arrowsize=0.4,fontsize=10]");
        out.println(
                "\t" + nodeId(this)
                        + " [label=\"\",style=filled,fillcolor=lightgrey,shape=circle,width=.1,height=.1];");
        out.println("//------leaves------");
        printLeaves(out);
        out.println("//------internal nodes------");
        printInternalNodes(this, out);
        out.println("//------edges------");
        printEdges(out);
        if (includeSuffixLinks) {
            out.println("//------suffix links------");
            printSLinks(out);
        }
        out.println("}");
    }

    private void printLeaves(PrintWriter out) {
        if (edges == null) {
            out.println(
                    "\t" + nodeId(this)
                            + " [label=\""
                            + data
                            + "\",shape=point,style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
        } else {
            for (Edge<T> edge : edges.values()) {
                edge.getDest().printLeaves(out);
            }
        }
    }

    private void printInternalNodes(Node<T> root, PrintWriter out) {
        if (this != root && edges != null) {
            out.println(
                    "\t" + nodeId(this)
                            + " [label=\""
                            + data
                            + "\",style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
        }
        if (edges != null) {
            for (Edge<T> edge : edges.values()) {
                edge.getDest().printInternalNodes(root, out);
            }
        }
    }

    private void printEdges(PrintWriter out) {
        if (edges != null) {
            for (Edge<T> edge : edges.values()) {
                Node<T> child = edge.getDest();
                out.println(
                        "\t" + nodeId(this) + " -> " + nodeId(child) + " [label=\"" + edge.commit() + "\",weight=10]");
                child.printEdges(out);
            }
        }
    }

    private void printSLinks(PrintWriter out) {
        if (suffix != null) {
            out.println("\t" + nodeId(this) + " -> " + nodeId(suffix) + " [label=\"\",weight=0,style=dotted]");
        }
        if (edges != null) {
            for (Edge<T> edge : edges.values()) {
                edge.getDest().printSLinks(out);
            }
        }
    }

    private static <T> String nodeId(Node<T> node) {
        return "node" + Integer.toHexString(node.hashCode()).toUpperCase();
    }

    /**
     * The root node can have a lot of values added to it because so many suffix links point to it. The values are never
     * read from here though. This class makes sure we don't accumulate a ton of useless values in the root node.
     */
    public static class Root<T> extends Node<T> {

        @Override
        protected boolean contains(T value) {
            return true;
        }

        @Override
        protected void addValue(T index) {}

    }

}

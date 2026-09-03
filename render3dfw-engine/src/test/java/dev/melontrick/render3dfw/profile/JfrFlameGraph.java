package dev.melontrick.render3dfw.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

public final class JfrFlameGraph {
    private static final String PACKAGE_PREFIX = "dev.melontrick.render3dfw";
    private static final double WIDTH = 1600.0;
    private static final double LEFT = 12.0;
    private static final double TOP = 58.0;
    private static final double FRAME_HEIGHT = 20.0;

    private JfrFlameGraph() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("expected input, output and title");
        }
        Path input = Path.of(arguments[0]);
        Path output = Path.of(arguments[1]);
        Node root = read(input);
        if (root.samples == 0L) {
            throw new IllegalStateException("recording contains no relevant execution samples");
        }
        Files.createDirectories(output.getParent());
        Files.writeString(output, render(root, arguments[2]), StandardCharsets.UTF_8);
        System.out.printf("flamegraph=%s samples=%d%n", output, root.samples);
    }

    private static Node read(Path input) throws IOException {
        Node root = new Node("root");
        try (RecordingFile recording = new RecordingFile(input)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (!event.getEventType().getName().equals("jdk.ExecutionSample")) {
                    continue;
                }
                RecordedStackTrace trace = event.getStackTrace();
                if (trace == null || trace.getFrames().isEmpty() || !isRelevant(trace.getFrames())) {
                    continue;
                }
                List<String> stack = new ArrayList<>(trace.getFrames().size());
                for (int index = trace.getFrames().size() - 1; index >= 0; index--) {
                    stack.add(label(trace.getFrames().get(index)));
                }
                root.add(stack, 0);
            }
        }
        return root;
    }

    private static boolean isRelevant(List<RecordedFrame> frames) {
        for (RecordedFrame frame : frames) {
            if (frame.getMethod().getType().getName().startsWith(PACKAGE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static String label(RecordedFrame frame) {
        RecordedMethod method = frame.getMethod();
        return method.getType().getName() + "." + method.getName();
    }

    private static String render(Node root, String title) {
        int depth = root.depth();
        double graphWidth = WIDTH - LEFT * 2.0;
        double height = TOP + depth * FRAME_HEIGHT + 34.0;
        StringBuilder svg = new StringBuilder(256_000);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append((int) WIDTH)
                .append("\" height=\"")
                .append((int) height)
                .append("\" viewBox=\"0 0 ")
                .append((int) WIDTH)
                .append(' ')
                .append((int) height)
                .append(
                        "\">\n<style>text{font-family:Consolas,monospace;fill:#161616} .title{font:20px sans-serif;font-weight:600} .meta{font:12px sans-serif;fill:#555} rect{stroke:#fff;stroke-width:.5}</style>\n")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#fafafa\"/>\n")
                .append("<text class=\"title\" x=\"")
                .append(LEFT)
                .append("\" y=\"27\">")
                .append(escape(title))
                .append("</text>\n<text class=\"meta\" x=\"")
                .append(LEFT)
                .append("\" y=\"46\">")
                .append(root.samples)
                .append(" execution samples · wider frames consumed more sampled CPU time</text>\n");
        renderChildren(svg, root, LEFT, graphWidth, 1, depth, root.samples);
        svg.append("</svg>\n");
        return svg.toString();
    }

    private static void renderChildren(
            StringBuilder svg,
            Node node,
            double x,
            double width,
            int currentDepth,
            int maximumDepth,
            long totalSamples) {
        List<Node> children = new ArrayList<>(node.children.values());
        children.sort(Comparator.comparing(child -> child.name));
        double cursor = x;
        for (Node child : children) {
            double childWidth = width * child.samples / node.samples;
            if (childWidth >= 0.2) {
                double y = TOP + (maximumDepth - currentDepth) * FRAME_HEIGHT;
                appendFrame(svg, child, cursor, y, childWidth, totalSamples);
                renderChildren(svg, child, cursor, childWidth, currentDepth + 1, maximumDepth, totalSamples);
            }
            cursor += childWidth;
        }
    }

    private static void appendFrame(StringBuilder svg, Node node, double x, double y, double width, long totalSamples) {
        double percentage = node.samples * 100.0 / totalSamples;
        svg.append("<g><title>")
                .append(escape(node.name))
                .append(" · ")
                .append(node.samples)
                .append(" samples · ")
                .append(String.format(java.util.Locale.ROOT, "%.2f", percentage))
                .append("%</title><rect x=\"")
                .append(format(x))
                .append("\" y=\"")
                .append(format(y))
                .append("\" width=\"")
                .append(format(width))
                .append("\" height=\"")
                .append(format(FRAME_HEIGHT - 1.0))
                .append("\" rx=\"2\" fill=\"")
                .append(color(node.name))
                .append("\"/>");
        int availableCharacters = Math.max(0, (int) (width / 7.2) - 1);
        if (availableCharacters >= 4) {
            String text = node.name.length() <= availableCharacters
                    ? node.name
                    : node.name.substring(0, availableCharacters - 2) + "..";
            svg.append("<text x=\"")
                    .append(format(x + 3.0))
                    .append("\" y=\"")
                    .append(format(y + 14.0))
                    .append("\" font-size=\"11\">")
                    .append(escape(text))
                    .append("</text>");
        }
        svg.append("</g>\n");
    }

    private static String color(String value) {
        int hash = value.hashCode();
        int red = 205 + Math.floorMod(hash, 45);
        int green = 75 + Math.floorMod(hash >>> 8, 105);
        int blue = 45 + Math.floorMod(hash >>> 16, 55);
        return "rgb(" + red + ',' + green + ',' + blue + ')';
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final class Node {
        private final String name;
        private final Map<String, Node> children = new HashMap<>();
        private long samples;

        private Node(String name) {
            this.name = name;
        }

        private void add(List<String> stack, int index) {
            samples++;
            if (index < stack.size()) {
                children.computeIfAbsent(stack.get(index), Node::new).add(stack, index + 1);
            }
        }

        private int depth() {
            int maximum = 0;
            for (Node child : children.values()) {
                maximum = Math.max(maximum, child.depth());
            }
            return children.isEmpty() ? 0 : maximum + 1;
        }
    }
}

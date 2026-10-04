package dev.vriege.render3dfw.scene;

import dev.vriege.render3dfw.api.RenderCommand;
import dev.vriege.render3dfw.api.RenderStyle;
import dev.vriege.render3dfw.api.ShapeMode;
import dev.vriege.render3dfw.frame.CameraView;
import dev.vriege.render3dfw.frame.RenderSettings;
import dev.vriege.render3dfw.frame.VisibilityTest;
import dev.vriege.render3dfw.math.Bounds3d;
import dev.vriege.render3dfw.math.Vec3d;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SceneSnapshot {
    private static final int MAX_LEAF_SIZE = 64;
    private static final SceneSnapshot EMPTY = new SceneSnapshot(new RenderCommand[0]);

    private final RenderCommand[] commands;
    private final List<RenderCommand> commandView;
    private final double[] minimumX;
    private final double[] minimumY;
    private final double[] minimumZ;
    private final double[] maximumX;
    private final double[] maximumY;
    private final double[] maximumZ;
    private final double[] centerX;
    private final double[] centerY;
    private final double[] centerZ;
    private final int[] order;
    private final Node root;
    private final IndexStats indexStats;
    private final double minimumCommandDistance;
    private final RenderCommand uniformCommand;
    private final RenderStyle uniformStyle;
    private final ShapeMode uniformShapeMode;

    private SceneSnapshot(RenderCommand[] commands) {
        long startedAt = System.nanoTime();
        this.commands = commands;
        commandView = Collections.unmodifiableList(Arrays.asList(commands));
        minimumX = new double[commands.length];
        minimumY = new double[commands.length];
        minimumZ = new double[commands.length];
        maximumX = new double[commands.length];
        maximumY = new double[commands.length];
        maximumZ = new double[commands.length];
        centerX = new double[commands.length];
        centerY = new double[commands.length];
        centerZ = new double[commands.length];
        order = new int[commands.length];

        double minimumDistance = Double.POSITIVE_INFINITY;
        RenderCommand representative = null;
        boolean uniform = true;
        boolean styleUniform = true;
        boolean shapeModeUniform = true;
        for (int index = 0; index < commands.length; index++) {
            RenderCommand command = Objects.requireNonNull(commands[index], "command");
            if (representative == null) {
                representative = command;
            } else {
                uniform &= command.shape() == representative.shape()
                        && command.state().equals(representative.state());
                styleUniform &= command.style() == representative.style();
                shapeModeUniform &=
                        command.style().shapeMode() == representative.style().shapeMode();
            }
            Bounds3d bounds = command.shape().bounds();
            Vec3d translation = command.translation();
            minimumX[index] = bounds.minimum().x() + translation.x();
            minimumY[index] = bounds.minimum().y() + translation.y();
            minimumZ[index] = bounds.minimum().z() + translation.z();
            maximumX[index] = bounds.maximum().x() + translation.x();
            maximumY[index] = bounds.maximum().y() + translation.y();
            maximumZ[index] = bounds.maximum().z() + translation.z();
            centerX[index] = (minimumX[index] + maximumX[index]) * 0.5;
            centerY[index] = (minimumY[index] + maximumY[index]) * 0.5;
            centerZ[index] = (minimumZ[index] + maximumZ[index]) * 0.5;
            minimumDistance = Math.min(minimumDistance, command.maxDistance());
            order[index] = index;
        }
        minimumCommandDistance = minimumDistance;
        uniformCommand = uniform && representative != null ? representative : null;
        uniformStyle = styleUniform && representative != null ? representative.style() : null;
        uniformShapeMode = shapeModeUniform && representative != null
                ? representative.style().shapeMode()
                : null;

        IndexBuilder builder = new IndexBuilder();
        root = commands.length == 0 ? null : builder.build(0, commands.length);
        indexStats = new IndexStats(commands.length, builder.nodes, builder.leaves, System.nanoTime() - startedAt);
    }

    public static SceneSnapshot empty() {
        return EMPTY;
    }

    public static SceneSnapshot of(Collection<RenderCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        if (commands.isEmpty()) {
            return EMPTY;
        }
        return new SceneSnapshot(commands.toArray(RenderCommand[]::new));
    }

    public int size() {
        return commands.length;
    }

    public List<RenderCommand> commands() {
        return commandView;
    }

    public IndexStats indexStats() {
        return indexStats;
    }

    public RenderCommand uniformCommand() {
        return uniformCommand;
    }

    public RenderStyle uniformStyle() {
        return uniformStyle;
    }

    public ShapeMode uniformShapeMode() {
        return uniformShapeMode;
    }

    public QueryStats collectVisible(
            CameraView camera, RenderSettings settings, VisibleCommandConsumer visibleCommands) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(visibleCommands, "visibleCommands");
        MutableQueryStats stats = new MutableQueryStats();
        if (root != null) {
            if (isFullyVisible(camera, settings)) {
                collectAll(camera, visibleCommands, stats);
            } else {
                collect(root, camera, settings, visibleCommands, stats, false, false);
            }
        }
        return new QueryStats(
                commands.length,
                stats.testedCommands,
                stats.spatiallyPrunedCommands,
                stats.detailCulledCommands,
                stats.distanceCulledCommands,
                stats.frustumCulledCommands,
                stats.visibleCommands,
                stats.testedNodes);
    }

    public QueryStats collectVisibleCommands(
            CameraView camera, RenderSettings settings, VisibleCommandOnlyConsumer visibleCommands) {
        Objects.requireNonNull(visibleCommands, "visibleCommands");
        return collectVisible(camera, settings, new VisibleCommandConsumer() {
            @Override
            public void accept(RenderCommand command, double distanceSquared) {
                visibleCommands.accept(command);
            }

            @Override
            public boolean requiresDistance() {
                return false;
            }
        });
    }

    public boolean isFullyVisible(CameraView camera, RenderSettings settings) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(settings, "settings");
        if (root == null) {
            return true;
        }
        return camera.visibility()
                                .classify(
                                        root.minimumX,
                                        root.minimumY,
                                        root.minimumZ,
                                        root.maximumX,
                                        root.maximumY,
                                        root.maximumZ)
                        == VisibilityTest.Classification.INSIDE
                && settings.enabledDetails().bits() == -1L
                && minimumCommandDistance >= settings.maxDistance()
                && maximumDistanceSquaredToBounds(
                                camera.position().x(),
                                camera.position().y(),
                                camera.position().z(),
                                root)
                        <= square(settings.maxDistance());
    }

    private void collectAll(CameraView camera, VisibleCommandConsumer visibleCommands, MutableQueryStats stats) {
        double cameraX = camera.position().x();
        double cameraY = camera.position().y();
        double cameraZ = camera.position().z();
        stats.testedNodes = 1;
        stats.testedCommands = commands.length;
        stats.visibleCommands = commands.length;
        for (int index = 0; index < commands.length; index++) {
            double distanceSquared = 0.0;
            if (visibleCommands.requiresDistance()) {
                double deltaX = centerX[index] - cameraX;
                double deltaY = centerY[index] - cameraY;
                double deltaZ = centerZ[index] - cameraZ;
                distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
            }
            visibleCommands.accept(commands[index], distanceSquared);
        }
    }

    private void collect(
            Node node,
            CameraView camera,
            RenderSettings settings,
            VisibleCommandConsumer visibleCommands,
            MutableQueryStats stats,
            boolean frustumInside,
            boolean distanceInside) {
        stats.testedNodes++;
        double cameraX = camera.position().x();
        double cameraY = camera.position().y();
        double cameraZ = camera.position().z();
        if (!distanceInside
                && distanceSquaredToBounds(cameraX, cameraY, cameraZ, node) > square(settings.maxDistance())) {
            stats.distanceCulledCommands += node.itemCount;
            stats.spatiallyPrunedCommands += node.itemCount;
            return;
        }
        boolean childrenDistanceInside = distanceInside
                || minimumCommandDistance >= settings.maxDistance()
                        && maximumDistanceSquaredToBounds(cameraX, cameraY, cameraZ, node)
                                <= square(settings.maxDistance());
        boolean childrenInside = frustumInside;
        if (!frustumInside) {
            VisibilityTest.Classification classification = camera.visibility()
                    .classify(node.minimumX, node.minimumY, node.minimumZ, node.maximumX, node.maximumY, node.maximumZ);
            if (classification == VisibilityTest.Classification.OUTSIDE) {
                stats.frustumCulledCommands += node.itemCount;
                stats.spatiallyPrunedCommands += node.itemCount;
                return;
            }
            childrenInside = classification == VisibilityTest.Classification.INSIDE;
        }
        if (!node.isLeaf()) {
            collect(node.left, camera, settings, visibleCommands, stats, childrenInside, childrenDistanceInside);
            collect(node.right, camera, settings, visibleCommands, stats, childrenInside, childrenDistanceInside);
            return;
        }

        boolean detailsInside = settings.enabledDetails().bits() == -1L;
        for (int orderedIndex = node.start; orderedIndex < node.end; orderedIndex++) {
            int index = order[orderedIndex];
            stats.testedCommands++;
            RenderCommand command = commands[index];
            if (!detailsInside && !command.details().visibleWithin(settings.enabledDetails())) {
                stats.detailCulledCommands++;
                continue;
            }
            double distanceSquared = 0.0;
            if (!childrenDistanceInside || visibleCommands.requiresDistance()) {
                double deltaX = centerX[index] - cameraX;
                double deltaY = centerY[index] - cameraY;
                double deltaZ = centerZ[index] - cameraZ;
                distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                if (!childrenDistanceInside) {
                    double maximumDistance = Math.min(command.maxDistance(), settings.maxDistance());
                    if (distanceSquared > square(maximumDistance)) {
                        stats.distanceCulledCommands++;
                        continue;
                    }
                }
            }
            stats.visibleCommands++;
            visibleCommands.accept(command, distanceSquared);
        }
    }

    private static double square(double value) {
        return value * value;
    }

    private static double distanceSquaredToBounds(double x, double y, double z, Node node) {
        double deltaX = axisDistance(x, node.minimumX, node.maximumX);
        double deltaY = axisDistance(y, node.minimumY, node.maximumY);
        double deltaZ = axisDistance(z, node.minimumZ, node.maximumZ);
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static double maximumDistanceSquaredToBounds(double x, double y, double z, Node node) {
        double deltaX = Math.max(Math.abs(x - node.minimumX), Math.abs(x - node.maximumX));
        double deltaY = Math.max(Math.abs(y - node.minimumY), Math.abs(y - node.maximumY));
        double deltaZ = Math.max(Math.abs(z - node.minimumZ), Math.abs(z - node.maximumZ));
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0;
    }

    @FunctionalInterface
    public interface VisibleCommandConsumer {
        void accept(RenderCommand command, double distanceSquared);

        default boolean requiresDistance() {
            return true;
        }
    }

    @FunctionalInterface
    public interface VisibleCommandOnlyConsumer {
        void accept(RenderCommand command);
    }

    public record IndexStats(int commands, int nodes, int leaves, long buildNanos) {}

    public record QueryStats(
            int commands,
            int testedCommands,
            int spatiallyPrunedCommands,
            int detailCulledCommands,
            int distanceCulledCommands,
            int frustumCulledCommands,
            int visibleCommands,
            int testedNodes) {}

    private final class IndexBuilder {
        private int nodes;
        private int leaves;

        private Node build(int start, int end) {
            nodes++;
            Node node = bounds(start, end);
            if (end - start <= MAX_LEAF_SIZE) {
                leaves++;
                return node;
            }
            int axis = widestCenterAxis(start, end);
            int middle = (start + end) >>> 1;
            select(start, end, middle, axis);
            node.left = build(start, middle);
            node.right = build(middle, end);
            return node;
        }

        private Node bounds(int start, int end) {
            int first = order[start];
            Node result = new Node(start, end, end - start);
            result.minimumX = minimumX[first];
            result.minimumY = minimumY[first];
            result.minimumZ = minimumZ[first];
            result.maximumX = maximumX[first];
            result.maximumY = maximumY[first];
            result.maximumZ = maximumZ[first];
            for (int orderedIndex = start + 1; orderedIndex < end; orderedIndex++) {
                int index = order[orderedIndex];
                result.minimumX = Math.min(result.minimumX, minimumX[index]);
                result.minimumY = Math.min(result.minimumY, minimumY[index]);
                result.minimumZ = Math.min(result.minimumZ, minimumZ[index]);
                result.maximumX = Math.max(result.maximumX, maximumX[index]);
                result.maximumY = Math.max(result.maximumY, maximumY[index]);
                result.maximumZ = Math.max(result.maximumZ, maximumZ[index]);
            }
            return result;
        }

        private int widestCenterAxis(int start, int end) {
            double minimumCenterX = Double.POSITIVE_INFINITY;
            double minimumCenterY = Double.POSITIVE_INFINITY;
            double minimumCenterZ = Double.POSITIVE_INFINITY;
            double maximumCenterX = Double.NEGATIVE_INFINITY;
            double maximumCenterY = Double.NEGATIVE_INFINITY;
            double maximumCenterZ = Double.NEGATIVE_INFINITY;
            for (int orderedIndex = start; orderedIndex < end; orderedIndex++) {
                int index = order[orderedIndex];
                double centerX = center(index, 0);
                double centerY = center(index, 1);
                double centerZ = center(index, 2);
                minimumCenterX = Math.min(minimumCenterX, centerX);
                minimumCenterY = Math.min(minimumCenterY, centerY);
                minimumCenterZ = Math.min(minimumCenterZ, centerZ);
                maximumCenterX = Math.max(maximumCenterX, centerX);
                maximumCenterY = Math.max(maximumCenterY, centerY);
                maximumCenterZ = Math.max(maximumCenterZ, centerZ);
            }
            double widthX = maximumCenterX - minimumCenterX;
            double widthY = maximumCenterY - minimumCenterY;
            double widthZ = maximumCenterZ - minimumCenterZ;
            if (widthX >= widthY && widthX >= widthZ) {
                return 0;
            }
            return widthY >= widthZ ? 1 : 2;
        }

        private void select(int start, int end, int selected, int axis) {
            int low = start;
            int high = end - 1;
            while (low < high) {
                double pivot = center(order[(low + high) >>> 1], axis);
                int left = low;
                int right = high;
                while (left <= right) {
                    while (center(order[left], axis) < pivot) {
                        left++;
                    }
                    while (center(order[right], axis) > pivot) {
                        right--;
                    }
                    if (left <= right) {
                        swap(left, right);
                        left++;
                        right--;
                    }
                }
                if (selected <= right) {
                    high = right;
                } else if (selected >= left) {
                    low = left;
                } else {
                    return;
                }
            }
        }

        private double center(int index, int axis) {
            return switch (axis) {
                case 0 -> centerX[index];
                case 1 -> centerY[index];
                default -> centerZ[index];
            };
        }

        private void swap(int first, int second) {
            int value = order[first];
            order[first] = order[second];
            order[second] = value;
        }
    }

    private static final class Node {
        private final int start;
        private final int end;
        private final int itemCount;
        private double minimumX;
        private double minimumY;
        private double minimumZ;
        private double maximumX;
        private double maximumY;
        private double maximumZ;
        private Node left;
        private Node right;

        private Node(int start, int end, int itemCount) {
            this.start = start;
            this.end = end;
            this.itemCount = itemCount;
        }

        private boolean isLeaf() {
            return left == null;
        }
    }

    private static final class MutableQueryStats {
        private int testedCommands;
        private int spatiallyPrunedCommands;
        private int detailCulledCommands;
        private int distanceCulledCommands;
        private int frustumCulledCommands;
        private int visibleCommands;
        private int testedNodes;
    }
}

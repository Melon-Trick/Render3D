package dev.melontrick.render3dfw.frame;

@FunctionalInterface
public interface VisibilityTest {
    VisibilityTest ALL = new VisibilityTest() {
        @Override
        public boolean isVisible(
                double minimumX, double minimumY, double minimumZ, double maximumX, double maximumY, double maximumZ) {
            return true;
        }

        @Override
        public Classification classify(
                double minimumX, double minimumY, double minimumZ, double maximumX, double maximumY, double maximumZ) {
            return Classification.INSIDE;
        }
    };

    boolean isVisible(
            double minimumX, double minimumY, double minimumZ, double maximumX, double maximumY, double maximumZ);

    default Classification classify(
            double minimumX, double minimumY, double minimumZ, double maximumX, double maximumY, double maximumZ) {
        return isVisible(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)
                ? Classification.INTERSECTING
                : Classification.OUTSIDE;
    }

    enum Classification {
        OUTSIDE,
        INTERSECTING,
        INSIDE
    }
}

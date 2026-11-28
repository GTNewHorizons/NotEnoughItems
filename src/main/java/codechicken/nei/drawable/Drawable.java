package codechicken.nei.drawable;

public interface Drawable {

    @FunctionalInterface
    public interface Painter {

        public void draw(int x, int y, int width, int height);
    }

    public int getWidth();

    public int getHeight();

    public void draw(int x, int y, int width, int height);

    default void draw(int x, int y) {
        draw(x, y, getWidth(), getHeight());
    }

    public static Drawable of(int width, int height, Painter painter) {
        return new Drawable() {

            @Override
            public int getWidth() {
                return width;
            }

            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public void draw(int x, int y, int w, int h) {
                painter.draw(x, y, w, h);
            }
        };
    }

    // every layer is drawn into the whole rectangle, the first one at the bottom
    public static Drawable stack(Drawable... layers) {
        int width = 0;
        int height = 0;

        for (Drawable layer : layers) {
            width = Math.max(width, layer.getWidth());
            height = Math.max(height, layer.getHeight());
        }

        return of(width, height, (x, y, w, h) -> {
            for (Drawable layer : layers) {
                layer.draw(x, y, w, h);
            }
        });
    }
}

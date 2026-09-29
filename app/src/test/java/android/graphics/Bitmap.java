package android.graphics;

/**
 * A pixel-less stand-in for the framework's Bitmap, for JVM unit tests only. The unit-test
 * android.jar returns null from Bitmap.createBitmap, which makes every painted texture (TexPaint)
 * fail, so nothing that builds a cabinet's model could be tested. With this, painting does
 * nothing and reads back zeros, but the models built around the textures are real: their polygon
 * counts, bounds and normals can be checked. Shadows the framework class; only what the app's
 * texture painter and renderer touch is here.
 */
public class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_8888 }

    public enum CompressFormat { JPEG, PNG, WEBP }

    private final int width;
    private final int height;

    private Bitmap(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(width, height);
    }

    public static Bitmap createBitmap(int[] colors, int width, int height, Config config) {
        return new Bitmap(width, height);
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public void eraseColor(int color) {}

    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {}

    public void setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {}

    public Bitmap extractAlpha(Paint paint, int[] offsetXY) {
        return new Bitmap(width, height);
    }

    public void recycle() {}

    public boolean compress(CompressFormat format, int quality, java.io.OutputStream stream) { return true; }
}

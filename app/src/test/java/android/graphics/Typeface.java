package android.graphics;

/** A stand-in for the framework's Typeface in JVM unit tests (see the Bitmap next to it). */
public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    public static Typeface create(String familyName, int style) {
        return new Typeface();
    }
}

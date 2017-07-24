package bi.two;

import java.awt.*;

public class Colors {
    public final static Color LIGHT_GREEN = new Color(168, 255, 168);
    public static final Color LIME = new Color(0, 255, 0);

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}

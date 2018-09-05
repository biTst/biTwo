package bi.two;

import java.awt.*;

public class Colors {
    public static final Color LIGHT_GREEN = new Color(168, 255, 168);
    public static final Color LIME = new Color(0, 255, 0);
    public static final Color DARK_GREEN = new Color(34, 142, 2);
    public static final Color DARK_RED = new Color(125, 0, 0);
    public static final Color LIGHT_BLUE = new Color(171, 162, 255);

    public static final Color ROSE = new Color(255, 2, 155);

    public static final Color BALERINA = new Color(255, 201, 214);
    public static final Color DUSTY_ROSE = new Color(255, 201, 214);
    public static final Color CANDY_PINK = new Color(255, 92, 142);
    public static final Color DEEP_RED_PEARL = new Color(214, 19, 75);
    public static final Color RED = new Color(198, 0, 46);
    public static final Color RED_HOT_RED = new Color(255, 0, 36);
    public static final Color SWEET_POTATO = new Color(255, 101, 0);
    public static final Color JUST_ORANGE = new Color(255, 136, 0);
    public static final Color YELLOW = new Color(255, 217, 0);
    public static final Color LEMONADE = new Color(255, 255, 151);
    public static final Color BEIGE = new Color(251, 215, 188);
    public static final Color CLOW_IN_THE_DARK = new Color(235, 240, 217);
    public static final Color PEARL = new Color(255, 249, 241);
    public static final Color GRANNY_SMITH = new Color(94, 193, 78);
    public static final Color EMERALD = new Color(0, 164, 102);
    public static final Color TURQUOISE = new Color(0, 142, 170);
    public static final Color TRANQUILITY = new Color(81, 181, 194);
    public static final Color SKY_BLUE = new Color(178, 221, 239);
    public static final Color LIGHT_BLUE_PEARL = new Color(105, 207, 236);
    public static final Color BLUE_PEARL = new Color(0, 62, 107);
    public static final Color GENTLE_PLUM = new Color(96, 109, 151);
    public static final Color SPRING_LILAC = new Color(185, 178, 216);
    public static final Color PURPLE = new Color(125, 8, 122);
    public static final Color PLUM = new Color(109, 37, 68);
    public static final Color VIOLET = new Color(185, 35, 161);
    public static final Color FUSCHIA_PEARL = new Color(209, 51, 144);
    public static final Color HOT_PINK = new Color(255, 0, 106);
    public static final Color SILVVER = new Color(184, 189, 194);
    public static final Color ELEPHANT_GRAY = new Color(213, 203, 184);
    public static final Color PEWTER = new Color(176, 166, 155);
    public static final Color JEWERLY_GOLD = new Color(255, 216, 155);
    public static final Color GOLD = new Color(235, 154, 36);
    public static final Color TAN = new Color(233, 174, 133);
    public static final Color BURIED_TREASURE = new Color(206, 170, 118);
    public static final Color HAZELNUT = new Color(134, 82, 59);
    public static final Color SUEDE_BROWN = new Color(79, 59, 0);
    public static final Color CHOCOLATE = new Color(101, 10, 0);
    public static final Color CAMOUFLAGE = new Color(132, 121, 63);
    public static final Color LEAF_GREEN = new Color(0, 73, 33);
    public static final Color STRING_BEAN = new Color(89, 145, 76);


//    public static final Color D = Color.decode("#FFCCEE");
//    Color aColor = new Color(0xFF0096); // Use the hex number syntax

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, alpha));
    }
}

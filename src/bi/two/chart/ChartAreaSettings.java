package bi.two.chart;


import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChartAreaSettings {

    private final String m_name;
    private final float m_left;
    private final float m_top;
    private final float m_width;
    private final float m_height;
    private final Color m_color;
    private final List<ChartAreaLayerSettings> m_layers = new ArrayList<ChartAreaLayerSettings>();

    public ChartAreaSettings(String name, float left, float top, float width, float height, Color color) {
        m_name = name;
        m_left = left;
        m_top = top;
        m_width = width;
        m_height = height;
        m_color = color;
    }

    @Override public String toString() {
        return "ChartAreaSettings[name=" + m_name + "]";
    }

    public String getName() { return m_name; }
    public float getLeft() { return m_left; }
    public float getWidth() {
        return m_width;
    }
    public float getTop() {
        return m_top;
    }
    public float getHeight() {
        return m_height;
    }
    public Color getColor() { return m_color; }
    public List<ChartAreaLayerSettings> getLayers() { return m_layers; }

}

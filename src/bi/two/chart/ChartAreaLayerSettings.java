package bi.two.chart;

import java.awt.*;

//-----------------------------------------------------------------
public class ChartAreaLayerSettings {
    private final String m_name;
    private final Color m_color;
    private final TickPainter m_tickPainter;

    public ChartAreaLayerSettings(String name, Color color, TickPainter tickPainter) {
        m_tickPainter = tickPainter;
        m_name = name;
        m_color = color;
    }

    public String getName() {
        return m_name;
    }
    public Color getColor() {
        return m_color;
    }
    public TickPainter getTickPainter() { return m_tickPainter; }

    @Override public String toString() {
        return "ChartAreaLayerSettings[name=" + m_name + "]";
    }

}

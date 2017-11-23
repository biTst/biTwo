package bi.two.chart;

import java.awt.Color;

//-----------------------------------------------------------------
public class ChartAreaLayerSettings {
    private final String m_name;
    private final Color m_color;
    private ChartAreaPainter m_chartAreaPainter;

    public ChartAreaLayerSettings(String name, Color color, TickPainter tickPainter) {
        this(name, color, new ChartAreaPainter.TicksChartAreaPainter(tickPainter));
    }

    public ChartAreaLayerSettings(String name, Color color, ChartAreaPainter chartAreaPainter) {
        m_name = name;
        m_color = color;
        m_chartAreaPainter = chartAreaPainter;
    }

    public String getName() {
        return m_name;
    }
    public Color getColor() {
        return m_color;
    }
    public ChartAreaPainter getChartAreaPainter() { return m_chartAreaPainter; }

    @Override public String toString() {
        return "ChartAreaLayerSettings[name=" + m_name + "]";
    }

}

package bi.two.chart;

import java.awt.*;

//-----------------------------------------------------------------
public class ChartAreaLayerSettings {
    private final String m_name;
    private final Color m_color;
    private final boolean m_adjustPriceAxe;
    private ChartAreaPainter m_chartAreaPainter;

    public ChartAreaLayerSettings(String name, Color color, TickPainter tickPainter) {
        this(name, color, tickPainter, true);
    }

    public ChartAreaLayerSettings(String name, Color color, TickPainter tickPainter, boolean adjustPriceAxe) {
        this(name, color, new ChartAreaPainter.TicksChartAreaPainter(tickPainter), adjustPriceAxe);
    }

    public ChartAreaLayerSettings(String name, Color color, ChartAreaPainter chartAreaPainter, boolean adjustPriceAxe) {
        m_name = name;
        m_color = color;
        m_chartAreaPainter = chartAreaPainter;
        m_adjustPriceAxe = adjustPriceAxe;
    }

    public String getName() { return m_name; }
    public Color getColor() { return m_color; }
    public boolean isAdjustPriceAxe() { return m_adjustPriceAxe; }

    public ChartAreaPainter getChartAreaPainter() { return m_chartAreaPainter; }

    @Override public String toString() {
        return "ChartAreaLayerSettings[name=" + m_name + "]";
    }

}

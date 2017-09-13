package bi.two.chart;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChartSetting {
    private final List<ChartAreaSettings> m_cas = new ArrayList<ChartAreaSettings>();

    public ChartSetting() {
    }

    public List<ChartAreaSettings> getChartAreasSettings() { return m_cas; }

    public int getTimeAxeHeight() {
        return 15;
    }

    public ChartAreaSettings addChartAreaSettings(String name, float left, float top, float width, float height, Color color) {
        ChartAreaSettings cas = new ChartAreaSettings(name, left, top, width, height, color);
        m_cas.add(cas);
        return cas;
    }
}

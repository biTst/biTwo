package bi.two.chart;

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

    public void addChartAreaSettings(ChartAreaSettings cas) {
        m_cas.add(cas);
    }
}

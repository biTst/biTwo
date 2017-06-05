package bi.two.chart;

import java.util.HashMap;
import java.util.Map;

public class ChartData {
    private Map<String, ChartAreaData> m_chartAreaDatas = new HashMap<String, ChartAreaData>();

    public ChartAreaData getChartAreaData(String name) {
        return m_chartAreaDatas.get(name);
    }

    public void addChartAreaData(ChartAreaData priceData) {
        String name = priceData.getName();
        m_chartAreaDatas.put(name, priceData);
    }

    public void setTicksData(String name, ITicksData ticksData) {
        getChartAreaData(name).setTicksData(ticksData);
    }
}

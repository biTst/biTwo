package bi.two.chart;

import java.util.List;

public interface ITicksData extends ITimesSeriesData {
    List<? extends ITickData> getTicks();
}

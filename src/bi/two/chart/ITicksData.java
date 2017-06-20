package bi.two.chart;

import java.util.List;

public interface ITicksData<T extends ITickData>
        extends ITimesSeriesData<T> {
    List<T> getTicks();
}

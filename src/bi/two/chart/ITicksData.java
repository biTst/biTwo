package bi.two.chart;

import bi.two.ts.ITimesSeriesData;

import java.util.List;

public interface ITicksData<T extends ITickData>
        extends ITimesSeriesData<T> {
    List<T> getTicks();
}

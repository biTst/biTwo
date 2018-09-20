package bi.two.ts;

import bi.two.chart.ITickData;

public interface ITimesSeriesData<T extends ITickData> {
    ITimesSeriesData<T> getParent();
    T getLatestTick();
    void addListener(ITimesSeriesListener<T> listener);
    void removeListener(ITimesSeriesListener<T> listener);
    ITimesSeriesData<T> getActive(); // this or inner enabled
}

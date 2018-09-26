package bi.two.ts;

import bi.two.chart.ITickData;

//---------------------------------------------------------------------------------
public interface ITimesSeriesListener<T extends ITickData> {
    void onChanged(ITimesSeriesData<T> ts, boolean changed);
    void waitWhenAllFinish();
    // no more ticks - call from parent
    void notifyNoMoreTicks();
}

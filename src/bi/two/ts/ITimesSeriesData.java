package bi.two.ts;

import bi.two.chart.ITickData;

public interface ITimesSeriesData<T extends ITickData> {
    ITimesSeriesData getParent();
    T getLatestTick();
    void addListener(ITimesSeriesListener listener);
    void removeListener(ITimesSeriesListener listener);
    ITimesSeriesData getActive(); // this or inner enabled


    //---------------------------------------------------------------------------------
    interface ITimesSeriesListener {
        void onChanged(ITimesSeriesData ts, boolean changed);
        void waitWhenFinished();
        // no more ticks - call from parent
        void notifyNoMoreTicks();
    }
}

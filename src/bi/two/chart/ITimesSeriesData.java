package bi.two.chart;

public interface ITimesSeriesData<T extends ITickData> {
    ITimesSeriesData getParent();
    T getLatestTick();
    void addListener(ITimesSeriesListener listener);
    void removeListener(ITimesSeriesListener listener);
    ITimesSeriesData getActive(); // this or inner enabled

    interface ITimesSeriesListener {
        void onChanged(ITimesSeriesData ts, boolean changed);
        void waitWhenFinished();
    }
}

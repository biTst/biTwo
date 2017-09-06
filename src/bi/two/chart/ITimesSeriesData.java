package bi.two.chart;

public interface ITimesSeriesData<T extends ITickData> {
    T getLatestTick();
    void addListener(ITimesSeriesListener listener);
    void removeListener(ITimesSeriesListener listener);

    interface ITimesSeriesListener {
        void onChanged(ITimesSeriesData ts, boolean changed);
    }
}

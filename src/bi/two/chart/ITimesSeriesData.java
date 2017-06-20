package bi.two.chart;

public interface ITimesSeriesData<T extends ITickData> {
    T getLastTick();
    void addListener(ITimesSeriesListener listener);

    interface ITimesSeriesListener {
        void onChanged(ITimesSeriesData ts, boolean changed);
    }
}

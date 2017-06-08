package bi.two.chart;

public interface ITimesSeriesData {
    void addListener(ITimesSeriesListener listener);

    interface ITimesSeriesListener {
        void onChanged(ITimesSeriesData ts, boolean changed);
    }
}

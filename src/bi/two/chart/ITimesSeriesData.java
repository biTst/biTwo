package bi.two.chart;

public interface ITimesSeriesData extends ITicksData {
    void addListener(ITimesSeriesListener listener);

    interface ITimesSeriesListener {
        void onChanged();
    }
}

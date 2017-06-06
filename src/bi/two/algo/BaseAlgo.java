package bi.two.algo;

import bi.two.chart.BaseTimesSeriesData;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;

import java.util.List;

public class BaseAlgo extends BaseTimesSeriesData<TickData> {
    public boolean m_joinNonChangedValues = false;

    public void setJoinNonChangedValues(boolean joinNonChangedValues) { m_joinNonChangedValues = joinNonChangedValues; }

    // override
    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
    public TickData getTickAdjusted() { return null; }

    public TimesSeriesData<TickData> getTS() {
        final TimesSeriesData<TickData> timesSeriesData = new TimesSeriesData<TickData>();
        addListener(new ITimesSeriesListener() {
            @Override public void onChanged(ITimesSeriesData ts) {
                TickData value = getTickAdjusted();
                if (value != null) {
                    if (m_joinNonChangedValues) {
                        List<TickData> ticks = timesSeriesData.getTicks();
                        if(!ticks.isEmpty()) {
                            TickData latestAddedTick = ticks.get(0); // latest
                            float latestAddedPrice = latestAddedTick.getPrice();
                            float nowPrice = value.getPrice();
                            if(latestAddedPrice == nowPrice) {
                                latestAddedTick.init(value); // just update latest added tick
                                return;
                            }
                        }
                    }
                    TickData tickData = new TickData(value); // close
                    timesSeriesData.addNewestTick(tickData);
                }
            }
        });
        return timesSeriesData;
    }
}

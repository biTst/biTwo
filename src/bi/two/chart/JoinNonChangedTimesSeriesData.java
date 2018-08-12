package bi.two.chart;

import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;

import java.util.List;

public class JoinNonChangedTimesSeriesData extends TicksTimesSeriesData<TickData> {

    // to override
    protected ITickData getTickValue() {
        return m_parent.getLatestTick();
    }

    public JoinNonChangedTimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            ITickData value = getTickValue();
            if (value != null) {
                List<TickData> ticks = getTicks();
                int size = ticks.size();
                if (size > 0) {
                    TickData newestTick = ticks.get(0); // newest
                    float newestTickPrice = newestTick.getClosePrice();
                    float nowPrice = value.getClosePrice();
                    if (newestTickPrice == nowPrice) {
                        TickData secondNewestTick = (size > 1) ? ticks.get(1) : null;
                        float secondNewestTickPrice = (secondNewestTick == null) ? Float.NEGATIVE_INFINITY: secondNewestTick.getClosePrice();
                        if (secondNewestTickPrice == nowPrice) {
                            newestTick.init(value); // just update newest added tick
                            notifyListeners(false);
                            return;
                        }
                    }
                }

                TickData tickData = new TickData(value); // close
                addNewestTick(tickData);
                return;
            }
        }
        notifyListeners(false);
    }
}

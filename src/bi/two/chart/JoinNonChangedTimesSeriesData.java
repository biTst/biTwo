package bi.two.chart;

import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;

public class JoinNonChangedTimesSeriesData extends TicksTimesSeriesData<TickData> {

    private final boolean m_horizontal;

    // to override
    protected ITickData getTickValue() {
        return m_parent.getLatestTick();
    }

    public JoinNonChangedTimesSeriesData(ITimesSeriesData parent) {
        this(parent, true);
    }
    public JoinNonChangedTimesSeriesData(ITimesSeriesData parent, boolean horizontal) {
        super(parent);
        m_horizontal = horizontal;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            ITickData value = getTickValue();
            if (value != null) {
                int size = getTicksNum();
                if (size > 0) {
                    TickData newestTick = getTick(0); // newest
                    float newestTickPrice = newestTick.getClosePrice();
                    float nowPrice = value.getClosePrice();
                    if (newestTickPrice == nowPrice) { // same value tick as prev
                        if (m_horizontal) { // will paint with horizontal lines
                            TickData secondNewestTick = (size > 1) ? getTick(1) : null;
                            float secondNewestTickPrice = (secondNewestTick == null) ? Float.NEGATIVE_INFINITY : secondNewestTick.getClosePrice();
                            if (secondNewestTickPrice == nowPrice) {
                                newestTick.init(value); // just update newest added tick
                                notifyListeners(false);
                            }
                        } else {
                            return; // just ignore same value tick
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

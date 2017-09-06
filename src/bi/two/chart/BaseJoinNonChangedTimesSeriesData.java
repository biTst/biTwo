package bi.two.chart;

import java.util.List;

public abstract class BaseJoinNonChangedTimesSeriesData extends TimesSeriesData<TickData> {

    // to override
    protected abstract ITickData getTickValue();

    public BaseJoinNonChangedTimesSeriesData(ITimesSeriesData parent) {
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

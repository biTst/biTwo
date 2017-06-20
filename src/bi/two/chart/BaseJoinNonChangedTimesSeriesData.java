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
                if (!ticks.isEmpty()) {
                    TickData newestAddedTick = ticks.get(0); // newest
                    float newestAddedPrice = newestAddedTick.getPrice();
                    float nowPrice = value.getPrice();
                    if (newestAddedPrice == nowPrice) {
                        newestAddedTick.init(value); // just update newest added tick
                        notifyListeners(false);
                        return;
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

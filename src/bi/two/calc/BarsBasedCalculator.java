package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;

import java.util.List;

//----------------------------------------------------------
public abstract class BarsBasedCalculator extends TimesSeriesData<TickData> {

    private final BarSplitter m_barSplitter;
    private final BarTicksProcessor m_processor;

    protected abstract BarTicksProcessor createBarTicksProcessor();

    public BarsBasedCalculator(final BarSplitter barSplitter) {
        super(barSplitter);
        m_barSplitter = barSplitter;
        m_processor = createBarTicksProcessor();
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            boolean anyNotified = false;
            int counter = 0;
            for (final BarSplitter.BarHolder barHolder : m_barSplitter.getBars()) { // iterate all bars
                m_processor.restart(barHolder, counter);
                Boolean notified = barHolder.iterateTicks(m_processor);
                anyNotified |= notified;
                counter++;
            }
            if (!anyNotified) {
                notifyListeners(true);
            }
        } else {
            notifyListeners(changed);
        }
    }

    //----------------------------------------------------------
    protected abstract class BarTicksProcessor implements BarSplitter.BarHolder.ITicksProcessor<Boolean> {
        private BarSplitter.BarHolder m_barHolder;
        private int m_barIndex;

        protected abstract float calcValue();

        protected BarTicksProcessor() {
        }

        void restart(BarSplitter.BarHolder barHolder, int barIndex) {
            m_barHolder = barHolder;
            m_barIndex = barIndex;
        }

        @Override public Boolean done() {
            boolean notified;
            long time = m_barHolder.getTime();
            float value = calcValue();
            List<TickData> ticks = getTicks(); // my ticks
            if (m_barIndex < ticks.size()) { // if known bar changed - update my tick
                TickData tickData = ticks.get(m_barIndex);
                tickData.init(time, value);
                notified = false;
            } else { // bars number is more than my ticks - add tick
                TickData tickData = new TickData(time, value) {
                    @Override public long getBarSize() {
                        return m_barHolder.getBarSize();
                    }
                };
                addOlderTick(tickData); // will notify listeners inside
                notified = true;
            }
            return notified;
        }
    }
}

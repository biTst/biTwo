package bi.two.algo;

import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;
import bi.two.exch.Exchange;

public class Watcher extends TimesSeriesData<TickData> {
    private final BaseAlgo m_algo;
    private final Exchange m_exch;

    public Watcher(BaseAlgo algo, Exchange exch) {
        m_algo = algo;
        m_exch = exch;
    }
}

package bi.two;

import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.chart.ITickData;
import bi.two.exch.ExchPairData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.io.IOException;

public class Main2 extends Thread {
    private static final String CONFIG = "cfg\\main2.properties";

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(final String[] args) {
        Log.s_impl = new Log.StdLog();
        MarketConfig.initMarkets(false);

        new Main2().start();
    }

    private Main2() {
        super("MAIN");
        setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
    }

    @Override public void run() {
        try {
            MapConfig config = new MapConfig();
//            config.loadAndEncrypted(CONFIG);
            config.load(CONFIG);

            String exchangeName = config.getString("exchange");
            Exchange exchange = Exchange.get(exchangeName);
            String pairName = config.getString("pair");
            Pair pair = Pair.getByName(pairName);
            ExchPairData pairData = exchange.getPairData(pair);

            String algoName = config.getPropertyNoComment(BaseAlgo.ALGO_NAME_KEY);
            if (algoName == null) {
                throw new RuntimeException("no '" + BaseAlgo.ALGO_NAME_KEY + "' param");
            }
            Algo algo = Algo.valueOf(algoName);
            BaseTimesSeriesData tsd = new ExchangeTradesTimesSeriesData(exchange, pair);
            BaseAlgo algoImpl = algo.createAlgo(config, tsd);
            long preload = algoImpl.getPreloadPeriod();

            exchange.subscribeTrades(pair);

        } catch (IOException e) {
            err("main error: " + e, e);
        }
    }


    //----------------------------------------------------------------------------
    private static class ExchangeTradesTimesSeriesData extends BaseTimesSeriesData {
        public ExchangeTradesTimesSeriesData(Exchange exchange, Pair pair) {

        }

        @Override public ITickData getLatestTick() {
            return null;
        }
    }
}

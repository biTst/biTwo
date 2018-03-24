package bi.two;

import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.chart.ITickData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.ConsoleReader;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.util.concurrent.TimeUnit;

public class Main2 extends Thread {
    private static final String CONFIG = "cfg\\main2.properties";

    private Exchange m_exchange;
    private Pair m_pair;

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
            m_exchange = Exchange.get(exchangeName);
            m_exchange.m_impl.init(config);

            String pairName = config.getString("pair");
            m_pair = Pair.getByName(pairName);
//            ExchPairData pairData = exchange.getPairData(pair);

            String algoName = config.getPropertyNoComment(BaseAlgo.ALGO_NAME_KEY);
            if (algoName == null) {
                throw new RuntimeException("no '" + BaseAlgo.ALGO_NAME_KEY + "' param");
            }
            Algo algo = Algo.valueOf(algoName);
            BaseTimesSeriesData tsd = new ExchangeTradesTimesSeriesData(m_exchange, m_pair);
            BaseAlgo algoImpl = algo.createAlgo(config, tsd);
            long preload = algoImpl.getPreloadPeriod();

            m_exchange.connect(new Exchange.IExchangeConnectListener() {
                @Override public void onConnected() { onExchangeConnected(); }
                @Override public void onDisconnected() { onExchangeDisconnected(); }
            });

            new IntConsoleReader().start();

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            console("done");

        } catch (Exception e) {
            err("main error: " + e, e);
        }
    }

    private void onExchangeConnected() {
        console("onExchangeConnected");

//        m_exchange.queryAccount(new Exchange.IAccountListener() {
//            @Override public void onUpdated() throws Exception {
//                onGotAccount();
//            }
//        });
        onGotAccount();

    }

    private void onGotAccount() {
        try {
            m_exchange.subscribeTrades(m_pair);
        } catch (Exception e) {
            err("subscribeTrades error: " + e, e);
        }
    }

    private void onExchangeDisconnected() {
        console("onExchangeDisconnected");
    }

    private boolean onConsoleLine(String line) {
        if (line.equals("t") || line.equals("top")) {
//            logTop();
        } else {
            log("not recognized command: " + line);
        }
        return false; // do not finish ConsoleReader
    }


    //----------------------------------------------------------------------------
    private static class ExchangeTradesTimesSeriesData extends BaseTimesSeriesData {
        public ExchangeTradesTimesSeriesData(Exchange exchange, Pair pair) {

        }

        @Override public ITickData getLatestTick() {
            return null;
        }
    }


    // -----------------------------------------------------------------------------------------------------------
    private class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }
}

package bi.two.main2;

import bi.two.ChartCanvas;
import bi.two.ChartFrame;
import bi.two.Colors;
import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.chart.*;
import bi.two.exch.*;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.ConsoleReader;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main2 extends Thread {
    private static final String CONFIG = "cfg\\main2.properties";

    private Exchange m_exchange;
    private Pair m_pair;
    private final ChartFrame m_frame;
    private BaseAlgo m_algoImpl;
    private TicksTimesSeriesData m_ticksTs;
    private MapConfig m_config;

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(final String[] args) {
        Log.s_impl = new Log.FileLog(); //StdLog();
        MarketConfig.initMarkets(false);

        new Main2().start();
    }

    private Main2() {
        super("MAIN");
        setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
        m_frame = new ChartFrame();
    }

    @Override public void run() {
        try {
            console("Main2 started");
            m_config = new MapConfig();
//            config.loadAndEncrypted(CONFIG);
            m_config.load(CONFIG);
            console("config loaded");

            boolean collectTicks = true;
            m_ticksTs = new TicksTimesSeriesData(collectTicks);
            if (!collectTicks) { // add initial tick to update
                m_ticksTs.addOlderTick(new TickData());
            }

            m_frame.setVisible(true);
            ChartCanvas chartCanvas = m_frame.getChartCanvas();
            setupChart(chartCanvas, m_ticksTs);

            String exchangeName = m_config.getString("exchange");
            m_exchange = Exchange.get(exchangeName);
            m_exchange.m_impl.init(m_config);

            String pairName = m_config.getString("pair");
            m_pair = Pair.getByName(pairName);
//            ExchPairData pairData = exchange.getPairData(pair);

            String algoName = m_config.getPropertyNoComment(BaseAlgo.ALGO_NAME_KEY);
            console("exchange " + exchangeName + "; pair=" + pairName + "; algo=" + algoName);
            if (algoName == null) {
                throw new RuntimeException("no '" + BaseAlgo.ALGO_NAME_KEY + "' param");
            }
            Algo algo = Algo.valueOf(algoName);
            m_algoImpl = algo.createAlgo(m_config, m_ticksTs);

            m_exchange.connect(new Exchange.IExchangeConnectListener() {
                @Override public void onConnected() {}
                @Override public void onAuthenticated() { onExchangeAuthenticated(); }
                @Override public void onDisconnected() { onExchangeDisconnected(); }
            });

            new IntConsoleReader().start();

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            console("done");

        } catch (Exception e) {
            err("main error: " + e, e);
        }
    }

    private void setupChart(ChartCanvas chartCanvas, TimesSeriesData<TickData> ticksTs) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            BaseAlgo.addChart(chartData, ticksTs,     topLayers, "price",     Colors.alpha(Color.RED, 70), TickPainter.TICK);
//            BaseAlgo.addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Color.RED, 70), TickPainter.BAR);
        }
    }

    private void onExchangeAuthenticated() {
        console("onExchangeAuthenticated");

        try {
            m_exchange.queryAccount(new Exchange.IAccountListener() {
                private int m_counter;

                @Override public void onAccountUpdated() {
                    try {
                        if(m_counter++ == 0) {
                            onFirstAccountUpdate();
                        }
                        Main2.this.onAccountUpdated();
                    } catch (Exception e) {
                        err("onAccountUpdated error: " + e, e);
                    }
                }
            });

console("TradesPreloader SKIPPED");
//            startPreloader();
        } catch (Exception e) {
            err("onExchangeConnected error: " + e, e);
        }
    }

    private void onFirstAccountUpdate() throws Exception {
        console("onFirstAccountUpdate");

        OrderBook orderBook = m_exchange.getOrderBook(m_pair);
        orderBook.subscribe(new OrderBook.IOrderBookListener() {
            @Override public void onOrderBookUpdated(OrderBook orderBook) {
                console("onOrderBookUpdated: orderBook=" + orderBook);
            }
        }, 10);
    }

    private void onAccountUpdated() throws Exception {
        console("onAccountUpdated");
    }

    private void startPreloader() throws Exception {
        long preloadPeriod = m_algoImpl.getPreloadPeriod();
        final TradesPreloader preloader = new TradesPreloader(m_exchange, m_pair, preloadPeriod, m_config, m_ticksTs) {
            @Override protected void onTicksPreloaded() {
                m_frame.repaint();
            }
            @Override protected void onLiveTick() {
                m_frame.repaint(100);
            }
        };

        m_exchange.subscribeTrades(m_pair, new ExchPairData.TradesData.ITradeListener() {
            @Override public void onTrade(TradeData td) {
                console("onTrade td=" + td);
                preloader.addNewestTick(td); // this will start preloader on first trade
            }
        });
    }

    private void onExchangeDisconnected() {
        console("onExchangeDisconnected");
        // todo: cleanup, stop preloader
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
//    private static class ExchangeTradesTimesSeriesData extends BaseTimesSeriesData {
//        public ExchangeTradesTimesSeriesData(Exchange exchange, Pair pair) {
//
//        }
//
//        @Override public ITickData getLatestTick() {
//            return null;
//        }
//    }


    // -----------------------------------------------------------------------------------------------------------
    private class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }
}

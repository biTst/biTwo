package bi.two.main2;

import bi.two.ChartCanvas;
import bi.two.ChartFrame;
import bi.two.Colors;
import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.chart.*;
import bi.two.exch.*;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.NoTicksTimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.ConsoleReader;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.*;

public class Main2 extends Thread {
    private static final String CONFIG = "cfg" + File.separator + "main2.properties";
    public static final String LOG_FILE_LOCATION = "logs" + File.separator + "main2.log";

//    private static final long PRELOAD_PERIOD = TimeUnit.MINUTES.toMillis(50);
//    private static final long PRELOAD_PERIOD = TimeUnit.HOURS.toMillis(24);
    private static final long PRELOAD_PERIOD = TimeUnit.DAYS.toMillis(120);

    private Exchange m_exchange;
    private Pair m_pair;
    private final ChartFrame m_frame;
    private BaseAlgo m_algoImpl;
    private BaseTicksTimesSeriesData<TickData> m_ticksTs;
    private MapConfig m_config;

    public static void main(final String[] args) {
        Log.s_impl = new Log.FileLog(LOG_FILE_LOCATION);
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
            m_ticksTs = collectTicks
                    ? new TicksTimesSeriesData<TickData>(null)
                    : new NoTicksTimesSeriesData<TickData>(null);
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
            m_algoImpl = algo.createAlgo(m_config, m_ticksTs, m_exchange);

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

    private void setupChart(ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.9f, Color.RED);
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
                        if (m_counter++ == 0) {
                            onFirstAccountUpdate();
                        }
                        Main2.this.onAccountUpdated();
                    } catch (Exception e) {
                        err("onAccountUpdated error: " + e, e);
                    }
                }
            });
        } catch (Exception e) {
            err("onExchangeConnected error: " + e, e);
        }
    }

    private void onFirstAccountUpdate() throws Exception {
        console("onFirstAccountUpdate");

        // cancel all orders first
        ExchPairData pairData = m_exchange.getPairData(m_pair);
        final LiveOrdersData liveOrders = pairData.getLiveOrders();
        Collection<OrderData> orders = liveOrders.m_orders.values();
        int ordersNum = orders.size();
        console(" pairData=" + pairData + "; liveOrders=" + liveOrders + "; ordersNum=" + ordersNum);

        if (true) { // do cancel orders
            if (ordersNum > 0) {

                // add live orders listener to continue only after canceling
                liveOrders.setOrdersListener(new Exchange.IOrdersListener() {
                    @Override public void onUpdated(Map<String, OrderData> orders) {
                        console("@@@@ live orders updated: " + orders);
                        liveOrders.setOrdersListener(null);
                        try {
                            onAllTradesCancelled();
                        } catch (Exception e) {
                            err("onAllTradesCancelled error: " + e, e);
                        }
                    }
                });

                OrderData[] array = orders.toArray(new OrderData[ordersNum]);
                // todo: for now cancel only first order
                OrderData orderData = array[0];
                console("  first order to cancel: orderData=" + orderData);
                m_exchange.cancelOrder(orderData);
            } else {
                console("  no orders to cancel");
                onAllTradesCancelled();
            }
        } else {
            onAllTradesCancelled();
        }

//        OrderBook orderBook = m_exchange.getOrderBook(m_pair);
//        orderBook.subscribe(new OrderBook.IOrderBookListener() {
//            @Override public void onOrderBookUpdated(OrderBook orderBook) {
//                console("onOrderBookUpdated: orderBook=" + orderBook);
//            }
//        }, 10);

    }

    private void onAllTradesCancelled() throws Exception {
        console("onAllTradesCancelled()");

//console("TradesPreloader SKIPPED");
        startPreloader();

        if (false) { // place test orders
            placeTestOrders();
        }
    }

    private void placeTestOrders() throws Exception {
        console("placeTestOrders()");

        TopQuote topQuote = m_exchange.getTopQuote(m_pair);
        topQuote.subscribe(new TopQuote.ITopQuoteListener() {
            boolean m_gotFirstQuote = false;
            private TopQuote m_topQuote;

            @Override public void onTopQuoteUpdated(TopQuote topQuote) {
                console("onTopQuoteUpdated: topQuote=" + topQuote);
                m_topQuote = topQuote;

                if (!m_gotFirstQuote) {
                    m_gotFirstQuote = true;
                    onFirstTopQuote();
                }
            }

            private void onFirstTopQuote() {
                new Thread() {
                    @Override public void run() {
                        try {
                            Thread.sleep(500);
                            String orderId = "oid" + System.currentTimeMillis();

//                                    Double price = m_topQuote.m_askPrice + 15;
//                                    OrderSide orderSide = OrderSide.SELL;
                            Double price = m_topQuote.m_bidPrice - 15;
                            OrderSide orderSide = OrderSide.BUY; // usd->btc

//                                    OrderType orderType = OrderType.LIMIT;
                            OrderType orderType = OrderType.MARKET;
                            price = 0d;

                            OrderData orderData = new OrderData(m_exchange, orderId, m_pair, orderSide, orderType, price, 0.05);
                            orderData.addOrderListener(new OrderData.IOrderListener() {
                                @Override public void onOrderUpdated(OrderData orderData) {
                                    console("onOrderUpdated: " + orderData);
                                }
                            });
                            console("submitOrder " + orderData);
                            m_exchange.submitOrder(orderData);
                        } catch (Exception e) {
                            err("submitOrder error: " + e, e);
                        }
                    }
                }.start();
            }
        });
    }

    private void onAccountUpdated() throws Exception {
        console("onAccountUpdated");
    }

    private void startPreloader() throws Exception {
//        long PRELOAD_PERIOD = m_algoImpl.getPreloadPeriod();
        long preloadPeriod = PRELOAD_PERIOD;

        console("preloadPeriod=" + Utils.millisToYDHMSStr(preloadPeriod));

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
                try {
                    m_exchange.unsubscribeTrades(m_pair);
                } catch (Exception e) {
                    err("unsubscribeTrades error: " + e, e);
                }
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

package bi.two.exch;

import bi.two.chart.ITickData;
import bi.two.main2.TicksCacheReader;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.util.List;

public class BaseExchImpl {
    protected static void console(String s) { Log.console(s); }
    protected static void log(String s) { Log.log(s); }
    protected static void err(String s, Throwable t) { Log.err(s, t); }

    public void init(MapConfig config) {
        throw new RuntimeException("not implemented: " + this);
    }

    public void connect(Exchange.IExchangeConnectListener iExchangeConnectListener) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void subscribeTopQuote(TopQuote topQuote) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void subscribeOrderBook(OrderBook orderBook, int depth) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void queryOrderBookSnapshot(OrderBook orderBook, int depth) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void queryAccount() throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void queryOrders(LiveOrdersData liveOrders) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void submitOrder(OrderData orderData) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void submitOrderReplace(String orderId, OrderData orderData) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void cancelOrder(OrderData orderData) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public void rateLimiterActive(boolean active) { /*noop by def*/ }

    public void subscribeTrades(ExchPairData.TradesData tradesData) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public TicksCacheReader getTicksCacheReader(MapConfig config) {
        throw new RuntimeException("not implemented: " + this);
    }

    public List<? extends ITickData> loadTrades(Pair pair, long timestamp, Direction direction, int tradesNum) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public int getMaxTradeHistoryLoadCount() {
        throw new RuntimeException("not implemented: " + this);
    }
}

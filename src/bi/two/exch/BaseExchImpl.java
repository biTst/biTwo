package bi.two.exch;

import bi.two.Main2;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.io.IOException;

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

    public void submitOrder(OrderData orderData) throws IOException {
        throw new RuntimeException("not implemented: " + this);
    }

    public void submitOrderReplace(String orderId, OrderData orderData) throws IOException {
        throw new RuntimeException("not implemented: " + this);
    }

    public void cancelOrder(OrderData orderData) throws IOException {
        throw new RuntimeException("not implemented: " + this);
    }

    public void rateLimiterActive(boolean active) { /*noop by def*/ }

    public void subscribeTrades(ExchPairData.TradesData tradesData) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }

    public Main2.TicksCacheReader getTicksCacheReader() {
        throw new RuntimeException("not implemented: " + this);
    }

    public void loadTrades(long timestamp) throws Exception {
        throw new RuntimeException("not implemented: " + this);
    }
}

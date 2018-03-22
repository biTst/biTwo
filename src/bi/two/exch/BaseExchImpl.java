package bi.two.exch;

import java.io.IOException;

public class BaseExchImpl {
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

    public void subscribeTrades(ExchPairData.TradesData tradesData) {
        throw new RuntimeException("not implemented: " + this);
    }
}

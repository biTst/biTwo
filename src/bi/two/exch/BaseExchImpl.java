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

    public void queryAccount() throws IOException, InterruptedException {
        throw new RuntimeException("not implemented: " + this);
    }
}

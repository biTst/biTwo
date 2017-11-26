package bi.two.exch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OrderBook {
    private final Exchange m_exchange;
    private final Pair m_pair;
    private List<OrderBookEntry> m_bids = new ArrayList<>();
    private List<OrderBookEntry> m_asks = new ArrayList<>();
    private Exchange.IOrderBookListener m_listener;

    public Pair getPair() { return m_pair; }

    public OrderBook(Exchange exchange, Pair pair) {
        m_exchange = exchange ;
        m_pair = pair;
    }

    public void subscribe(Exchange.IOrderBookListener listener, int depth) throws Exception {
        m_listener = listener;
        m_exchange.subscribeOrderBook(this, depth);
    }

    public void update(List<OrderBookEntry> bids, List<OrderBookEntry> asks) {
        updateBookSide(m_bids, bids, true);
        updateBookSide(m_asks, asks, false);
        m_listener.onUpdated();
    }

    private void updateBookSide(List<OrderBookEntry> entries, List<OrderBookEntry> updates, boolean reverse) {
        for (OrderBookEntry entry : updates) {
            int index = Collections.binarySearch(entries, entry, new Comparator<OrderBookEntry>() {
                @Override public int compare(OrderBookEntry o1, OrderBookEntry o2) {
                    int compare = Double.compare(o1.m_price, o1.m_price);
                    return reverse ? -compare : compare;
                }
            });
            if (index < 0) {
                // to insert
                index = -index - 1;
                m_bids.add(index, entry);
            } else {
                // to replace
                m_bids.set(index, entry);
            }
        }
    }

    
    //------------------------------------------------------------------------------
    public static class OrderBookEntry {
        private double m_price;
        private double m_size;

        public OrderBookEntry(double price, double size) {
            m_price = price;
            m_size = size;
        }
    }
}

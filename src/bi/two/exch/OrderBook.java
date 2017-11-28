package bi.two.exch;

import bi.two.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OrderBook {
    public final Exchange m_exchange;
    public final Pair m_pair;
    public List<OrderBookEntry> m_bids = new ArrayList<>();
    public List<OrderBookEntry> m_asks = new ArrayList<>();
    private Exchange.IOrderBookListener m_listener;

    public OrderBook(Exchange exchange, Pair pair) {
        m_exchange = exchange ;
        m_pair = pair;
    }

    public void subscribe(Exchange.IOrderBookListener listener, int depth) throws Exception {
        m_listener = listener;
        m_exchange.subscribeOrderBook(this, depth);
    }

    public void snapshot(Exchange.IOrderBookListener listener, int depth) throws Exception {
        m_listener = listener;
        m_exchange.queryOrderBookSnapshot(this, depth);
    }

    public void update(List<OrderBookEntry> bids, List<OrderBookEntry> asks) {
        updateBookSide(m_bids, bids, true);
        updateBookSide(m_asks, asks, false);
        m_listener.onUpdated();
    }

    private void updateBookSide(List<OrderBookEntry> entries, List<OrderBookEntry> updates, boolean reverse) {
//        System.out.println("  updateBookSide(" + reverse + ") updates=" + updates);
        for (OrderBookEntry update : updates) {
            //System.out.println("   entries=" + entries);
            int index = Collections.binarySearch(entries, update, new Comparator<OrderBookEntry>() {
                @Override public int compare(OrderBookEntry o1, OrderBookEntry o2) {
                    int compare = Double.compare(o1.m_price, o2.m_price);
                    return reverse ? -compare : compare;
                }
            });
            //System.out.println("    index=" + index + "; update=" + update);
            double size = update.m_size;
            if (index < 0) {
                // to insert
                if (size > 0) {
                    index = -index - 1;
                    entries.add(index, update);
                }
            } else {
                // to replace
                if (size > 0) {
                    entries.set(index, update);
                } else {
                    entries.remove(index);
                }
            }
        }
//        System.out.println("   out entries=" + entries);
    }

    public String toString(int levels) {
        StringBuilder sb = new StringBuilder("{");
        int min = Math.min(levels, m_bids.size());
        for (int i = min - 1; i >= 0; i--) {
            OrderBookEntry bid = m_bids.get(i);
            sb.append(bid);
            sb.append(";");
        }
        sb.append("  ||  ");
        min = Math.min(levels, m_asks.size());
        for (int i = 0; i < min; i++) {
            OrderBookEntry ask = m_asks.get(i);
            sb.append(ask);
            sb.append(";");
        }
        sb.append('}');
        return sb.toString();
    }

    @Override public String toString() {
        return "OrderBook{bids:" + m_bids + ", asks:" + m_asks + '}';
    }

    public Spread getTopSpread() {
        return new Spread(m_bids.get(0), m_asks.get(0));
    }


    //------------------------------------------------------------------------------
    public static class OrderBookEntry {
        public double m_price;
        public double m_size;

        public OrderBookEntry(double price, double size) {
            m_price = price;
            m_size = size;
        }

        @Override public String toString() {
            return "[" + m_price + ";" + Utils.format8(m_size) + ']';
        }
    }

    //------------------------------------------------------------------------------
    public static class Spread {
        public final OrderBookEntry m_bidEntry;
        public final OrderBookEntry m_askEntry;

        public Spread(OrderBookEntry bidEntry, OrderBookEntry askEntry) {
            m_bidEntry = bidEntry;
            m_askEntry = askEntry;
        }

        @Override public String toString() {
            return "Spread{" +
                    "bid=" + m_bidEntry +
                    ", ask=" + m_askEntry +
                    '}';
        }
    }
}

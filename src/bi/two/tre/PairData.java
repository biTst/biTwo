package bi.two.tre;

import bi.two.exch.OrderBook;
import bi.two.exch.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PairData implements OrderBook.IOrderBookListener {
    private static Map<Pair, PairData> s_map = new HashMap<>();

    public final Pair m_pair;
    public final List<OrderBook.IOrderBookListener> m_listeners = new ArrayList<>();
    public boolean m_liveOrderBook;

    public static PairData get(Pair pair) {
        PairData pairData = s_map.get(pair);
        if (pairData == null) {
            pairData = new PairData(pair);
        }
        return pairData;
    }

    public PairData(Pair pair) {
        m_pair = pair;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PairData pairData = (PairData) o;
        return m_pair.equals(pairData.m_pair);
    }

    @Override public int hashCode() {
        return m_pair.hashCode();
    }

    @Override public String toString() {
        return m_pair.toString();
    }

    public void addOrderBookListener(OrderBook.IOrderBookListener listener) {
        m_listeners.add(listener);
    }

    @Override public void onOrderBookUpdated(OrderBook orderBook) {
        m_liveOrderBook = true; // we got at least one order book update
        for (OrderBook.IOrderBookListener listener : m_listeners) {
            listener.onOrderBookUpdated(orderBook);
        }
    }
}

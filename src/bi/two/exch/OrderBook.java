package bi.two.exch;

import java.util.ArrayList;
import java.util.List;

public class OrderBook {
    private List<OrderBookEntry> m_bids = new ArrayList<>();
    private List<OrderBookEntry> m_asks = new ArrayList<>();

    private static class OrderBookEntry {
        private double m_price;
        private double m_size;
    }
}

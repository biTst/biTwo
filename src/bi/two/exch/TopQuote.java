package bi.two.exch;

public class TopQuote {

    public final Exchange m_exchange;
    public final Pair m_pair;

    public Double m_bidSize;
    public Double m_bidPrice;
    public Double m_askSize;
    public Double m_askPrice;

    private ITopQuoteListener m_listener;

    public TopQuote(Exchange exchange, Pair pair) {
        m_exchange = exchange ;
        m_pair = pair;
    }

    public void subscribe(ITopQuoteListener listener) throws Exception {
        m_listener = listener;
        m_exchange.subscribeTopQuote(this);
    }

    public void update(Number bidSize, Number bidPrice, Number askSize, Number askPrice) {
        m_bidSize = bidSize.doubleValue();
        m_bidPrice = bidPrice.doubleValue();
        m_askSize = askSize.doubleValue();
        m_askPrice = askPrice.doubleValue();

        if (m_listener != null) {
            m_listener.onTopQuoteUpdated(this);
        }
    }

    @Override public String toString() {
        return "TopQuote{" +
                "pair=" + m_pair +
                ", bidSize=" + m_bidSize +
                ", bidPrice=" + m_bidPrice +
                ", askSize=" + m_askSize +
                ", askPrice=" + m_askPrice +
                '}';
    }

    //----------------------------------------------------------------------------------------
    public interface ITopQuoteListener {
        void onTopQuoteUpdated(TopQuote topQuote);
    }
}

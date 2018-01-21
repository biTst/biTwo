package bi.two.exch;

import bi.two.tre.CurrencyValue;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class ExchPairData {
    private final Exchange m_exchange;
    public final Pair m_pair;
    public CurrencyValue m_minOrderToCreate = null;
    public CurrencyValue m_minOrderStep = null;
    public double m_minPriceStep = 0;
    public double m_commission = 0;
    public double m_makerCommission = 0;
    public double m_initBalance;
    public OrderBook m_orderBook;
    public LiveOrdersData m_liveOrders;
    public int m_priceStepDecimals;
    public DecimalFormat m_priceFormat;
    public int m_orderStepDecimals;
    public DecimalFormat m_sizeFormat;

    public ExchPairData(Exchange exchange, Pair pair) {
        m_exchange = exchange;
        m_pair = pair;
    }

    @Override public String toString() {
        return m_exchange.m_name + ":" + m_pair;
    }

    public CurrencyValue getMinOrderToCreate() {
        if (m_minOrderToCreate != null) {
            return m_minOrderToCreate;
        }
        throw new RuntimeException("no minOrderToCreate defined");
    }

    public OrderBook getOrderBook() {
        if (m_orderBook == null) { // lazy
            m_orderBook = new OrderBook(m_exchange, m_pair);
        }
        return m_orderBook;
    }

    public void onDisconnected() {
        if (m_orderBook != null) {
            m_orderBook.onDisconnected();
        }
    }

    public LiveOrdersData getLiveOrders() {
        if (m_liveOrders == null) { // lazy
            m_liveOrders = new LiveOrdersData(m_exchange, m_pair);
        }
        return m_liveOrders;
    }

    public void submitOrder(OrderData orderData) throws IOException {
        LiveOrdersData liveOrders = getLiveOrders();
        orderData.setFormats(m_priceFormat, m_sizeFormat);
        liveOrders.submitOrder(orderData);
    }

    public void setMinPriceStep(double minPriceStep) {
        m_minPriceStep = minPriceStep;
        double log10 = Math.log10(minPriceStep);
        m_priceStepDecimals = (int) -log10;
        m_priceFormat = newDecimalFormat(m_orderStepDecimals); // "price": "241.9477"
    }

    public void setMinOrderStep(CurrencyValue minOrderStep) {
        m_minOrderStep = minOrderStep;
        double value = m_minOrderStep.m_value;
        double log10 = Math.log10(value);
        m_orderStepDecimals = (int) -log10;
        m_sizeFormat = newDecimalFormat(m_orderStepDecimals); // "amount": "0.02000000"
    }

    private static DecimalFormat newDecimalFormat(int decimals) {
        DecimalFormat ret = new DecimalFormat();
        DecimalFormatSymbols decimalFormatSymbols = ret.getDecimalFormatSymbols();
        decimalFormatSymbols.setDecimalSeparator('.');
        ret.setDecimalFormatSymbols(decimalFormatSymbols);
        ret.setMaximumFractionDigits(decimals);
        ret.setGroupingUsed(false);
        return ret;
    }
}

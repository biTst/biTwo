package bi.two.chart;

import bi.two.exch.OrderSide;

public class TradeData extends TickVolumeData {
    public final OrderSide m_side;

    public TradeData(long timestamp, float price, float volume, OrderSide side) {
        super(timestamp, price, volume);
        m_side = side;
    }

    @Override public TickPainter getTickPainter() {
        return TickPainter.TRADE;
    }

}

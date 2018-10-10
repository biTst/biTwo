package bi.two.chart;

import bi.two.exch.OrderSide;

import java.awt.*;

public class TradeData extends TickVolumeData {
    public final OrderSide m_side;

    public TradeData(long timestamp, float price, float volume, OrderSide side) {
        super(timestamp, price, volume);
        m_side = side;
    }

    @Override public TickPainter getTickPainter() {
        return TickPainter.TRADE;
    }

    public void paintBubble(Graphics2D g2, int x, int y) {

    }

    //---------------------------------------------------------------
    public static class DebugTradeData extends TradeData {
        private final String m_debug;

        public DebugTradeData(long timestamp, float price, float volume, OrderSide side, String debug) {
            super(timestamp, price, volume, side);
            m_debug = debug;
        }

        public void paintBubble(Graphics2D g2, int x, int y) {
            g2.drawString(m_debug, x, y);
        }
    }
}

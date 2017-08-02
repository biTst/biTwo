package bi.two.chart;

public class ChartAreaPaintSetting {
    private final int m_paintLeft;
    private final int m_paintWidth;
    private final int m_paintTop;
    private final int m_paintHeight;
    private final Axe.AxeFloat m_yAxe = new Axe.AxeFloat();

    public ChartAreaPaintSetting(int paintLeft, int paintWidth, int paintTop, int paintHeight) {
        m_paintLeft = paintLeft;
        m_paintWidth = paintWidth;
        m_paintTop = paintTop;
        m_paintHeight = paintHeight;
    }

    public Axe getYAxe() { return m_yAxe; }
    public int getPaintLeft() { return m_paintLeft; }
    public int getPaintWidth() { return m_paintWidth; }
    public int getPaintTop() { return m_paintTop;     }
    public int getPaintHeight() { return m_paintHeight; }

    public void initYAxe(float minPrice, float maxPrice) {
        m_yAxe.init(minPrice, maxPrice, m_paintTop + m_paintHeight, m_paintTop);
    }
}

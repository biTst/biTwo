package bi.two.chart;


public class ChartPaintSetting {
    private int m_width;
    private int m_height;
    private final Axe.AxeLong m_xAxe = new Axe.AxeLong();
    private int m_priceAxeWidth;

    public ChartPaintSetting() {
    }

    public int getWidth() { return m_width; }
    public int getHeight() { return m_height; }
    public Axe.AxeLong getXAxe() { return m_xAxe; }
    public int getPriceAxeWidth() { return m_priceAxeWidth; }

    public void setPriceAxeWidth(int priceAxeWidth) { m_priceAxeWidth = priceAxeWidth; }

    public int getTimeAxeHeight() {
        return 15;
    }

    public void setBounds(int width, int height) {
        int widthDelta = width - m_width;
        m_width = width;
        m_height = height;
        shiftXAxe(widthDelta);
    }

    private void shiftXAxe(int widthDelta) {
        if (m_xAxe.isInitialized()) {
            m_xAxe.shift(widthDelta);
        }
    }

    public void initXAxe(long timeMin, long timeMax, int xMin, int xMax) {
        m_xAxe.init(timeMin, timeMax, xMin, xMax);
    }

    public void shift(int drag) {
        shiftXAxe(drag);
    }

    public void zoom(boolean in) {
        if (m_xAxe.isInitialized()) {
            m_xAxe.zoom(in);
        }
    }
}

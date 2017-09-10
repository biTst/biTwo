package bi.two;

import bi.two.chart.ChartData;
import bi.two.chart.ChartPaintSetting;
import bi.two.chart.ChartPainter;
import bi.two.chart.ChartSetting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

class ChartCanvas extends JComponent {
    private final ChartPainter m_chartPainter;
    private Font m_font;
    private ChartData m_chartData;
    private ChartSetting m_chartSetting;
    private ChartPaintSetting m_cps = new ChartPaintSetting();
    private boolean m_recalcBounds = true;
    private Point m_point; // point to highlight

    public ChartCanvas() {
        setMinimumSize(new Dimension(800, 500));
        setPreferredSize(new Dimension(800, 500));
        setBackground(Color.BLACK);

        m_chartPainter = new ChartPainter();

        MouseAdapter mouseAdapter = new MyMouseAdapter();
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);

        initChart();
    }

    public ChartData getChartData() { return m_chartData; }
    public ChartSetting getChartSetting() { return m_chartSetting; }

    private void initChart() {
        m_chartData = new ChartData();
        m_chartSetting = new ChartSetting();
    }

    @Override public void paint(Graphics g) {
        super.paint(g);

        if (m_font == null) {
            m_font = g.getFont().deriveFont(15.0f);
        }
        g.setFont(m_font);

        int width = getWidth();
        int height = getHeight();

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        if(m_recalcBounds) {
            m_cps.setBounds(width, height);
            m_recalcBounds = false;
        }

        Graphics2D g2 = initGraphics2D((Graphics2D) g);
        doPaint(g2);
    }

    private void doPaint(Graphics2D g2) {
        m_chartPainter.paintChart(g2, m_chartSetting, m_cps, m_chartData, m_point);
    }

    private Graphics2D initGraphics2D(Graphics2D g) {
        Graphics2D g2 = g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        m_recalcBounds = true;
    }


    //------------------------------------------------------------------------
    private class MyMouseAdapter extends MouseAdapter {

        private Integer m_dragStartX;
        private Integer m_dragDeltaX;
        private float m_zoom = 1;

        @Override public void mouseEntered(MouseEvent e) { updatePoint(e.getPoint()); }

        @Override public void mouseExited(MouseEvent e) { updatePoint(null); }

        @Override public void mouseMoved(MouseEvent e) { updatePoint(e.getPoint()); }

        @Override public void mouseWheelMoved(MouseWheelEvent e) {
            onMouseWheelMoved(e);
        }

        @Override public void mousePressed(MouseEvent e) {
            int x = e.getX();
            m_dragStartX = x;
            updatePoint(e.getPoint());
        }

        @Override public void mouseDragged(MouseEvent e) {
            int x = e.getX();
            int prevDragDeltaX = (m_dragDeltaX == null) ? 0 : m_dragDeltaX;
            m_dragDeltaX = x - m_dragStartX;

            int drag = m_dragDeltaX - prevDragDeltaX;
            m_cps.shift(drag);
            updatePoint(e.getPoint());
        }

        @Override public void mouseReleased(MouseEvent e) {
            int x = e.getX();
            m_dragStartX = null;
            m_dragDeltaX = null;
            updatePoint(e.getPoint());
        }

        private void updatePoint(Point point) {
            m_point = point;
            repaint(150);
        }

        private void onMouseWheelMoved(MouseWheelEvent e) {
            int notches = e.getWheelRotation();
            m_cps.zoom(notches > 0);
            repaint(150);
        }
    }
}

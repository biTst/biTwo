package bi.two;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

class ChartFrame extends JFrame {
    private final ChartCanvas m_chartCanvas;

    public ChartFrame() throws java.awt.HeadlessException {
        setTitle("bi.two");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        m_chartCanvas = new ChartCanvas();
        add(m_chartCanvas);
        pack();

        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                removeComponentListener(this);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() {
                        toFront();
                    }
                });
            }
        });
    }

    public ChartCanvas getChartCanvas() { return m_chartCanvas; }
}

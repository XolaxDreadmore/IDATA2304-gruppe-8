package ntnu.bidata.iir.controlpanel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;

final class LineChartPanel extends JPanel {
    private static final Color NORMAL = new Color(0x2F7DD1);
    private static final Color ALERT = new Color(0xD64545);

    private SensorSeries series;

    LineChartPanel() {setPreferredSize(new java.awt.Dimension(400,200));}

    void setSeries(SensorSeries series) {
        this.series = series;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int left = 56;
        int right = 14;
        int top = 28;
        int bottom = 16;
        g.setColor(getForeground());
        FontMetrics fm = g.getFontMetrics();

        List<Double> points = series == null ? List.of() : series.history();
        if (points.size() < 2)  {
            String text = series == null ? "Select a sensor to see its history" : "Waiting for readings...";
            g.drawString(text, (width - fm.stringWidth(text)) / 2, height / 2);
            g.dispose();
            return;
        }

        double min = points.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = points.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max - min < 1e-6) {
            min -= 1;
            max += 1;
        }
        double margin = (max - min) * 0.1;
        min -= margin;
        max += margin;

        int plotWidth = width - left - right;
        int plotHeight= height - top - bottom;

        //the title and gridlines.
        g.drawString(series.id(), left, top - 10);
        Color grid = new Color(getForeground().getRed(), getForeground().getGreen(), getForeground().getBlue(), 40);
        for (int i = 0; i <= 4; i++) {
            int y = top + plotHeight * i / 4;
            double value = max - (max - min) * i / 4;
            g.setColor(grid);
            g.drawLine(left, y, left + plotWidth, y);
            g.setColor(getForeground());
            String label = String.format(Locale.ROOT, max - min < 2 ? "%.2f" : "%.1f", value);
            g.drawString(label, left - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }

        //line
        g.setColor(series.anomaly() != null ? ALERT : NORMAL);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int count = points.size();
        int prevX = 0;
        int prevY = 0;
        for (int i = 0; i < count; i++) {
            int x = left + plotWidth * i / (count - 1);
            int y = top + (int) Math.round(plotHeight * (max - points.get(i)) / (max - min));
            if (i > 0) {
                g.drawLine(prevX, prevY, x, y);
            }
            prevX = x;
            prevY = y;
        }
        g.fillOval(prevX - 4, prevY - 4, 8, 8);
        g.dispose();
    }
}

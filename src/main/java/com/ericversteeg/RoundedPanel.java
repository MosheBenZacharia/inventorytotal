package com.ericversteeg;

import javax.swing.*;
import java.awt.*;

public class RoundedPanel extends JPanel {
    @Override
    protected void paintComponent(Graphics g) {
        int arc = 16; // Adjust the arc value to control the roundness of the corners
        int borderWidth = 0; // Adjust the border width

        // super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(getBackground());
        g2d.fillArc(borderWidth, borderWidth, arc, arc, 90, 90);
        g2d.fillArc(getWidth() - (borderWidth + arc), borderWidth, arc, arc, 0, 90);
        g2d.fillRect(borderWidth+(arc/2), borderWidth, getWidth() - (2*borderWidth + arc), arc/2);
        g2d.fillRect(borderWidth, arc/2 + borderWidth, getWidth() - 2*borderWidth, getHeight() - (borderWidth + arc/2));

        g2d.setColor(getForeground());
        g2d.drawArc(borderWidth, borderWidth, arc, arc, 90, 90);
        g2d.drawArc(getWidth() - (borderWidth + arc), borderWidth, arc, arc, 0, 90);
        g2d.drawLine(borderWidth + arc/2, borderWidth, getWidth() - arc/2, borderWidth);
        g2d.drawLine(borderWidth, arc/3 + borderWidth, borderWidth, getHeight() - (borderWidth));
        g2d.drawLine(getWidth()- borderWidth-1, arc/3 + borderWidth, getWidth() - borderWidth-1, getHeight() - (borderWidth));

        g2d.dispose();
    }
}
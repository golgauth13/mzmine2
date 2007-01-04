/*
 * Copyright 2006 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.visualizers.rawdata.neutralloss;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import net.sf.mzmine.util.TimeNumberFormat;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;

/**
 * 
 */
class NeutralLossPlot extends ChartPanel {

    private JFreeChart chart;

    private XYPlot plot;
    private XYItemRenderer renderer;

    // TODO: get these from parameter storage
    private static NumberFormat rtFormat = new TimeNumberFormat();
    private static NumberFormat mzFormat = new DecimalFormat("0");
    

    
    //  crosshair (selection) color
    private static final Color crossHairColor = Color.gray; 
    
    // crosshair stroke
    private static final BasicStroke crossHairStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 1.0f, new float[] { 5, 3 }, 0);

    // title font
    private static final Font titleFont = new Font("SansSerif", Font.PLAIN, 11);

    private TextTitle chartTitle;
    
    private double highlightedMin, highlightedMax;
    
 //   private TwoDItemRenderer renderer;

    
    NeutralLossPlot(NeutralLossVisualizerWindow visualizer, NeutralLossDataSet dataset, int xAxisType) {
        // superconstructor with no chart yet
        // disable off-screen buffering (makes problems with late drawing of the title)
        super(null, false);

        setBackground(Color.white);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        // set the X axis (retention time) properties
        NumberAxis xAxis;
        if (xAxisType == 0) {
            xAxis = new NumberAxis("Parent mass");
            xAxis.setNumberFormatOverride(mzFormat);
            xAxis.setUpperMargin(0);
            xAxis.setLowerMargin(0);
        } else {
            xAxis = new NumberAxis("Retention time");
            xAxis.setNumberFormatOverride(rtFormat);
            xAxis.setUpperMargin(0);
            xAxis.setLowerMargin(0);
        }

        // set the Y axis (intensity) properties
        NumberAxis yAxis = new NumberAxis("Neutral loss");
        yAxis.setAutoRangeIncludesZero(false);
        yAxis.setNumberFormatOverride(mzFormat);
        yAxis.setUpperMargin(0);
        yAxis.setLowerMargin(0);
        
        // set the renderer properties
        renderer = new NeutralLossDataPointRenderer(this);

        // tooltips
        renderer.setToolTipGenerator(dataset);

        
        // set the plot properties
        plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(Color.white);
       // TODO plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        
        // chart properties
        chart = new JFreeChart("", titleFont, plot, false); 
        chart.setBackgroundPaint(Color.white);
        
        setChart(chart);

        // title
        chartTitle = chart.getTitle();
        chartTitle.setMargin(5,0,0,0);
        chartTitle.setFont(titleFont);
        
        // disable maximum size (we don't want scaling)
        setMaximumDrawWidth(Integer.MAX_VALUE);
        setMaximumDrawHeight(Integer.MAX_VALUE);

        
        

        // set crosshair (selection) properties
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setDomainCrosshairPaint(crossHairColor);
        plot.setRangeCrosshairPaint(crossHairColor);
        plot.setDomainCrosshairStroke(crossHairStroke);
        plot.setRangeCrosshairStroke(crossHairStroke);
        
        plot.addRangeMarker(new ValueMarker(0));


        

        
        // add items to popup menu
        JMenuItem highlightMenuItem;

        
        highlightMenuItem = new JMenuItem("Highlight precursor m/z range...");
        highlightMenuItem.addActionListener(visualizer);
        highlightMenuItem.setActionCommand("HIGHLIGHT");
        add(highlightMenuItem);

        JPopupMenu popupMenu = getPopupMenu();
        popupMenu.addSeparator();
        popupMenu.add(highlightMenuItem);

    }

   
    void setTitle(String title) {
        chartTitle.setText(title);
    }




    
    /**
     * @return Returns the highlightedMin.
     */
    double getHighlightedMin() {
        return highlightedMin;
    }
    
    
    /**
     * @param highlightedMin The highlightedMin to set.
     */
    void setHighlightedMin(double highlightedMin) {
        this.highlightedMin = highlightedMin;
    }
    
    /**
     * @return Returns the highlightedMax.
     */
    double getHighlightedMax() {
        return highlightedMax;
    }
    
    /**
     * @param highlightedMax The highlightedMax to set.
     */
    void setHighlightedMax(double highlightedMax) {
        this.highlightedMax = highlightedMax;
    }




}
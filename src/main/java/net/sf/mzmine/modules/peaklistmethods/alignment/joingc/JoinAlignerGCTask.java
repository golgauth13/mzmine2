/*
 * Copyright 2006-2015 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peaklistmethods.alignment.joingc;

import java.text.Format;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Logger;

import org.apache.commons.collections.CollectionUtils;
import net.sf.mzmine.datamodel.Feature;
import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.datamodel.PeakIdentity;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.RawDataFile;
import net.sf.mzmine.datamodel.impl.SimpleFeature;
import net.sf.mzmine.datamodel.impl.SimplePeakIdentity;
import net.sf.mzmine.datamodel.impl.SimplePeakList;
import net.sf.mzmine.datamodel.impl.SimplePeakListAppliedMethod;
import net.sf.mzmine.datamodel.impl.SimplePeakListRow;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.peaklistmethods.normalization.rtadjuster.JDXCompound;
import net.sf.mzmine.modules.peaklistmethods.normalization.rtadjuster.JDXCompoundsIdentificationSingleTask;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import net.sf.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;
import net.sf.mzmine.util.PeakListRowSorter;
import net.sf.mzmine.util.PeakUtils;
import net.sf.mzmine.util.RangeUtils;
import net.sf.mzmine.util.SortingDirection;
import net.sf.mzmine.util.SortingProperty;

import com.google.common.collect.Range;

public class JoinAlignerGCTask extends AbstractTask {

    private Logger logger = Logger.getLogger(this.getClass().getName());

    
    public static String TASK_NAME = "Join aligner GC";
    
    private final MZmineProject project;
    private PeakList peakLists[];
    private PeakList alignedPeakList;

    // Processed rows counter
    private int processedRows, totalRows;

    private String peakListName;
    private RowVsRowOrderType comparisonOrder;
    static private boolean useOldestRDFAncestor;
    private MZTolerance mzTolerance;
    private RTTolerance rtTolerance;
    private double mzWeight, rtWeight;
    private double minScore;
    private double idWeight;
    
    private boolean useApex, useKnownCompoundsAsRef;
    private RTTolerance rtToleranceAfter;
    
    /** GLG HACK: temporary removed for clarity
    private boolean sameIDRequired, sameChargeRequired, compareIsotopePattern;
    **/
    private ParameterSet parameters;

    // ID counter for the new peaklist
    private int newRowID = 1;
    
    //
    private Format rtFormat = MZmineCore.getConfiguration().getRTFormat();


    JoinAlignerGCTask(MZmineProject project, ParameterSet parameters) {

        this.project = project;
        this.parameters = parameters;

        peakLists = parameters.getParameter(JoinAlignerGCParameters.peakLists)
                .getValue().getMatchingPeakLists();

        peakListName = parameters.getParameter(
                JoinAlignerGCParameters.peakListName).getValue();
        
        comparisonOrder = parameters.getParameter(
                JoinAlignerGCParameters.comparisonOrder).getValue();

        useOldestRDFAncestor = parameters.getParameter(
                JoinAlignerGCParameters.useOldestRDFAncestor).getValue();
        
        mzTolerance = parameters
                .getParameter(JoinAlignerGCParameters.MZTolerance).getValue();
        rtTolerance = parameters
                .getParameter(JoinAlignerGCParameters.RTTolerance).getValue();

        mzWeight = parameters.getParameter(JoinAlignerGCParameters.MZWeight)
                .getValue();

        rtWeight = parameters.getParameter(JoinAlignerGCParameters.RTWeight)
                .getValue();
        rtWeight = 0.0;
        
        minScore = parameters.getParameter(JoinAlignerGCParameters.minScore)
                .getValue();
        
//        idWeight = parameters.getParameter(JoinAlignerParameters.IDWeight)
//                .getValue();
        idWeight = 0.0;
        
        
        //***
        useApex = parameters.getParameter(
                JoinAlignerGCParameters.useApex).getValue();
        useKnownCompoundsAsRef = parameters.getParameter(
                JoinAlignerGCParameters.useKnownCompoundsAsRef).getValue();
        rtToleranceAfter = parameters.getParameter(
                JoinAlignerGCParameters.RTToleranceAfter).getValue();
        //***
        
        /** GLG HACK: temporarily removed for clarity
        sameChargeRequired = parameters.getParameter(
                JoinAlignerParameters.SameChargeRequired).getValue();

        sameIDRequired = parameters.getParameter(
                JoinAlignerParameters.SameIDRequired).getValue();

        compareIsotopePattern = parameters.getParameter(
                JoinAlignerParameters.compareIsotopePattern).getValue();
        **/
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getTaskDescription()
     */
    public String getTaskDescription() {
        return "Join aligner GC, " + peakListName + " (" + peakLists.length
                + " peak lists)";
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getFinishedPercentage()
     */
    public double getFinishedPercentage() {
        if (totalRows == 0)
            return 0f;
        return (double) processedRows / (double) totalRows;
    }

    /**
     * @see Runnable#run()
     */
    public void run() {

        if ((mzWeight == 0) && (rtWeight == 0)) {
            setStatus(TaskStatus.ERROR);
            setErrorMessage("Cannot run alignment, all the weight parameters are zero");
            return;
        }

        setStatus(TaskStatus.PROCESSING);
        logger.info("Running join aligner");

        // Remember how many rows we need to process. Each row will be processed
        // twice, first for score calculation, second for actual alignment.
        for (int i = 0; i < peakLists.length; i++) {
            totalRows += peakLists[i].getNumberOfRows() * 2;
        }

        // Collect all data files
        Vector<RawDataFile> allDataFiles = new Vector<RawDataFile>();
        for (PeakList peakList : peakLists) {

            for (RawDataFile dataFile : peakList.getRawDataFiles()) {

                // Each data file can only have one column in aligned peak list
                if (allDataFiles.contains(dataFile)) {
                    setStatus(TaskStatus.ERROR);
                    setErrorMessage("Cannot run alignment, because file "
                            + dataFile + " is present in multiple peak lists");
                    return;
                }

                allDataFiles.add(dataFile);
            }
        }

        // Create a new aligned peak list
        alignedPeakList = new SimplePeakList(peakListName,
                allDataFiles.toArray(new RawDataFile[0]));

        /** RTAdjustement mapping **/
        boolean recalibrateRT = useKnownCompoundsAsRef;
        Hashtable<RawDataFile, double[]> rtAdjustementMapping = new Hashtable<RawDataFile, double[]>();
        if (recalibrateRT) {
            
            boolean rtAdjustOk = true;
            // Iterate source peak lists
            // RT mapping based on the first PeakList
            double rt1 = -1.0;
            double rt2 = -1.0;
            for (int i=0; i < peakLists.length; ++i) {
                //double offset, scale;
                PeakList a_pl = peakLists[i];
                
                // Get ref RT1 and RT2
                // Sort peaks by ascending RT
                PeakListRow a_pl_rows[] = a_pl.getRows().clone(); 
                Arrays.sort(a_pl_rows, new PeakListRowSorter(SortingProperty.RT, SortingDirection.Ascending));
                ArrayList<PeakListRow> allIdentified = new ArrayList<PeakListRow>();
                for (int j=0; j < a_pl_rows.length; ++j) {
                    PeakListRow row = a_pl_rows[j];
//                    // If row actually was identified
//                    if (row.getPreferredPeakIdentity() != null 
//                            && !row.getPreferredPeakIdentity().getName().equals(JDXCompound.UNKNOWN_JDX_COMP.getName())) {
//                        allIdentified.add(row);
//                    }
                    // If row actually was identified AND is a "reference compound"
                    //**if (row.getPreferredPeakIdentity() != null) {
                    if (JDXCompound.isKnownIdentity(row.getPreferredPeakIdentity())) {
                        String isRefCompound = row.getPreferredPeakIdentity().getPropertyValue(AlignedRowProps.PROPERTY_IS_REF);
                        if (isRefCompound != null && isRefCompound.equals(AlignedRowProps.TRUE)) {
                            allIdentified.add(row);
                        }
                    } else {
                        logger.info("aFailed 1: " + row.getPreferredPeakIdentity());
                        logger.info("aFailed 2: " + row.getPreferredPeakIdentity().getPropertyValue(AlignedRowProps.PROPERTY_IS_REF));                       
                    }
                }
                //
                logger.info("allIdentified: NB found compounds: " + allIdentified.size());
                for (PeakListRow r : allIdentified) {
                    logger.info(r.getPreferredPeakIdentity().toString());
                }
                
                // Two ref compounds max, for now...
                if (allIdentified.size() == 2) {
                    // TODO: Or even better: duplicate the Peaklists involved and update the peaks RT
                    //          using "Feature.setRT()", as planned some time ago via button "Apply & Adjust RT"
                    //          from RTAdjuster module's result table !!!
                    rt1 = allIdentified.get(0).getAverageRT();
                    rt2 = allIdentified.get(1).getAverageRT();
                } else {
                    logger.info("Error => Couldn't identify the 2 required ref compounds for peaklist: " + a_pl);
                    rtAdjustOk = false;
                    continue;
                }
                
                // Think of "y = ax+b" (line equation)
                double b_offset, a_scale;
                // First list as ref, so:
                if (i == 0) {
                    b_offset = 0.0;
                    a_scale = 0.0;
                    //
                    rtAdjustementMapping.put(a_pl.getRawDataFile(0), new double[]{ b_offset, a_scale, rt1, rt2 });
                } else {
                    RawDataFile refPL_RDF = peakLists[0].getRawDataFile(0);
                    double rt1_ref = rtAdjustementMapping.get(refPL_RDF)[2];
                    double rt2_ref = rtAdjustementMapping.get(refPL_RDF)[3];
                    //
                    a_scale = ((rt2_ref - rt2) - (rt1_ref - rt1)) / (rt2 - rt1);
                    b_offset = (rt1_ref - rt1) - (a_scale * rt1);
                    //
                    rtAdjustementMapping.put(a_pl.getRawDataFile(0), new double[]{ b_offset, a_scale, rt1, rt2 });
                    logger.info(">> peakLists[0]/peakLists[i]:" + peakLists[0] + "/" + peakLists[i]);
                    logger.info(">> rt1_ref/rt1:" + rt1_ref + "/" + rt1);
                    logger.info(">> rt2_ref/rt2:" + rt2_ref + "/" + rt2);
                    logger.info(">> offset/scale: " + b_offset + "/" + a_scale);
                }
    
            }
            //
            if (!rtAdjustOk) {
                setStatus(TaskStatus.ERROR);
                setErrorMessage("Cannot run alignment, because ref compounds detection was incomplete");
                return;
            }
        }
        
        
        /** Alignment mapping **/ 
        // Iterate source peak lists
        Hashtable<SimpleFeature, Double> rtPeaksBackup = new Hashtable<SimpleFeature, Double>();
        Hashtable<PeakListRow, Object[]> infoRowsBackup = new Hashtable<PeakListRow, Object[]>();
        // Build comparison order
        ArrayList<Integer> orderIds = new ArrayList<Integer>();
        for (int i=0; i < peakLists.length; ++i) { orderIds.add(i); }
        logger.info("ORDER: " + comparisonOrder);
        if (comparisonOrder == RowVsRowOrderType.RANDOM) {
            Collections.shuffle(orderIds);
        } else if (comparisonOrder == RowVsRowOrderType.REVERSE_SEL) {
            Collections.reverse(orderIds);
        }
        Integer[] newIds = orderIds.toArray(new Integer[orderIds.size()]);
        //
        for (int i = 0; i < newIds.length; ++i) {
            
            PeakList peakList = peakLists[newIds[i]];
            
            // Create a sorted set of scores matching
            TreeSet<RowVsRowScoreGC> scoreSet = new TreeSet<RowVsRowScoreGC>();

            PeakListRow allRows[] = peakList.getRows();
            logger.info("Treating list " + peakList + " / NB rows: " + allRows.length);

            // Calculate scores for all possible alignments of this row
            for (PeakListRow row : allRows) {

                if (isCanceled())
                    return;

                // Calculate limits for a row with which the row can be aligned
//                Range<Double> mzRange = mzTolerance.getToleranceRange(row
//                        .getAverageMZ());
//                Range<Double> rtRange = rtTolerance.getToleranceRange(row
//                        .getAverageRT());
                // GLG HACK: Use best peak rather than average. No sure it is better... ???
                Range<Double> mzRange = mzTolerance.getToleranceRange(row
                        .getBestPeak().getMZ());
                Range<Double> rtRange = rtTolerance.getToleranceRange(row
                        .getBestPeak().getRT());

                // Get all rows of the aligned peaklist within parameter limits
                PeakListRow candidateRows[] = alignedPeakList
                        .getRowsInsideScanAndMZRange(rtRange, mzRange);

                // Calculate scores and store them
                for (PeakListRow candidate : candidateRows) {

                    /** GLG HACK: temporarily removed for clarity
                    if (sameChargeRequired) {
                        if (!PeakUtils.compareChargeState(row, candidate))
                            continue;
                    }

                    if (sameIDRequired) {
                        if (!PeakUtils.compareIdentities(row, candidate))
                            continue;
                    }

                    if (compareIsotopePattern) {
                        IsotopePattern ip1 = row.getBestIsotopePattern();
                        IsotopePattern ip2 = candidate.getBestIsotopePattern();

                        if ((ip1 != null) && (ip2 != null)) {
                            ParameterSet isotopeParams = parameters
                                    .getParameter(
                                            JoinAlignerParameters.compareIsotopePattern)
                                    .getEmbeddedParameters();

                            if (!IsotopePatternScoreCalculator.checkMatch(ip1,
                                    ip2, isotopeParams)) {
                                continue;
                            }
                        }
                    }
                    **/

//                    RowVsRowScore score = new RowVsRowScore(this.project, 
//                            row, candidate,
//                            RangeUtils.rangeLength(mzRange) / 2.0, mzWeight,
//                            RangeUtils.rangeLength(rtRange) / 2.0, rtWeight);
                    // GLG HACK: Use apex rather than average!
                    RowVsRowScoreGC score = new RowVsRowScoreGC(
                            this.project, useOldestRDFAncestor,
                            row.getRawDataFiles()[0], rtAdjustementMapping,
                            row, candidate,
                            RangeUtils.rangeLength(mzRange) / 2.0, mzWeight,
                            RangeUtils.rangeLength(rtRange) / 2.0, rtWeight,
                            idWeight,
                            useApex, useKnownCompoundsAsRef, rtToleranceAfter);

                    // If match was not rejected afterwards and score is acceptable
                    // (Acceptable score => higher than absolute min ever and higher than user defined min)
                    // 0.0 is OK for a minimum score only in "Dot Product" method (Not with "Person Correlation")
                    //if (score.getScore() > JDXCompoundsIdentificationSingleTask.MIN_SCORE_ABSOLUTE)
                    if (score.getScore() > Math.max(JDXCompoundsIdentificationSingleTask.MIN_SCORE_ABSOLUTE, minScore))
                        scoreSet.add(score);

                }

                processedRows++;

            }

            // Create a table of mappings for best scores
            Hashtable<PeakListRow, PeakListRow> alignmentMapping = new Hashtable<PeakListRow, PeakListRow>();

            // Iterate scores by descending order
            Iterator<RowVsRowScoreGC> scoreIterator = scoreSet.iterator();
            //Hashtable<PeakListRow, RowVsRowScore> scoresMapping = new Hashtable<PeakListRow, RowVsRowScore>();
            while (scoreIterator.hasNext()) {

                RowVsRowScoreGC score = scoreIterator.next();

                // Check if the row is already mapped
                if (alignmentMapping.containsKey(score.getPeakListRow()))
                    continue;

                // Check if the aligned row is already filled
                if (alignmentMapping.containsValue(score.getAlignedRow()))
                    continue;

                alignmentMapping.put(score.getPeakListRow(),
                        score.getAlignedRow());
                //scoresMapping.put(score.getPeakListRow(), score);
                //scoresMapping.put(score.getAlignedRow(), score);

            }

            // Align all rows using mapping
            for (PeakListRow row : allRows) {

                PeakListRow targetRow = alignmentMapping.get(row);

                // If we have no mapping for this row, add a new one
                if (targetRow == null) {
                    targetRow = new SimplePeakListRow(newRowID);
                    newRowID++;
                    alignedPeakList.addRow(targetRow);
                    //
                    infoRowsBackup.put(targetRow, new Object[] { 
                            new HashMap<RawDataFile, Double[]>(), 
                            new HashMap<RawDataFile, PeakIdentity>(), 
                            new HashMap<RawDataFile, Double>() 
                            });
                }
                
                // Add all non-existing identities from the original row to the
                // aligned row
                //PeakUtils.copyPeakListRowProperties(row, targetRow);
                // Set the preferred identity
                ////if (row.getPreferredPeakIdentity() != null)
                if (JDXCompound.isKnownIdentity(row.getPreferredPeakIdentity())) {
                    targetRow.setPreferredPeakIdentity(row.getPreferredPeakIdentity());
//                    //JDXCompound.setPreferredPeakIdentity(targetRow, row.getPreferredPeakIdentity());
//                    SimplePeakIdentity newIdentity = new SimplePeakIdentity(prop);
//                    targetRow.setPreferredPeakIdentity(row.getPreferredPeakIdentity());
                }
                else
                    targetRow.setPreferredPeakIdentity(JDXCompound.createUnknownCompound());
//                    JDXCompound.setPreferredPeakIdentity(targetRow, JDXCompound.createUnknownCompound());
                
                    

                // Add all peaks from the original row to the aligned row
                //for (RawDataFile file : row.getRawDataFiles()) {
                for (RawDataFile file : alignedPeakList.getRawDataFiles()) {
                    
//                    if (recalibrateRT) {
//                        if (!Arrays.asList(row.getRawDataFiles()).contains(file)) {
//                            double b_offset = rtAdjustementMapping.get(peakList)[0];
//                            double a_scale = rtAdjustementMapping.get(peakList)[1];
//                            ((HashMap<RawDataFile, Double[]>) infoRowsBackup.get(targetRow)[0]).put(file, new Double[] { Double.NaN, b_offset, a_scale });                        
//                            //continue;
//                            //break;
//                        }
//                    }

                    if (Arrays.asList(row.getRawDataFiles()).contains(file)) {
                        
                        Feature originalPeak = row.getPeak(file);
                        if (originalPeak != null) {
                            
                            if (recalibrateRT) {
                                // Set adjusted retention time to all peaks in this row
                                // *[Note 1]
                                RawDataFile pl_RDF = peakList.getRawDataFile(0);
                                logger.info("{" + rtAdjustementMapping.get(pl_RDF)[0] + ", " + rtAdjustementMapping.get(pl_RDF)[1] + "}");
                                double b_offset = rtAdjustementMapping.get(pl_RDF)[0];
                                double a_scale = rtAdjustementMapping.get(pl_RDF)[1];
                                //
                                double adjustedRT = JoinAlignerGCTask.getAdjustedRT(originalPeak.getRT(), b_offset, a_scale);
                                
                                SimpleFeature adjustedPeak = new SimpleFeature(originalPeak);
                                PeakUtils.copyPeakProperties(originalPeak, adjustedPeak);
                                adjustedPeak.setRT(adjustedRT);
                                logger.info("adjusted Peak/RT = " + originalPeak + ", " + adjustedPeak + " / " + originalPeak.getRT() + ", " + adjustedPeak.getRT());
    
                                targetRow.addPeak(file, adjustedPeak);
                                // Adjusted RT info
                                rtPeaksBackup.put(adjustedPeak, originalPeak.getRT());
                                ((HashMap<RawDataFile, Double[]>) infoRowsBackup.get(targetRow)[0]).put(file, new Double[] { adjustedRT, b_offset, a_scale });//originalPeak.getRT());
                                
                            } else {
                                targetRow.addPeak(file, originalPeak);
                            }
                            
                            // Identification info
                            ((HashMap<RawDataFile, PeakIdentity>) infoRowsBackup.get(targetRow)[1]).put(file, targetRow.getPreferredPeakIdentity());
                            //
                            String strScore = targetRow.getPreferredPeakIdentity().getPropertyValue(AlignedRowProps.PROPERTY_ID_SCORE);
                            if (strScore != null)
                                ((HashMap<RawDataFile, Double>) infoRowsBackup.get(targetRow)[2]).put(file, Double.valueOf(strScore));
                            else
                                ((HashMap<RawDataFile, Double>) infoRowsBackup.get(targetRow)[2]).put(file, 0.0);
                            
                            logger.info("targetRow RT=" + targetRow.getPeaks()[targetRow.getPeaks().length-1].getRT() + " / ID: " + newRowID);
                        }
                        else {
                            setStatus(TaskStatus.ERROR);
                            setErrorMessage("Cannot run alignment, no originalPeak");
                            return;
                        }
    
                    } 
//                    else {
//                        if (recalibrateRT) {
//                            double b_offset = rtAdjustementMapping.get(peakList)[0];
//                            double a_scale = rtAdjustementMapping.get(peakList)[1];
//                            ((HashMap<RawDataFile, Double[]>) infoRowsBackup.get(targetRow)[0]).put(file, new Double[] { Double.NaN, b_offset, a_scale });                        
//                            //continue;
//                        }
//                    }

                }

                // Copy all possible peak identities, if these are not already present
                for (PeakIdentity identity : row.getPeakIdentities()) {
                    PeakIdentity clonedIdentity = (PeakIdentity) identity.clone();
                    if (!PeakUtils.containsIdentity(targetRow, clonedIdentity))
                        targetRow.addPeakIdentity(clonedIdentity, false);
                }
                
//                // Notify MZmine about the change in the project
//                // TODO: Get the "project" from the instantiator of this class instead.
//                // Still necessary ???????
//                MZmineProject project = MZmineCore.getProjectManager().getCurrentProject();
//                project.notifyObjectChanged(targetRow, false);
//                // Repaint the window to reflect the change in the peak list
//                Desktop desktop = MZmineCore.getDesktop();
//                if (!(desktop instanceof HeadLessDesktop))
//                    desktop.getMainWindow().repaint();
                
                processedRows++;

            }

        } // Next peak list
        
        
        // Restore real RT - for the sake of consistency
        // (the adjusted one was only useful during alignment process)
        // WARN: Must be done before "Post processing" part to take advantage
        //       of the "targetRow.update()" used down there
        for (SimpleFeature peak : rtPeaksBackup.keySet()) {
            peak.setRT((double) rtPeaksBackup.get(peak));
        }

        
        /** Post-processing... **/
        // Build reference RDFs index: We need an ordered reference here, to we able to parse
        // correctly while reading back stored info
        RawDataFile[] rdf_sorted = alignedPeakList.getRawDataFiles().clone();
        Arrays.sort(rdf_sorted, new RawDataFileSorter(SortingDirection.Ascending));
        
        // Process
        for (PeakListRow targetRow: infoRowsBackup.keySet()) {
            
            // Refresh averaged RTs...
            ((SimplePeakListRow) targetRow).update();
            
            HashMap<RawDataFile, Double[]> rowRTinfo = ((HashMap<RawDataFile, Double[]>) infoRowsBackup.get(targetRow)[0]);
            HashMap<RawDataFile, PeakIdentity> rowIDs = ((HashMap<RawDataFile, PeakIdentity>) infoRowsBackup.get(targetRow)[1]);
            HashMap<RawDataFile, Double> rowIDsScores = ((HashMap<RawDataFile, Double>) infoRowsBackup.get(targetRow)[2]);
            
            String[] rowIDsNames = new String[rowIDs.values().size()];
            int i = 0;
            for (PeakIdentity id: rowIDs.values()) {
                rowIDsNames[i] = (id != null) ? id.getName() : "";
                ++i;
            }
            
//            // Save preferred (most frequent) identity
//            // Open question: Shouldn't we be set as "most frequent", the most 
//            //                  frequent excluding UNKNOWN??? (See *[Note2])
            int mainIdentityCard = 0;
            double mainIdentitySum = 0.0;
            PeakIdentity mainIdentity = null;

            // Save original RTs and Identities
            String strAdjustedRTs = "", strOffsets = "", strScales = "";
            String strIdentities = "";
            String strScores = "";
            // Object[] = { sum, cardinality }
            HashMap<String, Object[]> scoreQuantMapping = new HashMap<String, Object[]>();
            
            /** Tricky: using preferred row identity to store information **/
            for (RawDataFile rdf: rdf_sorted) {
                
                logger.info(">>>> RDF_write: " + rdf.getName());
                
                if (Arrays.asList(targetRow.getRawDataFiles()).contains(rdf)) {

                    // Adjusted RTs of source aligned rows used to compute target row
                    if (recalibrateRT) {
                        double rt = rowRTinfo.get(rdf)[0];
//                        double offset = rowRTinfo.get(rdf)[1];
//                        double scale = rowRTinfo.get(rdf)[2];
                        strAdjustedRTs += rtFormat.format(rt) + AlignedRowProps.PROP_SEP;
//                        strOffsets += rtFormat.format(offset) + AlignedRowIdentity.IDENTITY_SEP;
//                        strScales += rtFormat.format(scale) + AlignedRowIdentity.IDENTITY_SEP;
                    }
                    
                    //
                    PeakIdentity id = rowIDs.get(rdf);
                    double score = rowIDsScores.get(rdf);
                    
                    strIdentities += id.getName() + AlignedRowProps.PROP_SEP;
                    strScores += score + AlignedRowProps.PROP_SEP;
                    
                    
//                    int cardinality = CollectionUtils.cardinality(id.getName(), Arrays.asList(rowIDsNames));
//                    if (cardinality > mainIdentityCard) {// && !id.getName().equals(JDXCompound.UNKNOWN_JDX_COMP.getName()) /* *[Note2] */) {
//                        mainIdentity = id;
//                        mainIdentityCard = cardinality;
//                    }

                    int cardinality = 0;
                    double sum = 0.0;
                    for (RawDataFile rdf2: rdf_sorted) {
                        PeakIdentity id2 = rowIDs.get(rdf2);
                        if (id2 != null && id2.getName().equals(id.getName())) {
                            cardinality++;
                            sum += rowIDsScores.get(rdf2);
                        }
                    }
                    // If overall score is zero 'cardinality' prevails, Otherwise 'sum' prevails
                    // (With sum only check 'null' and 'Unknown' identities would be in concurrency)
                    if ((sum == 0.0 && cardinality > mainIdentityCard) || sum > mainIdentitySum) {
                        mainIdentity = id;
                        mainIdentityCard = cardinality;
                        logger.info(">> found max for: " + mainIdentity.getName() + " / " + sum + ", " + mainIdentitySum + " / " + cardinality + ", " + mainIdentityCard);
                        mainIdentitySum = sum;
                    }
                    
                
                    //-

                    if (scoreQuantMapping.get(id.getName()) == null) {
                        Object[] infos = { score, 1 };
                        scoreQuantMapping.put(id.getName(), infos);
                    } else {
                        Object[] infos = scoreQuantMapping.get(id.getName());
                        infos[0] = score + (double) infos[0];
                        infos[1] = 1 + (Integer) infos[1];
                    }
                    

                } else {
                    if (recalibrateRT) {
                        
                        strAdjustedRTs += AlignedRowProps.PROP_SEP;
//                      strOffsets += AlignedRowIdentity.IDENTITY_SEP;
//                      strScales += AlignedRowIdentity.IDENTITY_SEP;
                    }
                    strIdentities += AlignedRowProps.PROP_SEP;
                    strScores += AlignedRowProps.PROP_SEP;
                }
                if (recalibrateRT) {
                    // Gaps must have recalibration info as well, so do it whether or not
                    double offset = rtAdjustementMapping.get(rdf)[0];
                    double scale = rtAdjustementMapping.get(rdf)[1];
                    strOffsets += rtFormat.format(offset) + AlignedRowProps.PROP_SEP;
                    strScales += rtFormat.format(scale) + AlignedRowProps.PROP_SEP;
                }                
            }
            if (recalibrateRT) {
                strAdjustedRTs = strAdjustedRTs.substring(0, strAdjustedRTs.length()-1);
                strOffsets = strOffsets.substring(0, strOffsets.length()-1);
                strScales = strScales.substring(0, strScales.length()-1);
            }
            strIdentities = strIdentities.substring(0, strIdentities.length()-1);
            strScores = strScores.substring(0, strScores.length()-1);
            
            String strQuant = "";
            double mainIdentityQuant = Double.MIN_VALUE;
            // Calculate normalized quantification & deduce most present identity
            for (String idName : scoreQuantMapping.keySet()) {
                Object[] infos = scoreQuantMapping.get(idName);
                infos[0] = (double) infos[0] / (double) rdf_sorted.length;
                strQuant += idName + AlignedRowProps.KEYVAL_SEP + infos[0] + AlignedRowProps.PROP_SEP;
                if ((double) infos[0] > mainIdentityQuant) {
                    mainIdentityQuant = (double) infos[0];
                }
            }
            strQuant = strQuant.substring(0, strQuant.length()-1);
            ////strQuant = String.valueOf(mainIdentityQuant);
            
            //
            if (recalibrateRT) {
                logger.info(">> found max for: " + mainIdentity);
                ((SimplePeakIdentity) mainIdentity).setPropertyValue(AlignedRowProps.PROPERTY_RTS, strAdjustedRTs);
                ((SimplePeakIdentity) mainIdentity).setPropertyValue(AlignedRowProps.PROPERTY_OFFSETS, strOffsets);
                ((SimplePeakIdentity) mainIdentity).setPropertyValue(AlignedRowProps.PROPERTY_SCALES, strScales);
            }
            ((SimplePeakIdentity) mainIdentity).setPropertyValue(AlignedRowProps.PROPERTY_IDENTITIES_NAMES, strIdentities);
            ((SimplePeakIdentity) mainIdentity).setPropertyValue(AlignedRowProps.PROPERTY_IDENTITIES_SCORES, strScores);
            ((SimplePeakIdentity) mainIdentity).setPropertyValue(AlignedRowProps.PROPERTY_IDENTITIES_QUANT, strQuant);
            // Copy the original preferred identity's properties into the targetRow's preferred one
            // and update the mainIdentity properties
            for (PeakIdentity p: targetRow.getPeakIdentities()) {
                if (p.getName().equals(mainIdentity.getName())) {
                ///if (p.getName().equals(mainIdentityName)) {
                    PeakIdentity targetMainIdentity = p;
                    targetRow.setPreferredPeakIdentity(targetMainIdentity);
                //////JDXCompound.setPreferredPeakIdentity(targetRow, targetMainIdentity);
                    // Copy props
                    for (String key: mainIdentity.getAllProperties().keySet()) {
                        String value = mainIdentity.getAllProperties().get(key);
                        ((SimplePeakIdentity) targetMainIdentity).setPropertyValue(key, value);
                    }
                    
                    break;
                }
            }
            
            
            // Notify MZmine about the change in the project, necessary ???
            MZmineProject project = MZmineCore.getProjectManager().getCurrentProject();
            project.notifyObjectChanged(targetRow, false);
        }
        
        
        
        

        // Add new aligned peak list to the project
        project.addPeakList(alignedPeakList);

        // Add task description to peakList
        alignedPeakList
                .addDescriptionOfAppliedTask(new SimplePeakListAppliedMethod(
                        JoinAlignerGCTask.TASK_NAME, parameters));

        logger.info("Finished join aligner");
        setStatus(TaskStatus.FINISHED);

    }

    public static double getAdjustedRT(double rt, double b_offset, double a_scale) {
        double delta_rt = a_scale * rt + b_offset;
        return (rt + delta_rt);
    }

    public static double getReverseAdjustedRT(double rt, double b_offset, double a_scale) {
        double delta_rt = a_scale * rt + b_offset;
        return (rt - delta_rt);
    }
    
}

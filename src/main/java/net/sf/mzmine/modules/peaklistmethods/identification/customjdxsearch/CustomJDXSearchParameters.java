/*
 * Copyright 2006-2014 The MZmine 2 Development Team
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

package net.sf.mzmine.modules.peaklistmethods.identification.customjdxsearch;

import java.text.NumberFormat;

import net.sf.mzmine.datamodel.IonizationType;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.peaklistmethods.normalization.rtadjuster.SimilarityMethodType;
import net.sf.mzmine.parameters.Parameter;
import net.sf.mzmine.parameters.impl.SimpleParameterSet;
import net.sf.mzmine.parameters.parametertypes.BooleanParameter;
import net.sf.mzmine.parameters.parametertypes.ComboParameter;
import net.sf.mzmine.parameters.parametertypes.DoubleParameter;
import net.sf.mzmine.parameters.parametertypes.filenames.DirectoryParameter;
import net.sf.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsParameter;
import net.sf.mzmine.parameters.parametertypes.ranges.RTRangeParameter;
import net.sf.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;
import net.sf.mzmine.parameters.parametertypes.StringParameter;


public class CustomJDXSearchParameters extends SimpleParameterSet {

	public static final PeakListsParameter PEAK_LISTS = new PeakListsParameter();


	public static final DirectoryParameter JDX_DIR = new DirectoryParameter(
			"JDXs directory", "Directory path containing jdx files to blast from");
	
	// TODO: ???
	/*
	public static final RTToleranceParameter RI_SEARCH_WINDOW = new RTToleranceParameter(
			"Retention Index (RI) tolerance", "...");
	 */
	
	public static final ComboParameter<SimilarityMethodType> SIMILARITY_METHOD = new ComboParameter<SimilarityMethodType>(
			"Similarity method", "Similarity method", SimilarityMethodType.values());
	
        public static final DoubleParameter AREA_MIX_FACTOR = new DoubleParameter(
                "Area Mix factor", "Weight for balancing between Similarity and Area (0.0 is 'Similarity only', otherwise huge peaks get advantged).",
                MZmineCore.getConfiguration().getIntensityFormat(), 0.0, 0.0, 1.0);

	public static final DoubleParameter MIN_SCORE = new DoubleParameter(
                "Minimum score", 
                "Minimum score for matching between two peaks to be considered as successful " +
                "(WARN: 'Pearson correlation' similarity method can imply scores < 0.0 and/or > 1.0)",
                NumberFormat.getNumberInstance(), CustomJDXSearchTask.MIN_SCORE_ABSOLUTE);
        
	
	// TODO: ???
	/*
	public static final BooleanParameter APPLY_WITHOUT_CHECK = new BooleanParameter(
	        "Apply without checking", "Apply best scoring compounds without checking manually " 
	                + "(displaying validation table) first", false);
	 */
        public static final FileNameParameter BLAST_OUTPUT_FILENAME = new FileNameParameter(
                "Blast output filename",
                " Requires \"Apply without checking\" checked." +
                        " Name of the resulting CSV file to write standard compounds best blast scores into." +
                        " If the file already exists, it will be overwritten.",
                "csv");
        public static final StringParameter FIELD_SEPARATOR = new StringParameter(
                "Field separator",
                " Requires \"Apply without checking\" checked." +
                "Character(s) used to separate fields in the exported file", ",");
	
        public static final BooleanParameter BRUTE_FORCE_ERASE = new BooleanParameter(
                "Erase previous identities", "Erases any previous identitification operation " 
                        + "(use with caution)", false);
	
	public CustomJDXSearchParameters() {
		super(new Parameter[] { PEAK_LISTS,
		        //RI_SEARCH_WINDOW
		        JDX_DIR, BRUTE_FORCE_ERASE, 
		        SIMILARITY_METHOD, AREA_MIX_FACTOR, MIN_SCORE,
			//APPLY_WITHOUT_CHECK, 
		        BLAST_OUTPUT_FILENAME, FIELD_SEPARATOR 
		});
	}

}

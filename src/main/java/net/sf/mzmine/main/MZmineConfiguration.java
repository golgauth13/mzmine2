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

package net.sf.mzmine.main;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;

import net.sf.mzmine.desktop.preferences.MZminePreferences;
import net.sf.mzmine.modules.MZmineModule;
import net.sf.mzmine.parameters.ParameterSet;

/**
 * MZmine configuration interface
 */
public interface MZmineConfiguration {

    public static final File CONFIG_FILE = new File("conf/config.xml");

    public ParameterSet getModuleParameters(Class<? extends MZmineModule> module);

    public void setModuleParameters(Class<? extends MZmineModule> module,
	    ParameterSet parameters);

    public MZminePreferences getPreferences();

    public NumberFormat getMZFormat();

    public NumberFormat getRTFormat();

    public NumberFormat getIntensityFormat();

    public void loadConfiguration(File file) throws IOException;

    public void saveConfiguration(File file) throws IOException;
    
    public String getRexecPath();

    public Boolean getSendStatistics();

//    
//    public Boolean getPreferRTlabels();
}

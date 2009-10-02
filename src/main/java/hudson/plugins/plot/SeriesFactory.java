/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;

/**
 * This class creates a Series class based on the data source
 * 
 * @author areese
 *
 */
public class SeriesFactory {
	private static final Logger LOGGER = Logger.getLogger(SeriesFactory.class.getName());

	/**
	 * Using file and label and the Stapler request, create a subclass of series that can process the type selected.
	 * @param file file to load.
	 * @param label
	 * @param req Stapler request so subclasses can pull in settings.
	 * @return series that is used for the data source that was picked.
	 * @throws ServletException 
	 */
	public static Series createSeries(int seriesCounter, String file, String label, StaplerRequest req) throws ServletException
	{
		// The list of radio buttons for each series configuring the type.
		String[] seriesRadioButtons = req.getParameterValues("seriesParam.rbId");
		
		LOGGER.log(Level.INFO,"button: " + seriesCounter + " " + seriesRadioButtons);

		for (String s: seriesRadioButtons)
		{
			LOGGER.log(Level.INFO,"Series: " + s);
		}
		
		/*
		 * Only one of these should be checked, since they are radio buttons each is set to on.
		 */
		String[] setting = req.getParameterValues(seriesRadioButtons[seriesCounter]);
		
		if (setting == null || setting.length<=0 || "".equals(setting[0]) || "PLOT_SEPARATOR".equals(setting[0]))
			return null;
		
		if ("properties".equals(setting[0]))
		{
			return new PropertiesSeries(file,label);
		}
		
		if ("csv".equals(setting[0]))
		{
			return CSVSeries.createSeriesFromStapler(file, label, req, seriesRadioButtons[seriesCounter]);
		}

		if ("xml".equals(setting[0]))
		{
			return new XMLSeries(file, label, req, seriesRadioButtons[seriesCounter]);
		}

		return null;
	}
};

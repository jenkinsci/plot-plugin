/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */


package hudson.plugins.plot;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Properties;

import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.Lists;

/**
 * @author Allen Reese
 *
 */
public class PropertiesSeries extends Series {
    @DataBoundConstructor
    public PropertiesSeries(String file, String label) {
    	super(file, label, "properties");
    }

    /**
     * Load the series from a properties file.
     */
	@Override
	public PlotPoint[] loadSeries(FilePath workspaceRootDir, PrintStream logger) {
        InputStream in = null;
        FilePath[] seriesFiles = null;
        
        try {
            seriesFiles = workspaceRootDir.list(getFile());
        } catch (Exception e) {
            logger.println("Exception trying to retrieve series files: " + e);
            return null;
        }
        
        if (seriesFiles != null && seriesFiles.length < 1) {
            logger.println("No plot data file found: " + getFile());
            return null;
        }
        
        try {
            List<PlotPoint> plotPoints = Lists.newArrayList();

            in = seriesFiles[0].read();
            logger.println("Saving plot series data from: " + seriesFiles[0]);
            Properties properties = new Properties();
            properties.load(in);

            {
                String yvalue = properties.getProperty("YVALUE");
                String url = properties.getProperty("URL", "");
                if (yvalue != null && url != null) {
                    plotPoints.add(new PlotPoint(yvalue, url, getLabel()));
                }
            }

            // Support multiple values using YVALUE.<X>, URL.<X>, LABEL.<X>
            // syntax
            // Label defaults to <X> if LABEL.<X> is not found
            for (String key : properties.stringPropertyNames()) {
                final String PREFIX = "YVALUE.";

                if (key.startsWith(PREFIX)) {
                    String suffix = key.substring(PREFIX.length());

                    String yvalue = properties.getProperty("YVALUE." + suffix);
                    String url = properties.getProperty("URL." + suffix, "");
                    String label = properties.getProperty("LABEL." + suffix, suffix);

                    if (yvalue != null && url != null && label != null) {
                        plotPoints.add(new PlotPoint(yvalue, url, label));
                    }
                }
            }

            if (plotPoints.size() == 0) {
                logger.println("No point values found");
                return null;
            }
            return (PlotPoint[]) plotPoints.toArray(new PlotPoint[plotPoints.size()]);
        } catch (Exception e) {
            logger.println("Exception reading plot series data from: " + seriesFiles[0]);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    //ignore
                }
            }
        }
    }
}

/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Allen Reese
 */
public class PropertiesSeries extends Series {
    private static final transient Logger LOGGER =
            Logger.getLogger(PropertiesSeries.class.getName());

    @DataBoundConstructor
    public PropertiesSeries(String file, String label) {
        super(file, label, "properties");
    }

    /**
     * Load the series from a properties file.
     */
    @Override
    public List<PlotPoint> loadSeries(FilePath workspaceRootDir, int buildNumber,
            PrintStream logger) {
        InputStream in = null;
        FilePath[] seriesFiles;

        try {
            seriesFiles = workspaceRootDir.list(getFile());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "Exception trying to retrieve series files", e);
            return null;
        }

        if (ArrayUtils.isEmpty(seriesFiles)) {
            logger.println("No plot data file found: " + getFile());
            return null;
        }

        try {
            in = seriesFiles[0].read();
            logger.println("Saving plot series data from: " + seriesFiles[0]);
            Properties properties = new Properties();
            properties.load(in);
            String yvalue = properties.getProperty("YVALUE");
            String url = properties.getProperty("URL", "");
            if (yvalue == null || url == null) {
                logger.println("Not creating point with null values: y="
                        + yvalue + " label=" + getLabel() + " url=" + url);
                return null;
            }
            List<PlotPoint> series = new ArrayList<PlotPoint>();
            series.add(new PlotPoint(yvalue, url, getLabel()));
            return series;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception reading plot series data from "
                    + seriesFiles[0], e);
            return null;
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    @Override
    public Descriptor<Series> getDescriptor() {
        return new PropertiesSeries.DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Series> {
        public String getDisplayName() {
            return Messages.Plot_PropertiesSeries();
        }

        @Override
        public Series newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return SeriesFactory.createSeries(formData, req);
        }
    }
}

/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Represents a plot data series configuration.
 *
 * @author Nigel Daley
 * @author Allen Reese
 */
public abstract class Series extends AbstractDescribableImpl<Series> {
    private static final transient Pattern PAT_NAME = Pattern.compile("%name%");
    private static final transient Pattern PAT_INDEX = Pattern.compile("%index%");
    private static final Pattern PAT_BUILD_NUMBER = Pattern.compile("%build%");

    /**
     * Relative path to the data series property file. Mandatory.
     */
    @SuppressWarnings("visibilitymodifier")
    protected String file;

    /**
     * Data series legend label. Optional.
     */
    @SuppressWarnings("visibilitymodifier")
    protected String label;

    /**
     * Data series type. Mandatory. This can be csv, xml, or properties file.
     * This should be an enum, but I am not sure how to support that with
     * stapler at the moment
     */
    @SuppressWarnings("visibilitymodifier")
    protected String fileType;

    protected Series(String file, String label, String fileType) {
        this.file = file;

        // TODO: look into this, what do we do if there is no label?
        if (label == null) {
            label = Messages.Plot_Missing();
        }

        this.label = label;
        this.fileType = fileType;
    }

    public String getFile() {
        return file;
    }

    public String getLabel() {
        return label;
    }

    public String getFileType() {
        return fileType;
    }

    /**
     * Retrieves the plot data for one series after a build from the workspace.
     *
     * @param workspaceRootDir the root directory of the workspace
     * @param buildNumber      the build Number
     * @param logger           the logger to use
     * @return a PlotPoint array of points to plot
     */
    public abstract List<PlotPoint> loadSeries(FilePath workspaceRootDir,
                                               int buildNumber, PrintStream logger);

    // Convert data from before version 1.3
    private Object readResolve() {
        return (fileType == null) ? new PropertiesSeries(file, label) : this;
    }

    /**
     * Return the url that should be used for this point.
     *
     * @param label       Name of the column
     * @param index       Index of the column
     * @param buildNumber The build number
     * @return url for the label.
     */
    protected String getUrl(String baseUrl, String label, int index, int buildNumber) {
        String resultUrl = baseUrl;
        if (resultUrl != null) {
            if (label == null) {
                // This implementation searches for tokens to replace.
                // If the argument was NULL then replacing the null with an empty string
                // should still produce the desired outcome.
                label = "";
            }
            /*
             * Check the name first, and do replacement upon it.
             */
            Matcher nameMatcher = PAT_NAME.matcher(resultUrl);
            if (nameMatcher.find()) {
                resultUrl = nameMatcher.replaceAll(label);
            }

            /*
             * Check the index, and do replacement on it.
             */
            Matcher indexMatcher = PAT_INDEX.matcher(resultUrl);
            if (indexMatcher.find()) {
                resultUrl = indexMatcher.replaceAll(String.valueOf(index));
            }

            /*
             * Check the build number first, and do replacement upon it.
             */
            Matcher buildNumberMatcher = PAT_BUILD_NUMBER.matcher(resultUrl);
            if (buildNumberMatcher.find()) {
                resultUrl = buildNumberMatcher.replaceAll(String
                        .valueOf(buildNumber));
            }
        }

        return resultUrl;
    }

    @Override
    public Descriptor<Series> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Series> {
        public String getDisplayName() {
            return Messages.Plot_Series();
        }

        @Override
        public Series newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return SeriesFactory.createSeries(formData, req);
        }
    }
}

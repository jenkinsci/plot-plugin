package hudson.plugins.plot;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Plot {@link Builder} class for pipeline.
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link PlotBuilder} is created.
 * The created instance is persisted to the project configuration XML by using XStream,
 * so this allows you to use instance fields (like {@link #group}) to remember the configuration.
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked.
 */
public class PlotBuilder extends Builder implements SimpleBuildStep {

    // Required fields
    private final String group;
    private final String style;

    // Optional fields
    @CheckForNull
    private String title;
    @CheckForNull
    private String numBuilds;
    @CheckForNull
    private String yaxis;
    @CheckForNull
    private String yaxisMinimum;
    @CheckForNull
    private String yaxisMaximum;
    private boolean useDescr;
    private boolean exclZero;
    private boolean logarithmic;
    private boolean keepRecords;

    // Generated?
    @SuppressWarnings("visibilitymodifier")
    public String csvFileName;
    /**
     * List of data series.
     */
    @SuppressWarnings("visibilitymodifier")
    public List<CSVSeries> csvSeries;
    @SuppressWarnings("visibilitymodifier")
    public List<PropertiesSeries> propertiesSeries;
    @SuppressWarnings("visibilitymodifier")
    public List<XMLSeries> xmlSeries;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    // Similarly, any optional @DataBoundSetter properties must match
    @DataBoundConstructor
    public PlotBuilder(String group, String style, String csvFileName) {
        this.group = group;
        this.style = style;
        this.csvFileName = csvFileName;
    }

    public String getGroup() {
        return group;
    }

    public String getStyle() {
        return style;
    }

    @CheckForNull
    public String getTitle() {
        return title;
    }

    @DataBoundSetter
    public final void setTitle(@CheckForNull String title) {
        this.title = Util.fixEmptyAndTrim(title);
    }

    @CheckForNull
    public String getNumBuilds() {
        return numBuilds;
    }

    @DataBoundSetter
    public final void setNumBuilds(@CheckForNull String numBuilds) {
        this.numBuilds = Util.fixEmptyAndTrim(numBuilds);
    }

    @CheckForNull
    public String getYaxis() {
        return yaxis;
    }

    @DataBoundSetter
    public final void setYaxis(@CheckForNull String yaxis) {
        this.yaxis = Util.fixEmptyAndTrim(yaxis);
    }

    public boolean getUseDescr() {
        return useDescr;
    }

    @DataBoundSetter
    public void setUseDescr(boolean useDescr) {
        this.useDescr = useDescr;
    }

    public boolean getExclZero() {
        return exclZero;
    }

    @DataBoundSetter
    public void setExclZero(boolean exclZero) {
        this.exclZero = exclZero;
    }

    public boolean getLogarithmic() {
        return logarithmic;
    }

    @DataBoundSetter
    public void setLogarithmic(boolean logarithmic) {
        this.logarithmic = logarithmic;
    }

    public boolean getKeepRecords() {
        return keepRecords;
    }

    @DataBoundSetter
    public void setKeepRecords(boolean keepRecords) {
        this.keepRecords = keepRecords;
    }

    @CheckForNull
    public String getYaxisMinimum() {
        return yaxisMinimum;
    }

    @DataBoundSetter
    public final void setYaxisMinimum(@CheckForNull String yaxisMinimum) {
        this.yaxisMinimum = Util.fixEmptyAndTrim(yaxisMinimum);
    }

    @CheckForNull
    public String getYaxisMaximum() {
        return yaxisMaximum;
    }

    @DataBoundSetter
    public final void setYaxisMaximum(@CheckForNull String yaxisMaximum) {
        this.yaxisMaximum = Util.fixEmptyAndTrim(yaxisMaximum);
    }

    public List<CSVSeries> getCsvSeries() {
        return csvSeries;
    }

    @DataBoundSetter
    public void setCsvSeries(List<CSVSeries> csvSeries) {
        this.csvSeries = csvSeries;
    }

    public List<PropertiesSeries> getPropertiesSeries() {
        return propertiesSeries;
    }

    @DataBoundSetter
    public void setPropertiesSeries(List<PropertiesSeries> propertiesSeries) {
        this.propertiesSeries = propertiesSeries;
    }

    public List<XMLSeries> getXmlSeries() {
        return xmlSeries;
    }

    @DataBoundSetter
    public void setXmlSeries(List<XMLSeries> xmlSeries) {
        this.xmlSeries = xmlSeries;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher,
                        TaskListener listener) {
        List<Plot> plots = new ArrayList<>();
        Plot plot = new Plot(title, yaxis, group, numBuilds, csvFileName, style,
                useDescr, keepRecords, exclZero, logarithmic,
                yaxisMinimum, yaxisMaximum);

        List<Series> series = new ArrayList<>();
        if (csvSeries != null) {
            series.addAll(csvSeries);
        }
        if (xmlSeries != null) {
            series.addAll(xmlSeries);
        }
        if (propertiesSeries != null) {
            series.addAll(propertiesSeries);
        }

        plot.series = series;
        plot.addBuild(build, listener.getLogger(), workspace);
        plots.add(plot);
        PlotBuildAction buildAction = build.getAction(PlotBuildAction.class);
        if (buildAction == null) {
            build.addAction(new PlotBuildAction(build, plots));
        } else {
            buildAction.addPlots(plots);
        }
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor, you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link PlotBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    @Symbol("plot")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /*
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public String getCsvFileName() {
            return "plot-" + UUID.randomUUID().toString() + ".csv";
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Please set a group");
            }
            if (value.length() < 4) {
                return FormValidation.warning("Isn't the group too short?");
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable group is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.Plot_Publisher_DisplayName();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }
    }
}


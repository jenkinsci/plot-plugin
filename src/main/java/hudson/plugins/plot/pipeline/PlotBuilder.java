package hudson.plugins.plot.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.plot.CSVSeries;
import hudson.plugins.plot.Messages;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.PropertiesSeries;
import hudson.plugins.plot.Series;
import hudson.plugins.plot.XMLSeries;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
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

    private final String group;
    private final String title;
    private final String numBuilds;
    private final String yaxis;
    private final String style;
    private final Boolean useDescr;
    private final Boolean exclZero;
    private final Boolean logarithmic;
    private final Boolean keepRecords;
    private final String yaxisMinimum;
    private final String yaxisMaximum;
    public String csvFileName;
    /** List of data series. */
    public List<Series> series;
    public List<CSVSeries> csvSeries;
    public List<PropertiesSeries> propertiesSeries;
    public List<XMLSeries> xmlSeries;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PlotBuilder(String group, String title, String numBuilds, String yaxis, String style,
            Boolean useDescr, Boolean exclZero, Boolean logarithmic, Boolean keepRecords,
            String yaxisMinimum, String yaxisMaximum, String csvFileName,
            List<CSVSeries> csvSeries, List<PropertiesSeries> propertiesSeries,
            List<XMLSeries> xmlSeries) {
        this.group = group;
        this.title = title;
        this.numBuilds = numBuilds;
        this.yaxis = yaxis;
        this.style = style;
        this.useDescr = useDescr;
        this.exclZero = exclZero;
        this.logarithmic = logarithmic;
        this.keepRecords = keepRecords;
        this.yaxisMinimum = yaxisMinimum;
        this.yaxisMaximum = yaxisMaximum;
        this.csvFileName = csvFileName;
        this.csvSeries = csvSeries;
        this.propertiesSeries = propertiesSeries;
        this.xmlSeries = xmlSeries;
        this.series = new ArrayList<>();
        if (csvSeries != null) {
            this.series.addAll(csvSeries);
        }
        if (xmlSeries != null) {
            this.series.addAll(xmlSeries);
        }
        if (propertiesSeries != null) {
            this.series.addAll(propertiesSeries);
        }
    }

    public String getGroup() {
        return group;
    }

    public String getTitle() {
        return title;
    }

    public String getNumBuilds() {
        return numBuilds;
    }

    public String getYaxis() {
        return yaxis;
    }

    public String getStyle() {
        return style;
    }

    public Boolean getUseDescr() {
        return useDescr;
    }

    public Boolean getExclZero() {
        return exclZero;
    }

    public Boolean getLogarithmic() {
        return logarithmic;
    }

    public Boolean getKeepRecords() {
        return keepRecords;
    }

    public String getYaxisMinimum() {
        return yaxisMinimum;
    }

    public String getYaxisMaximum() {
        return yaxisMaximum;
    }

    public List<Series> getSeries() {
        return series;
    }

    public List<CSVSeries> getCsvSeries() {
        return csvSeries;
    }

    public List<PropertiesSeries> getPropertiesSeries() {
        return propertiesSeries;
    }

    public List<XMLSeries> getXmlSeries() {
        return xmlSeries;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher,
            TaskListener listener) {
        List<Plot> plots = new ArrayList<>();
        Plot plot = new Plot(title, yaxis, group, numBuilds, csvFileName, style,
                false, false, false, false, yaxisMinimum, yaxisMaximum);
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
            return "plot-" + String.valueOf((int) Math.round(Math.random() * 100000000)) + ".csv";
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set a group");
            }
            if (value.length() < 4) {
                return FormValidation.warning("Isn't the group too short?");
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return false;
        }

        /**
         * This human readable group is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.Plot_Pipeline_Builder_DisplayName();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }
    }
}


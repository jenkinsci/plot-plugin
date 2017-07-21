package hudson.plugins.plot.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.plugins.plot.Messages;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.SeriesFactory;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * The Descriptor for the plotpipeline configuration Extension
 *
 * @author Nigel Daley
 * @author Thomas Fox
 *
 */
@Extension
public class PlotDescriptor extends BuildStepDescriptor<Publisher> {

    /**
     * Standard Constructor.
     */
    public PlotDescriptor() {
        super(PlotPublisher.class);
    }

    public String getDisplayName() {
        return Messages.Plot_Publisher_DisplayName();
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return AbstractProject.class.isAssignableFrom(jobType)
                && !MatrixProject.class.isAssignableFrom(jobType);
    }

    /**
     * Called when the user saves the project configuration.
     */
    @Override
    public Publisher newInstance(StaplerRequest req, JSONObject formData)
            throws FormException {
        PlotPublisher publisher = new PlotPublisher();
        for (Object data : SeriesFactory.getArray(formData.get("plots"))) {
            publisher.addPlot(bindPlot((JSONObject) data, req));
        }
        return publisher;
    }

    private static Plot bindPlot(JSONObject data, StaplerRequest req) {
        Plot p = req.bindJSON(Plot.class, data);
        p.series = SeriesFactory.createSeriesList(data.get("series"), req);
        return p;
    }

    /**
     * Checks if the series file is valid.
     */
    public FormValidation doCheckSeriesFile(
            @AncestorInPath Job<?, ?> project,
            @QueryParameter String value) throws IOException {
        FilePath fp = new FilePath( new FilePath( project.getRootDir() ), "workspace" );
        //Check if workspace folder is missing form root directory
        if ( fp.validateFileMask( value ) == null ) {
            return new FilePath( project.getRootDir() ).validateFileMask( value );
        }
         return fp.validateFileMask( value );
    }
}

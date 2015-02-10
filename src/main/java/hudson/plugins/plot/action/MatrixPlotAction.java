package hudson.plugins.plot.action;

import hudson.matrix.MatrixConfiguration;
import hudson.model.Action;
import hudson.model.AbstractProject;
import hudson.plugins.plot.Messages;
import hudson.plugins.plot.PlotReport;
import hudson.plugins.plot.publisher.MatrixPlotPublisher;

import java.io.IOException;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author lucinka
 */
public class MatrixPlotAction implements Action, StaplerProxy {

    private MatrixConfiguration project;
    private MatrixPlotPublisher publisher;

    public MatrixPlotAction(MatrixConfiguration project,
            MatrixPlotPublisher publisher) {
        this.project = project;
        this.publisher = publisher;
    }

    public AbstractProject<?, ?> getProject() {
        return project;
    }

    // called from PlotAction/index.jelly
    public boolean hasPlots() throws IOException {
        return CollectionUtils.isNotEmpty(publisher.getPlots(project));
    }

    // called from PlotAction/index.jelly
    public List<String> getOriginalGroups() {
        return publisher.getOriginalGroups(project);
    }

    // called from PlotAction/index.jelly
    public String getUrlGroup(String originalGroup) {
        return publisher.originalGroupToUrlEncodedGroup(originalGroup);
    }

    // called from href created in PlotAction/index.jelly
    public PlotReport getDynamic(String group, StaplerRequest req,
            StaplerResponse rsp) throws IOException {
        return new PlotReport(project, publisher.urlGroupToOriginalGroup(
                getUrlGroup(group), project), publisher.getPlots(
                getUrlGroup(group), project));
    }

    /**
     * If there's only one plot category, simply display that category of
     * reports on this view.
     */
    public Object getTarget() {
        List<String> groups = getOriginalGroups();
        if (groups != null && groups.size() == 1) {
            return new PlotReport(project, groups.get(0), publisher.getPlots(
                    getUrlGroup(groups.get(0)), project));
        } else {
            return this;
        }
    }

    public String getDisplayName() {
        return Messages.Plot_Action_DisplayName();
    }

    public String getIconFileName() {
        return "graph.gif";
    }

    public String getUrlName() {
        return Messages.Plot_UrlName();
    }

}

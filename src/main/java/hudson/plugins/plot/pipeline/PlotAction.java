package hudson.plugins.plot.pipeline;

import hudson.model.Action;
import hudson.model.Job;
import hudson.plugins.plot.Messages;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.PlotReport;
import java.io.IOException;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Project action to display plots.
 *
 * Created by max on 2016-06-20.
 */
public class PlotAction implements Action, StaplerProxy {

    private final Job<?, ?> project;
    private PlotPublisher publisher;

    public PlotAction(Job<?, ?> job, List<Plot> plots){
        this.project = job;
        publisher = new PlotPublisher();
        if(plots != null) {
            publisher.setPlots(plots);
        }
    }

    @Override
    public String getIconFileName() {
        return "graph.gif";
    }

    @Override
    public String getDisplayName() {
        return Messages.Plot_Action_DisplayName();
    }

    @Override
    public String getUrlName() {
        return Messages.Plot_UrlName();
    }

    // called from pipeline/PlotAction/index.jelly
    public boolean hasPlots() throws IOException {
        return CollectionUtils.isNotEmpty(publisher.getPlots());
    }

    // called from pipeline/PlotReport/index.jelly
    public Job<?, ?> getProject() {
        return project;
    }

    // called from pipeline/PlotAction/index.jelly
    public List<String> getOriginalGroups() {
        return publisher.getOriginalGroups();
    }

    // called from pipeline/PlotAction/index.jelly
    public String getUrlGroup(String originalGroup) {
        return publisher.originalGroupToUrlEncodedGroup(originalGroup);
    }

    // called from href created in pipeline/PlotAction/index.jelly
    public PlotReport getDynamic(String group, StaplerRequest req,
                                 StaplerResponse rsp) throws IOException {
        return new PlotReport(project,
                publisher.urlGroupToOriginalGroup(getUrlGroup(group)),
                publisher.getPlots(getUrlGroup(group)));
    }

    /**
     * If there's only one plot category, simply display that category of
     * reports on this view.
     */
    public Object getTarget() {
        List<String> groups = getOriginalGroups();
        if (groups != null && groups.size() == 1) {
            return new PlotReport(project, groups.get(0),
                    publisher.getPlots(getUrlGroup(groups.get(0))));
        } else {
            return this;
        }
    }
}
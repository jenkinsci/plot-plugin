/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import org.apache.commons.collections.CollectionUtils;
import org.kohsuke.stapler.StaplerProxy;

import java.io.IOException;
import java.util.List;

/**
 * Project action to display plots.
 *
 * @author Nigel Daley
 */
public class PlotAction implements Action, StaplerProxy {

    private final Job<?, ?> project;
    private PlotPublisher publisher;

    public PlotAction(AbstractProject<?, ?> project, PlotPublisher publisher) {
        this.project = project;
        this.publisher = publisher;
    }

    public PlotAction(Job<?, ?> job, List<Plot> plots) {
        this.project = job;
        publisher = new PlotPublisher();
        if (plots != null) {
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

    // called from PlotAction/index.jelly
    public boolean hasPlots() throws IOException {
        return CollectionUtils.isNotEmpty(publisher.getPlots());
    }

    @Deprecated
    public AbstractProject<?, ?> getProject() {
        return project instanceof AbstractProject ? (AbstractProject<?, ?>) project : null;
    }

    // called from PlotAction/index.jelly
    public Job<?, ?> getJob() {
        return project;
    }

    // called from PlotAction/index.jelly
    public List<String> getOriginalGroups() {
        return publisher.getOriginalGroups();
    }

    // called from PlotAction/index.jelly
    public String getUrlGroup(String originalGroup) {
        return publisher.originalGroupToUrlEncodedGroup(originalGroup);
    }

    // called from href created in PlotAction/index.jelly
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

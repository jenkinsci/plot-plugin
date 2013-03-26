/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.model.Action;
import hudson.model.AbstractProject;

import java.io.IOException;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Project action to display plots.
 * 
 * @author Nigel Daley
 */
public class PlotAction implements Action, StaplerProxy {

    private static final Logger LOGGER = Logger.getLogger(PlotAction.class.getName());
    private final AbstractProject<?, ?> project;
    private final PlotPublisher publisher;

    public PlotAction(AbstractProject<?, ?> project, PlotPublisher publisher) {
        this.project = project;
        this.publisher = publisher;
    }

    public AbstractProject<?, ?> getProject() {
        return project;
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

    // called from PlotAction/index.jelly
    public boolean hasPlots() throws IOException {
        return publisher.getPlots().length != 0;
    }
    
    // called from PlotAction/index.jelly
    public String[] getOriginalGroups() {
    	return publisher.getOriginalGroups();
    }
    
    // called from PlotAction/index.jelly
    public String getUrlGroup(String originalGroup) {
    	return publisher.originalGroupToUrlEncodedGroup(originalGroup);
    } 
    
    // called from href created in PlotAction/index.jelly    
    public PlotReport getDynamic(String group, StaplerRequest req, 
    		StaplerResponse rsp) throws IOException 
    {
    	return new PlotReport(project, 
                       publisher.urlGroupToOriginalGroup(getUrlGroup(group)),
                       publisher.getPlots(getUrlGroup(group)));
    }
    
    /**
     * If there's only one plot category, simply display that 
     * category of reports on this view.
     */
    public Object getTarget() {
    	String[] groups = getOriginalGroups();
    	if (groups != null && groups.length == 1) {
    		return new PlotReport(project, groups[0], 
        			publisher.getPlots(getUrlGroup(groups[0])));
    	} else {
    		return this;
    	}
    }
}

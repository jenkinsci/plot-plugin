/*
 * Copyright (c) 2007 Yahoo! Inc.  All rights reserved.  
 * Copyrights licensed under the MIT License.
 */
package hudson.plugins.plot;

import hudson.FilePath;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.Action;
import hudson.model.AbstractProject;

import java.io.IOException;
import java.util.ArrayList;
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
    	ArrayList<PlotData> array = getPlotData(publisher.getPlots());
        return array.size() > 0;
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
    	ArrayList<PlotData> array = getPlotData(publisher.getPlots(getUrlGroup(group)));

    	return new PlotReport(project, 
                       publisher.urlGroupToOriginalGroup(getUrlGroup(group)),
                       array.toArray(new PlotData[]{}));
    }
    
    /**
     * If there's only one plot category, simply display that 
     * category of reports on this view.
     */
    public Object getTarget() {
    	String[] groups = getOriginalGroups();
    	if (groups != null && groups.length == 1) {
        	ArrayList<PlotData> array = getPlotData(publisher.getPlots(getUrlGroup(groups[0])));
    		if (array.size() > 0) {
    			return new PlotReport(project, groups[0],array.toArray(new PlotData[]{}));
    		}
    	}
		return this;
    }
    
    private ArrayList<PlotData> getPlotData(Plot[] plots) {
       	ArrayList<PlotData> array = new ArrayList<PlotData>();
    	for( Plot plot: plots) {
			if(project instanceof MatrixProject ) {
				for(MatrixConfiguration config: ((MatrixProject)project).getActiveConfigurations() ) {
					PlotData plotData = plot.getPlotData(project,new FilePath(((MatrixProject)project).getRootDirFor(config)));
					if(plotData != null) {
						array.add( plotData );
					}
				}
			} else {
				PlotData plotData = plot.getPlotData(project,new FilePath(project.getRootDir()));
				if(plotData != null) {
					array.add( plotData );
				}
			}    	
    	}
    	return array;
    }
}

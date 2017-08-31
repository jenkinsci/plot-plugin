package hudson.plugins.plot;

import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class PlotBuildAction extends InvisibleAction
        implements StaplerProxy, SimpleBuildStep.LastBuildAction {

    private Run<?, ?> run;
    private List<Plot> plots;

    PlotBuildAction(Run<?, ?> run, List<Plot> plots) {
        this.run = run;
        this.plots = plots;
    }

    @Override
    public synchronized Collection<? extends Action> getProjectActions() {
        return Collections.<Action>singleton(new PlotAction(run.getParent(), plots));
    }

    @Override
    public Object getTarget() {
        return null;
    }

    synchronized void addPlots(List<Plot> plots) {
        if (this.plots == null) {
            this.plots = new ArrayList<>();
        }
        this.plots.addAll(plots);
    }
}

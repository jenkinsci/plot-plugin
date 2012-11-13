package hudson.plugins.plot;


import hudson.Extension;
import hudson.Util;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author lucinka
 */
public class AbstractPlotPublisher extends Recorder {
    
    /**
     * Converts the original plot group name to a URL friendly group name.
     */
    public String originalGroupToUrlEncodedGroup(String originalGroup) {
        return Util.rawEncode(originalGroupToUrlGroup(originalGroup));
    }

    protected String originalGroupToUrlGroup(String originalGroup) {
        if (originalGroup == null || "".equals(originalGroup)) {
            return "nogroup";
        }
        return originalGroup.replace('/', ' ');
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }
    
}

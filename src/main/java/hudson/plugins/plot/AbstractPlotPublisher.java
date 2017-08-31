package hudson.plugins.plot;

import hudson.Util;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import org.apache.commons.lang.StringUtils;

/**
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
        if (StringUtils.isEmpty(originalGroup)) {
            return "nogroup";
        }
        return originalGroup.replace('/', ' ');
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }
}

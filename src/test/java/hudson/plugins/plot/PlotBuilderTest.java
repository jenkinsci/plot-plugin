package hudson.plugins.plot;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PlotBuilderTest {

    @Test
    void testWithMinimalPipelineArgs(JenkinsRule r) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition("""
                        node { \s
                            plot csvFileName: 'plot-minimal.csv',
                               group: 'My Data',
                               style: 'line'
                        }""", true));
        r.buildAndAssertSuccess(p);
    }

    @Test
    void testWithXML(JenkinsRule r) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition("""
                        node { \s
                            def content = '<my_data>'
                            content += '<test value1="123.456"/>'
                            content += '<test value2="321.654"/>'
                            content += '</my_data>'
                            writeFile file: 'data.xml', text: content
                            plot csvFileName: 'plot-xml.csv',
                               group: 'My Data',
                               title: 'Useful Title',
                               style: 'line',
                               yaxis: 'arbitrary',
                               xmlSeries: [
                                 [file: 'data.xml',
                                  nodeType: 'NODESET',
                                  xpath: 'my_data/test/@*']
                               ]
                        }""", true));
        r.buildAndAssertSuccess(p);
    }

    @Test
    void testWithCSV(JenkinsRule r) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition("""
                        node { \s
                            def content = 'Avg,Median,90,min,max,samples,errors,error %'
                            content += '515.33,196,1117,2,16550,97560,360,0.37'
                            writeFile file: 'data.csv', text: content
                            plot csvFileName: 'plot-csv.csv',
                               group: 'My Data',
                               title: 'Useful Title',
                               style: 'line',
                               yaxis: 'arbitrary',
                               csvSeries: [[file: 'data.csv']]
                        }""", true));
        r.buildAndAssertSuccess(p);
    }

    @Test
    void testWithProperties(JenkinsRule r) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "projectUnderTest");
        p.setDefinition(new CpsFlowDefinition("""
                        node { \s
                            def content = 'YVALUE=1'
                            writeFile file: 'data.properties', text: content
                            plot csvFileName: 'plot-properties.csv',
                               group: 'My Data',
                               title: 'Useful Title',
                               style: 'line',
                               yaxis: 'arbitrary',
                               propertiesSeries: [
                                 [file: 'data.properties',
                                  label: 'My Label']
                               ]
                        }""", true));
        r.buildAndAssertSuccess(p);
    }
}

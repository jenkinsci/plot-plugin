package hudson.plugins.plot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PlotBuildActionTest {

    private PlotBuildAction plotBuildAction;

    @BeforeEach
    void setUp(JenkinsRule r) throws Exception {
        final Run<?, ?> run = r.buildAndAssertSuccess(r.createFreeStyleProject());
        final List<Plot> plots = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Plot p = new Plot();
            p.title = String.valueOf(i);
            plots.add(p);
        }
        plotBuildAction = new PlotBuildAction(run, plots);
    }

    @Issue("JENKINS-48465")
    @Test
    void checksNoConcurrentModificationExceptionIsThrownForPlotsListAccess() throws Exception {
        int tasksCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<FutureTask<Object>> tasks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(tasksCount);

        simulateConcurrentModificationException(executorService, tasksCount, tasks, latch);

        waitForAllThreadsToFinish(executorService, latch);
        assertNoConcurrentModificationExceptionThrown(tasks);
    }

    private void simulateConcurrentModificationException(
            ExecutorService executorService,
            int tasksCount,
            List<FutureTask<Object>> tasks,
            final CountDownLatch latch) {
        for (int i = 0; i < tasksCount; i++) {
            FutureTask<Object> task = new FutureTask<>(() -> {
                try {
                    Thread.sleep(new Random().nextInt(100));
                    // using PureJavaReflectionProvider just because it's used in Jenkins
                    // close to "real world"
                    PureJavaReflectionProvider provider = new PureJavaReflectionProvider();
                    provider.visitSerializableFields(plotBuildAction, (fieldName, fieldType, definedIn, value) -> {
                        if (value instanceof List) {
                            List<Plot> plots = (List<Plot>) value;
                            // simulate ConcurrentModificationException
                            for (Plot p : plots) {
                                if (!plots.isEmpty()) {
                                    plots.remove(p);
                                }
                            }
                        }
                    });
                } finally {
                    latch.countDown();
                }
                return null;
            });
            tasks.add(task);
            executorService.submit(task);
        }
    }

    private static void waitForAllThreadsToFinish(ExecutorService executorService, CountDownLatch latch)
            throws Exception {
        latch.await();
        executorService.shutdown();
    }

    private static void assertNoConcurrentModificationExceptionThrown(List<FutureTask<Object>> tasks) {
        assertDoesNotThrow(
                () -> {
                    // we expect here no ConcurrentModificationException
                    // otherwise access to plots list is not synchronized
                    for (FutureTask<?> task : tasks) {
                        task.get();
                    }
                },
                "Access to PlotBuildAction#plots list is not synchronized");
    }
}

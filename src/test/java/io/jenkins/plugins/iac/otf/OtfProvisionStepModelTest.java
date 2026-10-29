package io.jenkins.plugins.iac.otf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** How Pipeline (and the Snippet Generator) sees the step's parameters. */
@WithJenkins
class OtfProvisionStepModelTest {
 private final DescribableModel<OtfProvisionStep> model=DescribableModel.of(OtfProvisionStep.class);

 @Test void onlyServerIsRequired(){
  assertTrue(model.getParameter("server").isRequired());
  assertFalse(model.getParameter("workspaceId").isRequired());
 }
 @Test void workspaceIdRoundTrips(JenkinsRule j) throws Exception {
  OtfProvisionStep step=model.instantiate(Map.of("server","otf","workspaceId","ws-1","mode","apply"));
  assertEquals("ws-1",step.getWorkspaceId());
  Map<String,?> args=DescribableModel.uninstantiate2_(step).getArguments();
  assertEquals("otf",args.get("server"));
  assertEquals("ws-1",args.get("workspaceId"));
  assertEquals("apply",args.get("mode"));
 }
 @Test void snippetShowsWorkspaceId(JenkinsRule j){
  OtfProvisionStep step=new OtfProvisionStep("otf");step.setWorkspaceId("ws-1");
  String snippet=Snippetizer.object2Groovy(step);
  assertTrue(snippet.startsWith("otfProvision(server: 'otf', workspaceId: 'ws-1')"),snippet);
 }
 @Test void javaConvenienceConstructorSetsWorkspaceId(){
  assertEquals("ws-1",new OtfProvisionStep("otf","ws-1").getWorkspaceId());
  assertNull(new OtfProvisionStep("otf").getWorkspaceId());
 }
}

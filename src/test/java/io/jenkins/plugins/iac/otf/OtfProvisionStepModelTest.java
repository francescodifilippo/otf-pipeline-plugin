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
  for(String name:new String[]{"workspaceId","organization","workspaceName"})assertFalse(model.getParameter(name).isRequired(),name);
 }
 @Test void workspaceIdRoundTrips(JenkinsRule j) throws Exception {
  OtfProvisionStep step=model.instantiate(Map.of("server","otf","workspaceId","ws-1","mode","apply"));
  assertEquals("ws-1",step.getWorkspaceId());
  Map<String,?> args=DescribableModel.uninstantiate2_(step).getArguments();
  assertEquals("otf",args.get("server"));
  assertEquals("ws-1",args.get("workspaceId"));
  assertEquals("apply",args.get("mode"));
  assertFalse(args.containsKey("organization"));
  assertFalse(args.containsKey("workspaceName"));
 }
 @Test void workspaceNameRoundTrips(JenkinsRule j) throws Exception {
  OtfProvisionStep step=model.instantiate(Map.of("server","otf","organization","acme","workspaceName","network.prod"));
  assertNull(step.getWorkspaceId());
  assertEquals(Map.of("mode","plan","organization","acme","workspaceName","network.prod"),step.parameters());
  Map<String,?> args=DescribableModel.uninstantiate2_(step).getArguments();
  assertEquals("acme",args.get("organization"));
  assertEquals("network.prod",args.get("workspaceName"));
  assertFalse(args.containsKey("workspaceId"));
 }
 @Test void snippetShowsWorkspaceId(JenkinsRule j){
  OtfProvisionStep step=new OtfProvisionStep("otf");step.setWorkspaceId("ws-1");
  String snippet=Snippetizer.object2Groovy(step);
  assertTrue(snippet.startsWith("otfProvision(server: 'otf', workspaceId: 'ws-1')"),snippet);
 }
 @Test void snippetShowsOnlyTheConfiguredWorkspaceParameters(JenkinsRule j){
  OtfProvisionStep step=new OtfProvisionStep("otf");step.setOrganization("acme");step.setWorkspaceName("network");
  String snippet=Snippetizer.object2Groovy(step);
  assertTrue(snippet.contains("organization: 'acme'")&&snippet.contains("workspaceName: 'network'"),snippet);
  assertFalse(snippet.contains("workspaceId"),snippet);
 }
 @Test void workspaceIdKeepsItsParametersAndDefaultKey(){
  OtfProvisionStep step=new OtfProvisionStep("otf","ws-1");
  assertEquals(Map.of("mode","plan"),step.parameters());
 }
 @Test void blankNamesAreUnset(){
  OtfProvisionStep step=new OtfProvisionStep("otf","ws-1");step.setOrganization(" ");step.setWorkspaceName("");
  assertNull(step.getOrganization());
  assertNull(step.getWorkspaceName());
 }
 @Test void namesFollowOtfRules(){
  OtfProvisionStep step=new OtfProvisionStep("otf");
  step.setOrganization("_acme-2");step.setWorkspaceName("-net_work.prod-1");
  assertEquals("_acme-2",step.getOrganization());
  assertEquals("-net_work.prod-1",step.getWorkspaceName());
  for(String bad:new String[]{"ac.me","ac me","acme/x","acmé","a\nb","x".repeat(256)})
   assertThrows(IllegalArgumentException.class,()->step.setOrganization(bad),bad);
  for(String bad:new String[]{"net work","net/work","net%2Fwork","a\tb","ws?x","x".repeat(256)})
   assertThrows(IllegalArgumentException.class,()->step.setWorkspaceName(bad),bad);
  for(String dots:new String[]{".",".."})
   assertEquals("workspaceName '"+dots+"' cannot be looked up by name; use workspaceId",
     assertThrows(IllegalArgumentException.class,()->step.setWorkspaceName(dots)).getMessage());
 }
 @Test void javaConvenienceConstructorSetsWorkspaceId(){
  assertEquals("ws-1",new OtfProvisionStep("otf","ws-1").getWorkspaceId());
  assertNull(new OtfProvisionStep("otf").getWorkspaceId());
 }
}

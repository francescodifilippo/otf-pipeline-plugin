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
  assertTrue(model.getParameter("connection").isRequired());
  for(String name:new String[]{"workspaceId","organizationName","workspaceName"})assertFalse(model.getParameter(name).isRequired(),name);
 }
 @Test void workspaceIdRoundTrips(JenkinsRule j) throws Exception {
  OtfProvisionStep step=model.instantiate(Map.of("connection","otf","workspaceId","ws-1","mode","apply"));
  assertEquals("ws-1",step.getWorkspaceId());
  Map<String,?> args=DescribableModel.uninstantiate2_(step).getArguments();
  assertEquals("otf",args.get("connection"));
  assertEquals("ws-1",args.get("workspaceId"));
  assertEquals("apply",args.get("mode"));
  assertFalse(args.containsKey("organizationName"));
  assertFalse(args.containsKey("workspaceName"));
 }
 @Test void workspaceNameRoundTrips(JenkinsRule j) throws Exception {
  OtfProvisionStep step=model.instantiate(Map.of("connection","otf","organizationName","acme","workspaceName","network.prod"));
  assertNull(step.getWorkspaceId());
  assertEquals(Map.of("mode","plan","organizationName","acme","workspaceName","network.prod"),step.parameters());
  Map<String,?> args=DescribableModel.uninstantiate2_(step).getArguments();
  assertEquals("acme",args.get("organizationName"));
  assertEquals("network.prod",args.get("workspaceName"));
  assertFalse(args.containsKey("workspaceId"));
 }
 @Test void snippetShowsWorkspaceId(JenkinsRule j){
  OtfProvisionStep step=new OtfProvisionStep("otf");step.setWorkspaceId("ws-1");
  String snippet=Snippetizer.object2Groovy(step);
  assertTrue(snippet.startsWith("otfProvision(connection: 'otf', workspaceId: 'ws-1')"),snippet);
 }
 @Test void snippetShowsOnlyTheConfiguredWorkspaceParameters(JenkinsRule j){
  OtfProvisionStep step=new OtfProvisionStep("otf");step.setOrganizationName("acme");step.setWorkspaceName("network");
  String snippet=Snippetizer.object2Groovy(step);
  assertTrue(snippet.contains("organizationName: 'acme'")&&snippet.contains("workspaceName: 'network'"),snippet);
  assertFalse(snippet.contains("workspaceId"),snippet);
 }
 @Test void workspaceIdKeepsItsParametersAndDefaultKey(){
  OtfProvisionStep step=new OtfProvisionStep("otf","ws-1");
  assertEquals(Map.of("mode","plan"),step.parameters());
 }
 @Test void blankNamesAreUnset(){
  OtfProvisionStep step=new OtfProvisionStep("otf","ws-1");step.setOrganizationName(" ");step.setWorkspaceName("");
  assertNull(step.getOrganizationName());
  assertNull(step.getWorkspaceName());
 }
 @Test void namesFollowOtfRules(){
  OtfProvisionStep step=new OtfProvisionStep("otf");
  step.setOrganizationName("_acme-2");step.setWorkspaceName("-net_work.prod-1");
  assertEquals("_acme-2",step.getOrganizationName());
  assertEquals("-net_work.prod-1",step.getWorkspaceName());
  for(String bad:new String[]{"ac.me","ac me","acme/x","acmé","a\nb","x".repeat(256)})
   assertThrows(IllegalArgumentException.class,()->step.setOrganizationName(bad),bad);
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

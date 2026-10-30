package io.jenkins.plugins.iac.otf;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** How Pipeline (and the Snippet Generator) sees the otfWorkspace parameters. */
@WithJenkins
class OtfWorkspaceStepModelTest {
 private final DescribableModel<OtfWorkspaceStep> model=DescribableModel.of(OtfWorkspaceStep.class);

 @Test void connectionOrganizationAndWorkspaceAreRequired(){
  for(String name:new String[]{"connection","organizationName","workspaceName"})assertTrue(model.getParameter(name).isRequired(),name);
  for(String name:new String[]{"vcsProviderId","variables","sensitiveVariables","ifExists"})assertFalse(model.getParameter(name).isRequired(),name);
 }
 @Test void snippetShowsTheConfiguredParameters(JenkinsRule j){
  OtfWorkspaceStep step=new OtfWorkspaceStep("otf","acme","network");
  Map<String,Object> variables=new LinkedHashMap<>();variables.put("region","eu-west-1");variables.put("count",3);
  step.setVariables(variables);
  step.setSensitiveVariables(Map.of("db_password","db-password"));
  String snippet=Snippetizer.object2Groovy(step);
  assertTrue(snippet.startsWith("otfWorkspace(connection: 'otf', organizationName: 'acme', sensitiveVariables: [db_password: 'db-password'], "+
    "variables: [region: 'eu-west-1', count: '3'], workspaceName: 'network')"),snippet);
 }
 @Test void blankNamesAreRejected(){
  assertEquals("organizationName is required in the otfWorkspace stage options",
    assertThrows(IllegalArgumentException.class,()->new OtfWorkspaceStep("otf"," ","network")).getMessage());
  assertEquals("workspaceName is required in the otfWorkspace stage options",
    assertThrows(IllegalArgumentException.class,()->new OtfWorkspaceStep("otf","acme",null)).getMessage());
 }
 @Test void unsetOptionsAreLeftOutOfTheRequest(){
  OtfWorkspaceStep step=new OtfWorkspaceStep("otf","acme","network");
  assertEquals(0,step.spec().attributes().size());
  step.setVcsProviderId("vcs-1");step.setRepository("acme/network");
  assertEquals("{\"vcs-repo\":{\"oauth-token-id\":\"vcs-1\",\"identifier\":\"acme/network\"}}",step.spec().attributes().toString());
 }
}

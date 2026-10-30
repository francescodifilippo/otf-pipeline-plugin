package io.jenkins.plugins.iac.otf;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.util.Secret;
import io.jenkins.plugins.iac.core.Connection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

/**
 * otfWorkspace when Jenkins restarts while a request is open: the stub stores the workspace or variable and never
 * answers, as if the response were lost, and the resumed build continues from what the execution saved.
 */
class OtfWorkspaceRestartTest {
 private static final String STAGE_OPTIONS="variables: [region: 'eu-west-1'], environmentVariables: [TF_LOG: 'INFO'], "+
   "sensitiveVariables: [db_password: 'db-password']";
 private static final List<String> VARIABLES=List.of("terraform:region","env:TF_LOG","terraform:db_password");
 @RegisterExtension
 private final JenkinsSessionExtension sessions=new JenkinsSessionExtension();
 private OtfStub otf;

 @BeforeEach void startStub() throws Exception {otf=new OtfStub();}
 @AfterEach void stopStub(){otf.close();}

 @Test void aRestartDuringTheCreateRequestKeepsTheCreatedWorkspace() throws Throwable {
  otf.holdCreate=true;
  sessions.then(j -> startAndRestartWhenHeld(j));
  sessions.then(j -> {
   WorkflowRun b=resumed(j);
   assertEquals(1,otf.creates.size());
   j.assertLogContains("found workspace 'network' in organization 'acme' created before the restart: ws-new1",b);
   assertEquals(VARIABLES,otf.vars.get("ws-new1"));
  });
 }

 @Test void aRestartWhileSettingVariablesAddsOnlyTheMissingOnes() throws Throwable {
  otf.holdVariable="TF_LOG";
  sessions.then(j -> startAndRestartWhenHeld(j));
  sessions.then(j -> {
   WorkflowRun b=resumed(j);
   assertEquals(1,otf.creates.size());
   j.assertLogNotContains("created before the restart",b);
   assertEquals(VARIABLES,otf.vars.get("ws-new1"));
   assertEquals(3,otf.varCreates.size());
  });
 }

 private void startAndRestartWhenHeld(JenkinsRule j) throws Exception {
  SystemCredentialsProvider.getInstance().getCredentials().add(
    new StringCredentialsImpl(CredentialsScope.GLOBAL,"otf-token","OTF token",Secret.fromString("not-a-real-token")));
  SystemCredentialsProvider.getInstance().getCredentials().add(
    new StringCredentialsImpl(CredentialsScope.GLOBAL,"db-password","DB password",Secret.fromString("db-password-value")));
  SystemCredentialsProvider.getInstance().save();
  Connection c=new Connection("otf-test",otf.url(),"otf-token");
  c.setAllowHttp(true);
  OtfConnections.get().setConnections(List.of(c));
  WorkflowJob p=j.createProject(WorkflowJob.class,"workspace");
  p.setDefinition(new CpsFlowDefinition("pipeline {\n agent none\n stages {\n stage('workspace') {\n options { otfWorkspace("+
    "connection: 'otf-test', organizationName: 'acme', workspaceName: 'network', "+STAGE_OPTIONS+") }\n"+
    " steps { echo 'workspace body' }\n }\n }\n}",true));
  p.scheduleBuild2(0).waitForStart();
  assertTrue(otf.held.await(1,TimeUnit.MINUTES),"no request was held");
 }
 /** The build of the first session, completed after the restart, with the body run once. */
 private static WorkflowRun resumed(JenkinsRule j) throws Exception {
  WorkflowRun b=j.jenkins.getItemByFullName("workspace",WorkflowJob.class).getBuildByNumber(1);
  j.assertBuildStatusSuccess(j.waitForCompletion(b));
  String log=JenkinsRule.getLog(b);
  assertTrue(log.contains("Resuming build"),log);
  assertEquals(1,log.split(Pattern.quote("workspace body"),-1).length-1,log);
  return b;
 }
}

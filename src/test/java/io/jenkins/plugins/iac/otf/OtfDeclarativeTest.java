package io.jenkins.plugins.iac.otf;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hudson.model.Result;
import hudson.util.Secret;
import io.jenkins.plugins.iac.core.Connection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** Declarative stage options against a stub OTF API, with agent none. */
@WithJenkins
class OtfDeclarativeTest {
 private JenkinsRule j;
 private HttpServer otf;
 private final List<String> submits=new CopyOnWriteArrayList<>();
 private final Queue<String> statuses=new ConcurrentLinkedQueue<>();
 private final List<String> lookups=new CopyOnWriteArrayList<>();
 /** Workspaces served by name: acme/network is ws-net, acme/db.prod is ws-db, acme/secret answers 403. */
 private static final Map<String,String> WORKSPACES=Map.of("/api/v2/organizations/acme/workspaces/network","ws-net",
   "/api/v2/organizations/acme/workspaces/db.prod","ws-db");

 @BeforeEach void setUp(JenkinsRule rule) throws Exception {
  j=rule;
  otf=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  otf.createContext("/api/v2/runs",exchange -> {
   submits.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   respond(exchange,201,"pending");
  });
  otf.createContext("/api/v2/runs/run-1",exchange -> {
   String next=statuses.poll();
   respond(exchange,200,next==null?"applying":next);
  });
  otf.createContext("/api/v2/organizations/",exchange -> {
   String path=exchange.getRequestURI().getRawPath();
   lookups.add(exchange.getRequestMethod()+" "+path);
   String id=WORKSPACES.get(path);
   int code=id!=null?200:path.endsWith("/secret")?403:404;
   byte[] body=(id==null?"{\"errors\":[{\"status\":\""+code+"\"}]}":
     "{\"data\":{\"id\":\""+id+"\",\"type\":\"workspaces\",\"attributes\":{\"name\":\""+path.substring(path.lastIndexOf('/')+1)+"\"}}}")
     .getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/vnd.api+json");
   exchange.sendResponseHeaders(code,body.length);
   try(OutputStream o=exchange.getResponseBody()){o.write(body);}
  });
  otf.start();
  SystemCredentialsProvider.getInstance().getCredentials().add(
    new StringCredentialsImpl(CredentialsScope.GLOBAL,"otf-token","OTF token",Secret.fromString("not-a-real-token")));
  SystemCredentialsProvider.getInstance().save();
  Connection c=new Connection("otf-test","http://127.0.0.1:"+otf.getAddress().getPort(),"otf-token");
  c.setAllowHttp(true);
  OtfConnections.get().setConnections(List.of(c));
 }

 @AfterEach void tearDown(){otf.stop(0);}

 @Test void asyncApplyReportsApprovalPendingThenAwaits() throws Exception {
  statuses.add("planned");statuses.add("applied");
  WorkflowRun b=j.buildAndAssertSuccess(pipeline(
    "stage('apply') {\n options {\n otfProvision(connection: 'otf-test', workspaceId: 'ws-1', mode: 'apply', operationKey: 'deploy', waitForCompletion: false)\n }\n"+
    " steps {\n echo 'submitted'\n }\n}\n"+
    "stage('await') {\n options {\n otfAwait(connection: 'otf-test', operationKey: 'deploy', pollingSeconds: 5)\n }\n"+
    " steps {\n echo 'awaited'\n }\n}"));
  assertEquals(1,submits.size());
  assertTrue(submits.get(0).contains("\"plan-only\":false"),submits.get(0));
  j.assertLogContains("submitted",b);
  j.assertLogContains("approval pending for ID run-1",b);
  j.assertLogContains("completed ID run-1 with status applied",b);
  j.assertLogContains("awaited",b);
 }

 @Test void discardedRunFailsTheStage() throws Exception {
  statuses.add("discarded");
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(
    "stage('apply') {\n options {\n otfProvision(connection: 'otf-test', workspaceId: 'ws-1', mode: 'apply', pollingSeconds: 5)\n }\n"+
    " steps {\n echo 'applied body'\n }\n}"));
  j.assertLogContains("Remote operation run-1 failed: discarded",b);
  j.assertLogNotContains("applied body",b);
 }

 @Test void workspaceIdMakesNoLookup() throws Exception {
  statuses.add("planned_and_finished");
  WorkflowRun b=j.buildAndAssertSuccess(pipeline(
    "stage('plan') {\n options {\n otfProvision(connection: 'otf-test', workspaceId: 'ws-1', pollingSeconds: 5)\n }\n steps {\n echo 'plan body'\n }\n}"));
  assertEquals(List.of(),lookups);
  assertTrue(submits.get(0).contains("\"id\":\"ws-1\""),submits.get(0));
  j.assertLogNotContains("resolved target",b);
  j.assertLogContains("plan body",b);
 }

 @Test void workspaceNameIsResolvedOnceThenAwaited() throws Exception {
  statuses.add("planned");statuses.add("applied");
  WorkflowRun b=j.buildAndAssertSuccess(pipeline(
    "stage('apply') {\n options {\n otfProvision(connection: 'otf-test', organizationName: 'acme', workspaceName: 'db.prod', mode: 'apply', operationKey: 'deploy', waitForCompletion: false)\n }\n"+
    " steps {\n echo 'submitted'\n }\n}\n"+
    "stage('await') {\n options {\n otfAwait(connection: 'otf-test', operationKey: 'deploy', pollingSeconds: 5)\n }\n"+
    " steps {\n echo 'awaited'\n }\n}"));
  assertEquals(List.of("GET /api/v2/organizations/acme/workspaces/db.prod"),lookups);
  assertEquals(1,submits.size());
  assertTrue(submits.get(0).contains("\"id\":\"ws-db\""),submits.get(0));
  j.assertLogContains("IaC otf: resolved target to ws-db",b);
  j.assertLogContains("submitting target ws-db with operationKey deploy",b);
  j.assertLogContains("completed ID run-1 with status applied",b);
  j.assertLogContains("awaited",b);
 }

 @Test void stagesNamingDifferentWorkspacesGetDifferentDefaultKeys() throws Exception {
  statuses.add("planned_and_finished");statuses.add("planned_and_finished");
  j.buildAndAssertSuccess(pipeline(byName("network")+"\n"+byName("db.prod")));
  assertEquals(2,lookups.size());
  assertEquals(2,submits.size());
  assertTrue(submits.get(0).contains("\"id\":\"ws-net\""),submits.get(0));
  assertTrue(submits.get(1).contains("\"id\":\"ws-db\""),submits.get(1));
 }

 @Test void unknownWorkspaceNameFailsBeforeSubmitting() throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(byName("Network")));
  j.assertLogContains("No OTF workspace named 'Network' in organization 'acme'; check organizationName and workspaceName, or use workspaceId",b);
  assertEquals(1,lookups.size());
  assertEquals(0,submits.size());
  j.assertLogNotContains("submitting target",b);
  j.assertLogNotContains("plan body",b);
 }

 @Test void forbiddenLookupFailsWithTheHttpStatus() throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(byName("secret")));
  j.assertLogContains("IaC API HTTP 403 for GET /api/v2/organizations/acme/workspaces/secret",b);
  assertEquals(1,lookups.size());
  assertEquals(0,submits.size());
  j.assertLogNotContains("plan body",b);
 }

 @Test void workspaceIdOrWorkspaceNameIsRequired() throws Exception {
  assertRejected("connection: 'otf-test'","otfProvision needs workspaceId, or workspaceName with organizationName, in the stage options");
 }
 @Test void workspaceIdAndWorkspaceNameAreExclusive() throws Exception {
  assertRejected("connection: 'otf-test', workspaceId: 'ws-1', organizationName: 'acme', workspaceName: 'network'",
    "Use either workspaceId or workspaceName in the otfProvision stage options, not both");
 }
 @Test void workspaceNameNeedsOrganization() throws Exception {
  assertRejected("connection: 'otf-test', workspaceName: 'network'","workspaceName needs organizationName");
 }
 @Test void organizationIsRejectedWithWorkspaceId() throws Exception {
  assertRejected("connection: 'otf-test', workspaceId: 'ws-1', organizationName: 'acme'","organizationName is used only with workspaceName");
 }
 @Test void invalidWorkspaceNameIsRejected() throws Exception {
  assertRejected("connection: 'otf-test', organizationName: 'acme', workspaceName: 'net/work'",
    "workspaceName must contain 1..255 letters, numbers, hyphens, underscores or dots");
 }

 private void assertRejected(String args,String message) throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(
    "stage('plan') {\n options {\n otfProvision("+args+")\n }\n steps {\n echo 'plan body'\n }\n}"));
  j.assertLogContains(message,b);
  j.assertLogNotContains("submitting target",b);
  j.assertLogNotContains("plan body",b);
  assertEquals(0,submits.size());
  assertEquals(List.of(),lookups);
 }
 private static String byName(String name){
  return "stage('plan "+name+"') {\n options {\n otfProvision(connection: 'otf-test', organizationName: 'acme', workspaceName: '"+name+"', pollingSeconds: 5)\n }\n"+
    " steps {\n echo 'plan body'\n }\n}";
 }

 private WorkflowJob pipeline(String stages) throws Exception {
  WorkflowJob p=j.createProject(WorkflowJob.class);
  p.setDefinition(new CpsFlowDefinition("pipeline {\n agent none\n stages {\n"+stages+"\n }\n}",true));
  return p;
 }

 private static void respond(HttpExchange exchange,int code,String status) throws IOException {
  byte[] body=("{\"data\":{\"id\":\"run-1\",\"attributes\":{\"status\":\""+status+"\"}}}").getBytes(StandardCharsets.UTF_8);
  exchange.getResponseHeaders().set("Content-Type","application/vnd.api+json");
  exchange.sendResponseHeaders(code,body.length);
  try(OutputStream o=exchange.getResponseBody()){o.write(body);}
 }
}

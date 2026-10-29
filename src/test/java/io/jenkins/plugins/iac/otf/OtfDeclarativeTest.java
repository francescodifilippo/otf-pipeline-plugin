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
  otf.start();
  SystemCredentialsProvider.getInstance().getCredentials().add(
    new StringCredentialsImpl(CredentialsScope.GLOBAL,"otf-token","OTF token",Secret.fromString("not-a-real-token")));
  SystemCredentialsProvider.getInstance().save();
  Connection c=new Connection("otf-test","http://127.0.0.1:"+otf.getAddress().getPort(),"otf-token");
  c.setAllowHttp(true);
  OtfConnections.get().setServers(List.of(c));
 }

 @AfterEach void tearDown(){otf.stop(0);}

 @Test void asyncApplyReportsApprovalPendingThenAwaits() throws Exception {
  statuses.add("planned");statuses.add("applied");
  WorkflowRun b=j.buildAndAssertSuccess(pipeline(
    "stage('apply') {\n options {\n otfProvision(server: 'otf-test', workspaceId: 'ws-1', mode: 'apply', operationKey: 'deploy', waitForCompletion: false)\n }\n"+
    " steps {\n echo 'submitted'\n }\n}\n"+
    "stage('await') {\n options {\n otfAwait(server: 'otf-test', operationKey: 'deploy', pollingSeconds: 5)\n }\n"+
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
    "stage('apply') {\n options {\n otfProvision(server: 'otf-test', workspaceId: 'ws-1', mode: 'apply', pollingSeconds: 5)\n }\n"+
    " steps {\n echo 'applied body'\n }\n}"));
  j.assertLogContains("Remote operation run-1 failed: discarded",b);
  j.assertLogNotContains("applied body",b);
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

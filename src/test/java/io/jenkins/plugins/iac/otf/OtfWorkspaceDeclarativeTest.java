package io.jenkins.plugins.iac.otf;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hudson.model.Result;
import hudson.util.Secret;
import io.jenkins.plugins.iac.core.Connection;
import io.jenkins.plugins.iac.core.HttpJsonClient;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

/** otfWorkspace as a Declarative stage option against an in-memory stub of the OTF workspace and variable API. */
@WithJenkins
class OtfWorkspaceDeclarativeTest {
 private static final String SECRET_VALUE="db-password-value";
 private JenkinsRule j;
 private HttpServer otf;
 /** Workspaces of organization acme by name. */
 private final Map<String,String> workspaces=new ConcurrentHashMap<>();
 /** Variables by workspace ID, as "category:key". */
 private final Map<String,List<String>> vars=new ConcurrentHashMap<>();
 private final List<JsonNode> creates=new CopyOnWriteArrayList<>(),varCreates=new CopyOnWriteArrayList<>();
 private final List<String> runs=new CopyOnWriteArrayList<>();
 /** When set, the next workspace create answers 409, as if another build had just created this workspace. */
 private volatile String createdConcurrently;
 /** Variable key whose create answers 422. */
 private volatile String failingVariable;

 @BeforeEach void setUp(JenkinsRule rule) throws Exception {
  j=rule;
  otf=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  otf.createContext("/api/v2/organizations/acme/workspaces",this::workspaces);
  otf.createContext("/api/v2/workspaces/",this::variables);
  otf.createContext("/api/v2/runs",exchange -> {
   runs.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   respond(exchange,201,"{\"data\":{\"id\":\"run-1\",\"attributes\":{\"status\":\"planned_and_finished\"}}}");
  });
  otf.start();
  SystemCredentialsProvider.getInstance().getCredentials().add(
    new StringCredentialsImpl(CredentialsScope.GLOBAL,"otf-token","OTF token",Secret.fromString("not-a-real-token")));
  SystemCredentialsProvider.getInstance().getCredentials().add(
    new StringCredentialsImpl(CredentialsScope.GLOBAL,"db-password","DB password",Secret.fromString(SECRET_VALUE)));
  SystemCredentialsProvider.getInstance().save();
  Connection c=new Connection("otf-test","http://127.0.0.1:"+otf.getAddress().getPort(),"otf-token");
  c.setAllowHttp(true);
  OtfConnections.get().setConnections(List.of(c));
 }
 @AfterEach void tearDown(){otf.stop(0);}

 private void workspaces(HttpExchange exchange) throws IOException {
  String path=exchange.getRequestURI().getRawPath();
  if("POST".equals(exchange.getRequestMethod())){
   JsonNode body=HttpJsonClient.JSON.readTree(exchange.getRequestBody());
   creates.add(body);
   String name=body.path("data").path("attributes").path("name").asText();
   if(name.equals(createdConcurrently)){workspaces.put(name,"ws-other");createdConcurrently=null;}
   if(workspaces.containsKey(name)){respond(exchange,409,"{\"errors\":[{\"status\":\"409\"}]}");return;}
   String id="ws-new"+(workspaces.size()+1);
   workspaces.put(name,id);
   respond(exchange,201,workspace(id,name));
   return;
  }
  String name=path.substring(path.lastIndexOf('/')+1);
  String id=workspaces.get(name);
  if(id==null)respond(exchange,404,"{\"errors\":[{\"status\":\"404\"}]}");
  else respond(exchange,200,workspace(id,name));
 }
 private void variables(HttpExchange exchange) throws IOException {
  String id=exchange.getRequestURI().getRawPath().split("/")[4];
  List<String> list=vars.computeIfAbsent(id,k->new CopyOnWriteArrayList<>());
  if("POST".equals(exchange.getRequestMethod())){
   JsonNode body=HttpJsonClient.JSON.readTree(exchange.getRequestBody());
   varCreates.add(body);
   JsonNode a=body.path("data").path("attributes");
   if(a.path("key").asText().equals(failingVariable)){respond(exchange,422,"{\"errors\":[{\"status\":\"422\"}]}");return;}
   list.add(a.path("category").asText()+":"+a.path("key").asText());
   respond(exchange,201,"{\"data\":{\"id\":\"var-"+list.size()+"\",\"type\":\"vars\"}}");
   return;
  }
  StringBuilder b=new StringBuilder("{\"data\":[");
  for(int i=0;i<list.size();i++){
   String[] v=list.get(i).split(":",2);
   b.append(i==0?"":",").append("{\"type\":\"vars\",\"attributes\":{\"category\":\"").append(v[0]).append("\",\"key\":\"").append(v[1]).append("\"}}");
  }
  respond(exchange,200,b.append("]}").toString());
 }

 @Test void createsAWorkspaceWithVcsAndVariables() throws Exception {
  WorkflowRun b=j.buildAndAssertSuccess(pipeline(stage("vcsProviderId: 'vcs-github', repository: 'acme/network', branchName: 'main', "+
    "workingDirectory: 'stacks/network', terraformVersion: '1.6.0', executionMode: 'remote', description: 'Network', "+
    "variables: [region: 'eu-west-1', node_count: 3], environmentVariables: [TF_LOG: 'INFO'], "+
    "sensitiveVariables: [db_password: 'db-password']")));
  assertEquals(1,creates.size());
  JsonNode a=creates.get(0).path("data").path("attributes");
  assertEquals("workspaces",creates.get(0).path("data").path("type").asText());
  assertEquals("network",a.path("name").asText());
  assertEquals("Network",a.path("description").asText());
  assertEquals("1.6.0",a.path("terraform-version").asText());
  assertEquals("stacks/network",a.path("working-directory").asText());
  assertEquals("remote",a.path("execution-mode").asText());
  assertEquals("vcs-github",a.path("vcs-repo").path("oauth-token-id").asText());
  assertEquals("acme/network",a.path("vcs-repo").path("identifier").asText());
  assertEquals("main",a.path("vcs-repo").path("branch").asText());
  assertFalse(a.has("agent-pool-id"));
  assertEquals(List.of("terraform:region","terraform:node_count","env:TF_LOG","terraform:db_password"),vars.get("ws-new1"));
  JsonNode count=varCreates.get(1).path("data").path("attributes");
  assertEquals("3",count.path("value").asText());
  assertFalse(count.path("sensitive").asBoolean());
  JsonNode secret=varCreates.get(3).path("data").path("attributes");
  assertEquals(SECRET_VALUE,secret.path("value").asText());
  assertTrue(secret.path("sensitive").asBoolean());
  j.assertLogContains("IaC otf: creating workspace 'network' in organization 'acme' from VCS repository acme/network with "+
    "variables [region, node_count], environment variables [TF_LOG], sensitive variables [db_password]",b);
  j.assertLogContains("created workspace ws-new1",b);
  j.assertLogNotContains(SECRET_VALUE,b);
  j.assertLogNotContains("eu-west-1",b);
  j.assertLogContains("workspace body",b);
 }

 @Test void createsAWorkspaceWithoutOptions() throws Exception {
  j.buildAndAssertSuccess(pipeline(stage(null)));
  JsonNode a=creates.get(0).path("data").path("attributes");
  assertEquals(List.of("name"),List.copyOf(iterable(a.fieldNames())));
  assertNull(vars.get("ws-new1"));
 }

 @Test void keepsAnExistingWorkspaceUnchanged() throws Exception {
  workspaces.put("network","ws-existing");
  WorkflowRun b=j.buildAndAssertSuccess(pipeline(stage("variables: [region: 'eu-west-1']")));
  assertEquals(0,creates.size());
  assertEquals(0,varCreates.size());
  j.assertLogContains("found existing workspace ws-existing; configuration and variables not compared or changed",b);
  j.assertLogContains("workspace body",b);
 }

 @Test void ifExistsFailRejectsAnExistingWorkspace() throws Exception {
  workspaces.put("network","ws-existing");
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(stage("ifExists: 'fail'")));
  j.assertLogContains("OTF workspace 'network' in organization 'acme' already exists: ws-existing (ifExists: 'fail')",b);
  j.assertLogNotContains("workspace body",b);
 }

 @Test void aConcurrentCreateKeepsTheOtherWorkspace() throws Exception {
  createdConcurrently="network";
  WorkflowRun b=j.buildAndAssertSuccess(pipeline(stage("variables: [region: 'eu-west-1']")));
  assertEquals(1,creates.size());
  assertEquals(0,varCreates.size());
  j.assertLogContains("was created at the same time by someone else",b);
  j.assertLogContains("found existing workspace ws-other",b);
 }

 @Test void aConcurrentCreateFailsWithIfExistsFail() throws Exception {
  createdConcurrently="network";
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(stage("ifExists: 'fail'")));
  j.assertLogContains("already exists: ws-other",b);
 }

 @Test void aMissingCredentialFailsBeforeCreating() throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(stage("sensitiveVariables: [token: 'no-such-credential']")));
  j.assertLogContains("Missing or inaccessible Secret text credential no-such-credential for sensitive variable token",b);
  assertEquals(0,creates.size());
 }

 @Test void aFailedVariableNamesTheWorkspace() throws Exception {
  failingVariable="node_count";
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(stage("variables: [region: 'eu-west-1', node_count: 3]")));
  j.assertLogContains("OTF workspace ws-new1 was created, but its terraform variable node_count could not be set",b);
  j.assertLogContains("delete the workspace before running this stage again",b);
  j.assertLogNotContains("workspace body",b);
 }

 @Test void aCreatedWorkspaceCanBeUsedByNameInALaterStage() throws Exception {
  j.buildAndAssertSuccess(pipeline(stage(null)+
    "stage('plan') {\n options { otfProvision(connection: 'otf-test', organizationName: 'acme', workspaceName: 'network') }\n"+
    " steps { echo 'plan body' }\n}\n"));
  assertEquals(1,runs.size());
  assertTrue(runs.get(0).contains("\"id\":\"ws-new1\""),runs.get(0));
 }

 @Test void vcsNeedsProviderAndRepository() throws Exception {
  assertRejected("vcsProviderId: 'vcs-github'","A VCS repository needs both vcsProviderId and repository");
 }
 @Test void branchNeedsVcs() throws Exception {
  assertRejected("branchName: 'main'","branchName needs vcsProviderId and repository");
 }
 @Test void agentModeNeedsAPool() throws Exception {
  assertRejected("executionMode: 'agent'","agentPoolId is required with executionMode: 'agent' and allowed only with it");
 }
 @Test void aPoolNeedsAgentMode() throws Exception {
  assertRejected("agentPoolId: 'apool-1'","agentPoolId is required with executionMode: 'agent' and allowed only with it");
 }
 @Test void aVariableCannotBePlainAndSensitive() throws Exception {
  assertRejected("variables: [token: 'x'], sensitiveVariables: [token: 'db-password']",
    "Variable token is set in both variables and sensitiveVariables");
 }
 @Test void variableNamesAreChecked() throws Exception {
  assertRejected("environmentVariables: ['1BAD': 'x']","environmentVariables names must start with a letter or underscore");
 }
 @Test void sensitiveValuesMustBeCredentialIds() throws Exception {
  assertRejected("sensitiveVariables: [token: 3]","sensitiveVariables value of token must be the ID of a Secret text credential");
 }
 @Test void executionModeIsChecked() throws Exception {
  assertRejected("executionMode: 'cloud'","executionMode must be remote, local or agent");
 }

 private void assertRejected(String args,String message) throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(stage(args)));
  j.assertLogContains(message,b);
  j.assertLogNotContains("creating workspace",b);
  j.assertLogNotContains("workspace body",b);
  assertEquals(0,creates.size());
 }
 private static String stage(String args){
  return "stage('workspace') {\n options { otfWorkspace(connection: 'otf-test', organizationName: 'acme', workspaceName: 'network'"+
    (args==null?"":", "+args)+") }\n steps { echo 'workspace body' }\n}\n";
 }
 private WorkflowJob pipeline(String stages) throws Exception {
  WorkflowJob p=j.createProject(WorkflowJob.class);
  p.setDefinition(new CpsFlowDefinition("pipeline {\n agent none\n stages {\n"+stages+"\n }\n}",true));
  return p;
 }
 private static <T> List<T> iterable(java.util.Iterator<T> it){
  List<T> list=new java.util.ArrayList<>();it.forEachRemaining(list::add);return list;
 }
 private static String workspace(String id,String name){
  return "{\"data\":{\"id\":\""+id+"\",\"type\":\"workspaces\",\"attributes\":{\"name\":\""+name+"\"}}}";
 }
 private static void respond(HttpExchange exchange,int code,String body) throws IOException {
  byte[] b=body.getBytes(StandardCharsets.UTF_8);
  exchange.getResponseHeaders().set("Content-Type","application/vnd.api+json");
  exchange.sendResponseHeaders(code,b.length);
  try(OutputStream o=exchange.getResponseBody()){o.write(b);}
 }
}

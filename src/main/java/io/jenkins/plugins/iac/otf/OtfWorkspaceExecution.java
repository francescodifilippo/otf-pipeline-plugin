package io.jenkins.plugins.iac.otf;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.HttpJsonClient;
import io.jenkins.plugins.iac.core.IacHttpException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jenkins.util.Timer;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.*;
/**
 * Finds or creates the workspace of an {@code otfWorkspace} step off the CPS thread, then runs the body. OTF
 * creates a workspace synchronously and keeps names unique in an organization, so a concurrent create answers 409.
 * Safe to repeat after a restart: the execution saves that it sent the create request, and a resumed execution
 * that finds the workspace treats it as its own and adds only the variables that are still missing.
 */
final class OtfWorkspaceExecution extends StepExecution {
 private static final long serialVersionUID=1L;
 private final String connection;
 private final OtfWorkspaceSpec spec;
 private final boolean failIfExists;
 /** Set and saved before the create request. */
 private boolean createRequested;
 /** True when this execution created the workspace, so that it sets the variables. */
 private boolean created;
 private String workspaceId;
 private volatile boolean bodyStarted,stopped;
 private volatile BodyExecution body;
 private transient volatile Throwable stopCause;
 private transient Future<?> task;

 OtfWorkspaceExecution(StepContext context,String connection,OtfWorkspaceSpec spec,boolean failIfExists){
  super(context);this.connection=connection;this.spec=spec;this.failIfExists=failIfExists;
 }
 @Override public boolean start(){task=Timer.get().submit(this::advance);return false;}
 @Override public void onResume(){if(!bodyStarted)task=Timer.get().submit(this::advance);}
 private void log(String m) throws IOException,InterruptedException {
  getContext().get(TaskListener.class).getLogger().println("IaC otf: "+m);
 }
 private String describe(){return "workspace '"+spec.workspaceName+"' in organization '"+spec.organizationName+"'";}

 private void advance(){
  if(bodyStarted||stopped)return;
  try{
   Run<?,?> run=getContext().get(Run.class);
   try(HttpJsonClient c=OtfConnections.get().client(connection,run)){
    OtfApi api=new OtfApi(c);
    if(workspaceId==null&&!findOrCreate(api,run))return;
    if(created&&spec.hasVariables())setVariables(api,secrets(run));
    log("workspace "+workspaceId+" is ready");
    startBody();
   }
  }catch(Throwable e){getContext().onFailure(e);}
 }
 /** False when an existing workspace was kept and the body already started. */
 private boolean findOrCreate(OtfApi api,Run<?,?> run) throws Exception {
  String id=api.findWorkspaceId(spec.organizationName,spec.workspaceName);
  if(id!=null&&createRequested){
   workspaceId=id;created=true;getContext().saveState();
   log("found "+describe()+" created before the restart: "+id);
   return true;
  }
  if(id!=null)return keep(id);
  // Credentials are checked before the workspace exists, so that a missing one does not leave it half configured.
  secrets(run);
  createRequested=true;
  getContext().saveState().get(1,TimeUnit.MINUTES);
  log("creating "+describe()+(spec.vcsProviderId==null?"":" from VCS repository "+spec.repository)+variableNames());
  try{id=api.createWorkspace(spec.organizationName,spec.workspaceName,spec.attributes());}
  catch(IacHttpException e){
   if(e.statusCode()!=409)throw e;
   id=api.findWorkspaceId(spec.organizationName,spec.workspaceName);
   if(id==null)throw e;
   log(describe()+" was created at the same time by someone else");
   return keep(id);
  }
  workspaceId=id;created=true;getContext().saveState();
  log("created workspace "+id);
  return true;
 }
 private boolean keep(String id) throws Exception {
  if(failIfExists)throw new IllegalStateException("OTF "+describe()+" already exists: "+id+" (ifExists: 'fail')");
  workspaceId=id;getContext().saveState();
  log("found existing workspace "+id+"; configuration and variables not compared or changed");
  startBody();
  return false;
 }
 private String variableNames(){
  StringJoiner names=new StringJoiner(", "," with ","").setEmptyValue("");
  if(!spec.variables.isEmpty())names.add("variables "+spec.variables.keySet());
  if(!spec.environmentVariables.isEmpty())names.add("environment variables "+spec.environmentVariables.keySet());
  if(!spec.sensitiveVariables.isEmpty())names.add("sensitive variables "+spec.sensitiveVariables.keySet());
  if(!spec.sensitiveEnvironmentVariables.isEmpty())names.add("sensitive environment variables "+spec.sensitiveEnvironmentVariables.keySet());
  return names.toString();
 }
 /** Values of the sensitive variables, read from their credentials now and never stored. Keys are "category:name". */
 private Map<String,String> secrets(Run<?,?> run){
  Map<String,String> values=new HashMap<>();
  spec.sensitiveVariables.forEach((k,id)->values.put("terraform:"+k,secret(id,k,run)));
  spec.sensitiveEnvironmentVariables.forEach((k,id)->values.put("env:"+k,secret(id,k,run)));
  return values;
 }
 private static String secret(String id,String variable,Run<?,?> run){
  StringCredentials c=CredentialsProvider.findCredentialById(id,StringCredentials.class,run,Collections.emptyList());
  if(c==null)throw new IllegalArgumentException("Missing or inaccessible Secret text credential "+id+" for sensitive variable "+variable);
  return c.getSecret().getPlainText();
 }
 /** Creates the variables the workspace does not have yet, so that a resumed execution does not repeat any. */
 private void setVariables(OtfApi api,Map<String,String> secrets) throws Exception {
  Set<String> existing=api.variableKeys(workspaceId);
  List<String[]> wanted=new ArrayList<>();
  spec.variables.forEach((k,v)->wanted.add(new String[]{"terraform",k,v,"false"}));
  spec.environmentVariables.forEach((k,v)->wanted.add(new String[]{"env",k,v,"false"}));
  spec.sensitiveVariables.keySet().forEach(k->wanted.add(new String[]{"terraform",k,secrets.get("terraform:"+k),"true"}));
  spec.sensitiveEnvironmentVariables.keySet().forEach(k->wanted.add(new String[]{"env",k,secrets.get("env:"+k),"true"}));
  for(String[] v:wanted){
   if(existing.contains(v[0]+":"+v[1]))continue;
   try{api.createVariable(workspaceId,v[0],v[1],v[2],Boolean.parseBoolean(v[3]));}
   catch(Exception e){
    throw new IOException("OTF workspace "+workspaceId+" was created, but its "+v[0]+" variable "+v[1]+" could not be set ("+
      e.getMessage()+"); set the missing variables in OTF or delete the workspace before running this stage again",e);
   }
  }
 }
 private void startBody() throws Exception {
  if(stopped||bodyStarted)return;
  bodyStarted=true;
  getContext().saveState();
  BodyExecution started=getContext().newBodyInvoker().withCallback(BodyExecutionCallback.wrap(getContext())).start();
  body=started;
  if(stopped)started.cancel(stopCause);
 }
 @Override public void stop(Throwable cause){
  stopCause=cause;
  stopped=true;
  if(task!=null)task.cancel(false);
  BodyExecution running=body;
  if(running!=null)running.cancel(cause);
  else getContext().onFailure(cause);
 }
}

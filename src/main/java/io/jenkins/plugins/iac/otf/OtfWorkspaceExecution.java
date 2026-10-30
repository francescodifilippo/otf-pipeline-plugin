package io.jenkins.plugins.iac.otf;
import hudson.model.Run;
import io.jenkins.plugins.iac.core.AsyncBlockExecution;
import io.jenkins.plugins.iac.core.HttpJsonClient;
import io.jenkins.plugins.iac.core.IacHttpException;
import io.jenkins.plugins.iac.core.VariableMaps;
import java.io.IOException;
import java.util.*;
import org.jenkinsci.plugins.workflow.steps.StepContext;
/**
 * Finds or creates the workspace of an {@code otfWorkspace} step, then lets the body run. OTF
 * creates a workspace synchronously and keeps names unique in an organization, so a concurrent create answers 409.
 * Safe to repeat after a restart: the execution saves that it sent the create request, and a resumed execution
 * that finds the workspace treats it as its own and adds only the variables that are still missing.
 */
final class OtfWorkspaceExecution extends AsyncBlockExecution {
 private static final long serialVersionUID=1L;
 private final String connection;
 private final OtfWorkspaceSpec spec;
 private final boolean failIfExists;
 /** Set and saved before the create request. */
 private boolean createRequested;
 /** True when this execution created the workspace, so that it sets the variables. */
 private boolean created;
 private String workspaceId;

 OtfWorkspaceExecution(StepContext context,String connection,OtfWorkspaceSpec spec,boolean failIfExists){
  super(context,"otf");this.connection=connection;this.spec=spec;this.failIfExists=failIfExists;
 }
 private String describe(){return "workspace '"+spec.workspaceName+"' in organization '"+spec.organizationName+"'";}

 @Override protected boolean advance() throws Exception {
  Run<?,?> run=getContext().get(Run.class);
  try(HttpJsonClient c=OtfConnections.get().client(connection,run)){
   OtfApi api=new OtfApi(c);
   if(workspaceId==null)findOrCreate(api,run);
   if(created&&spec.hasVariables())setVariables(api,secrets(run));
   log("workspace "+workspaceId+" is ready");
   return true;
  }
 }
 /** Sets workspaceId, and created when this execution created the workspace. */
 private void findOrCreate(OtfApi api,Run<?,?> run) throws Exception {
  String id=api.findWorkspaceId(spec.organizationName,spec.workspaceName);
  if(id!=null&&createRequested){
   workspaceId=id;created=true;save();
   log("found "+describe()+" created before the restart: "+id);
   return;
  }
  if(id!=null){keep(id);return;}
  // Credentials are checked before the workspace exists, so that a missing one does not leave it half configured.
  secrets(run);
  createRequested=true;
  save();
  log("creating "+describe()+(spec.vcsProviderId==null?"":" from VCS repository "+spec.repository)+variableNames());
  try{id=api.createWorkspace(spec.organizationName,spec.workspaceName,spec.attributes());}
  catch(IacHttpException e){
   if(e.statusCode()!=409)throw e;
   id=api.findWorkspaceId(spec.organizationName,spec.workspaceName);
   if(id==null)throw e;
   log(describe()+" was created at the same time by someone else");
   keep(id);
   return;
  }
  workspaceId=id;created=true;save();
  log("created workspace "+id);
 }
 private void keep(String id) throws Exception {
  if(failIfExists)throw new IllegalStateException("OTF "+describe()+" already exists: "+id+" (ifExists: 'fail')");
  workspaceId=id;save();
  log("found existing workspace "+id+"; configuration and variables not compared or changed");
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
  spec.sensitiveVariables.forEach((k,id)->values.put("terraform:"+k,VariableMaps.secretText(id,k,run)));
  spec.sensitiveEnvironmentVariables.forEach((k,id)->values.put("env:"+k,VariableMaps.secretText(id,k,run)));
  return values;
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
}

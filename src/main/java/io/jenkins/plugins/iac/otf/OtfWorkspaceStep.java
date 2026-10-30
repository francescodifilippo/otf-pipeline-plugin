package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.Identifiers;
import java.util.*;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
/**
 * Declarative stage option: options { otfWorkspace(...) }. Finds the workspace named {@code workspaceName} in
 * {@code organizationName}, or creates it with its variables, then runs the stage. It runs nothing: later stages
 * use {@code otfProvision(organizationName:, workspaceName:)}.
 */
public final class OtfWorkspaceStep extends Step {
 static final Set<String> IF_EXISTS=Set.of("keep","fail"),EXECUTION_MODES=Set.of("remote","local","agent");
 private final String connection,organizationName,workspaceName;
 private String description,terraformVersion,workingDirectory,executionMode,agentPoolId,ifExists="keep";
 private String vcsProviderId,repository,branchName;
 private LinkedHashMap<String,String> variables=new LinkedHashMap<>(),environmentVariables=new LinkedHashMap<>();
 private LinkedHashMap<String,String> sensitiveVariables=new LinkedHashMap<>(),sensitiveEnvironmentVariables=new LinkedHashMap<>();

 @DataBoundConstructor public OtfWorkspaceStep(String connection,String organizationName,String workspaceName){
  this.connection=Identifiers.required(connection,"connection");
  this.organizationName=OtfProvisionStep.name(organizationName,"organizationName","[A-Za-z0-9_-]+","letters, numbers, hyphens or underscores");
  if(this.organizationName==null)throw new IllegalArgumentException("organizationName is required in the otfWorkspace stage options");
  this.workspaceName=OtfProvisionStep.name(workspaceName,"workspaceName","[A-Za-z0-9_.-]+","letters, numbers, hyphens, underscores or dots");
  if(this.workspaceName==null)throw new IllegalArgumentException("workspaceName is required in the otfWorkspace stage options");
  if(".".equals(this.workspaceName)||"..".equals(this.workspaceName))
   throw new IllegalArgumentException("workspaceName '"+this.workspaceName+"' cannot be looked up by name");
 }
 public String getConnection(){return connection;}
 public String getOrganizationName(){return organizationName;}
 public String getWorkspaceName(){return workspaceName;}
 public String getDescription(){return description;}
 public String getTerraformVersion(){return terraformVersion;}
 public String getWorkingDirectory(){return workingDirectory;}
 public String getExecutionMode(){return executionMode;}
 public String getAgentPoolId(){return agentPoolId;}
 public String getIfExists(){return ifExists;}
 public String getVcsProviderId(){return vcsProviderId;}
 public String getRepository(){return repository;}
 public String getBranchName(){return branchName;}
 public Map<String,String> getVariables(){return view(variables);}
 public Map<String,String> getEnvironmentVariables(){return view(environmentVariables);}
 public Map<String,String> getSensitiveVariables(){return view(sensitiveVariables);}
 public Map<String,String> getSensitiveEnvironmentVariables(){return view(sensitiveEnvironmentVariables);}

 @DataBoundSetter public void setDescription(String v){description=text(v,"description",4096);}
 @DataBoundSetter public void setTerraformVersion(String v){terraformVersion=text(v,"terraformVersion",64);}
 @DataBoundSetter public void setWorkingDirectory(String v){workingDirectory=Identifiers.optionalOpaque(v,"workingDirectory");}
 @DataBoundSetter public void setExecutionMode(String v){
  if(!EXECUTION_MODES.contains(v))throw new IllegalArgumentException("executionMode must be remote, local or agent");
  executionMode=v;
 }
 @DataBoundSetter public void setAgentPoolId(String v){agentPoolId=Identifiers.optional(v,"agentPoolId");}
 @DataBoundSetter public void setIfExists(String v){
  if(!IF_EXISTS.contains(v))throw new IllegalArgumentException("ifExists must be keep or fail");
  ifExists=v;
 }
 @DataBoundSetter public void setVcsProviderId(String v){vcsProviderId=Identifiers.optional(v,"vcsProviderId");}
 @DataBoundSetter public void setRepository(String v){repository=text(v,"repository",255);}
 @DataBoundSetter public void setBranchName(String v){branchName=text(v,"branchName",255);}
 /** Pipeline passes a map literal's values as Groovy gives them (numbers, booleans, GStrings): each becomes a string. */
 @DataBoundSetter public void setVariables(Map<?,?> v){variables=values(v,"variables");}
 @DataBoundSetter public void setEnvironmentVariables(Map<?,?> v){environmentVariables=values(v,"environmentVariables");}
 @DataBoundSetter public void setSensitiveVariables(Map<?,?> v){sensitiveVariables=credentialIds(v,"sensitiveVariables");}
 @DataBoundSetter public void setSensitiveEnvironmentVariables(Map<?,?> v){
  sensitiveEnvironmentVariables=credentialIds(v,"sensitiveEnvironmentVariables");
 }

 /** Checks option combinations; runs when the stage starts, before any OTF call. */
 OtfWorkspaceSpec spec(){
  if((vcsProviderId==null)!=(repository==null))
   throw new IllegalArgumentException("A VCS repository needs both vcsProviderId and repository in the otfWorkspace stage options");
  if(branchName!=null&&vcsProviderId==null)
   throw new IllegalArgumentException("branchName needs vcsProviderId and repository in the otfWorkspace stage options");
  if("agent".equals(executionMode)!=(agentPoolId!=null))
   throw new IllegalArgumentException("agentPoolId is required with executionMode: 'agent' and allowed only with it");
  both(variables,sensitiveVariables,"variables","sensitiveVariables");
  both(environmentVariables,sensitiveEnvironmentVariables,"environmentVariables","sensitiveEnvironmentVariables");
  return new OtfWorkspaceSpec(organizationName,workspaceName,description,terraformVersion,workingDirectory,executionMode,agentPoolId,
    vcsProviderId,repository,branchName,variables,environmentVariables,sensitiveVariables,sensitiveEnvironmentVariables);
 }
 @Override public StepExecution start(StepContext context){
  return new OtfWorkspaceExecution(context,connection,spec(),"fail".equals(ifExists));
 }
 private static void both(Map<String,String> plain,Map<String,String> sensitive,String plainName,String sensitiveName){
  for(String key:plain.keySet())if(sensitive.containsKey(key))
   throw new IllegalArgumentException("Variable "+key+" is set in both "+plainName+" and "+sensitiveName);
 }
 private static String key(Object key,String map){
  String k=key==null?null:key.toString();
  if(k==null||!k.matches("[A-Za-z_][A-Za-z0-9_-]{0,127}"))
   throw new IllegalArgumentException(map+" names must start with a letter or underscore and contain up to 128 letters, numbers, underscores or hyphens");
  return k;
 }
 private static LinkedHashMap<String,String> values(Map<?,?> input,String map){
  LinkedHashMap<String,String> checked=new LinkedHashMap<>();
  if(input!=null)for(Map.Entry<?,?> e:input.entrySet()){
   String k=key(e.getKey(),map);
   Object v=e.getValue();
   if(!(v instanceof CharSequence||v instanceof Number||v instanceof Boolean))
    throw new IllegalArgumentException(map+" value of "+k+" must be a string, number or boolean");
   checked.put(k,v.toString());
  }
  return checked;
 }
 private static LinkedHashMap<String,String> credentialIds(Map<?,?> input,String map){
  LinkedHashMap<String,String> checked=new LinkedHashMap<>();
  if(input!=null)for(Map.Entry<?,?> e:input.entrySet()){
   String k=key(e.getKey(),map);
   if(!(e.getValue() instanceof CharSequence))
    throw new IllegalArgumentException(map+" value of "+k+" must be the ID of a Secret text credential");
   checked.put(k,Identifiers.opaque(e.getValue().toString(),map+" credential ID of "+k));
  }
  return checked;
 }
 private static Map<String,String> view(Map<String,String> map){return map.isEmpty()?null:Collections.unmodifiableMap(map);}
 private static String text(String value,String name,int max){
  if(value==null||value.isBlank())return null;
  if(value.length()>max||value.chars().anyMatch(c->c<32||c==127))
   throw new IllegalArgumentException(name+" must be 1.."+max+" characters without control characters");
  return value;
 }
 @Extension public static final class DescriptorImpl extends StepDescriptor {
  @Override public String getFunctionName(){return "otfWorkspace";}
  @Override public String getDisplayName(){return "Find or create an OTF workspace (Declarative stage option)";}
  @Override public boolean takesImplicitBlockArgument(){return true;}
  @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class);}
 }
}

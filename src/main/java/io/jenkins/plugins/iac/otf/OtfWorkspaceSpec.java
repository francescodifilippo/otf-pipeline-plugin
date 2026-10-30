package io.jenkins.plugins.iac.otf;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jenkins.plugins.iac.core.HttpJsonClient;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validated description of the workspace an {@code otfWorkspace} step finds or creates, persisted with the step
 * execution. It holds no secret: sensitive variables are Jenkins credential IDs, read when the variable is created.
 */
final class OtfWorkspaceSpec implements Serializable {
 private static final long serialVersionUID=1L;
 final String organizationName,workspaceName,description,terraformVersion,workingDirectory,executionMode,agentPoolId;
 final String vcsProviderId,repository,branchName;
 /** Plain values by name. */
 final LinkedHashMap<String,String> variables,environmentVariables;
 /** Jenkins Secret text credential IDs by variable name. */
 final LinkedHashMap<String,String> sensitiveVariables,sensitiveEnvironmentVariables;

 OtfWorkspaceSpec(String organizationName,String workspaceName,String description,String terraformVersion,String workingDirectory,
   String executionMode,String agentPoolId,String vcsProviderId,String repository,String branchName,
   Map<String,String> variables,Map<String,String> environmentVariables,
   Map<String,String> sensitiveVariables,Map<String,String> sensitiveEnvironmentVariables){
  this.organizationName=organizationName;this.workspaceName=workspaceName;this.description=description;
  this.terraformVersion=terraformVersion;this.workingDirectory=workingDirectory;
  this.executionMode=executionMode;this.agentPoolId=agentPoolId;
  this.vcsProviderId=vcsProviderId;this.repository=repository;this.branchName=branchName;
  this.variables=new LinkedHashMap<>(variables);this.environmentVariables=new LinkedHashMap<>(environmentVariables);
  this.sensitiveVariables=new LinkedHashMap<>(sensitiveVariables);
  this.sensitiveEnvironmentVariables=new LinkedHashMap<>(sensitiveEnvironmentVariables);
 }
 /** TFE workspace attributes, without the name; unset options are left out so that OTF applies its defaults. */
 ObjectNode attributes(){
  ObjectNode a=HttpJsonClient.JSON.createObjectNode();
  if(description!=null)a.put("description",description);
  if(terraformVersion!=null)a.put("terraform-version",terraformVersion);
  if(workingDirectory!=null)a.put("working-directory",workingDirectory);
  if(executionMode!=null)a.put("execution-mode",executionMode);
  if(agentPoolId!=null)a.put("agent-pool-id",agentPoolId);
  if(vcsProviderId!=null){
   ObjectNode vcs=a.putObject("vcs-repo").put("oauth-token-id",vcsProviderId).put("identifier",repository);
   if(branchName!=null)vcs.put("branch",branchName);
  }
  return a;
 }
 boolean hasVariables(){
  return !variables.isEmpty()||!environmentVariables.isEmpty()||!sensitiveVariables.isEmpty()||!sensitiveEnvironmentVariables.isEmpty();
 }
}

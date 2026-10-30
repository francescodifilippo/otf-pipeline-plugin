package io.jenkins.plugins.iac.otf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jenkins.plugins.iac.core.*;
import java.io.IOException;
import java.util.Set;
/** Implements only the OTF subset verified against its Terraform-compatible REST API. */
public final class OtfApi {
 private final HttpJsonClient transport;
 private static final Set<String> SUCCESS=Set.of("applied","planned_and_finished");
 private static final Set<String> FAILED=Set.of("errored","canceled","force_canceled","discarded");
 /** auto-apply is always false, so an apply run with changes waits in planned (cost_estimated if enabled) for confirmation. */
 private static final Set<String> APPROVAL=Set.of("planned","cost_estimated");
 OtfApi(HttpJsonClient transport){this.transport=transport;}
 public JobResult submit(String workspaceId,String mode) throws IOException,InterruptedException {
  Identifiers.required(workspaceId,"workspaceId");
  if(!"plan".equals(mode)&&!"apply".equals(mode))throw new IllegalArgumentException("OTF mode must be plan or apply");
  ObjectNode request=HttpJsonClient.JSON.createObjectNode();
  ObjectNode data=request.putObject("data");data.put("type","runs");
  ObjectNode attributes=data.putObject("attributes");attributes.put("plan-only","plan".equals(mode));attributes.put("auto-apply",false);
  data.putObject("relationships").putObject("workspace").putObject("data")
    .put("type","workspaces").put("id",workspaceId);
  return parse(transport.request("POST","/api/v2/runs",request));
 }
 public JobResult status(String id) throws IOException,InterruptedException {
  return parse(transport.request("GET","/api/v2/runs/"+Identifiers.required(id,"remoteId"),null));
 }
 /**
  * ID of the workspace named exactly {@code name} in {@code organization}, read with TFE "show workspace by name"
  * ({@code GET /api/v2/organizations/:organization/workspaces/:name}). OTF answers 404 when either does not exist.
  */
 public String workspaceIdByName(String organization,String name) throws IOException,InterruptedException {
  String id=findWorkspaceId(organization,name);
  if(id==null)throw new IllegalArgumentException("No OTF workspace named '"+name+"' in organization '"+organization+
     "'; check organizationName and workspaceName, or use workspaceId");
  return id;
 }
 /** Same read as {@link #workspaceIdByName}, but null when OTF answers 404. */
 String findWorkspaceId(String organization,String name) throws IOException,InterruptedException {
  JsonNode data;
  try{
   data=transport.request("GET",workspacesPath(organization)+"/"+HttpJsonClient.pathSegment(name),null).path("data");
  }catch(IacHttpException e){
   if(e.statusCode()==404)return null;
   throw e;
  }
  return workspaceId(data,organization,name);
 }
 /**
  * Creates a workspace with TFE "create a workspace" ({@code POST /api/v2/organizations/:organization/workspaces}).
  * Names are unique in an organization: OTF answers 409 when the workspace already exists.
  */
 String createWorkspace(String organization,String name,ObjectNode attributes) throws IOException,InterruptedException {
  ObjectNode request=HttpJsonClient.JSON.createObjectNode();
  ObjectNode data=request.putObject("data");data.put("type","workspaces");
  data.set("attributes",attributes.deepCopy().put("name",name));
  return workspaceId(transport.request("POST",workspacesPath(organization),request).path("data"),organization,name);
 }
 /** Category and key ({@code terraform:region}, {@code env:TF_LOG}) of every variable of the workspace. */
 Set<String> variableKeys(String workspaceId) throws IOException,InterruptedException {
  Set<String> keys=new java.util.HashSet<>();
  for(JsonNode v:transport.request("GET","/api/v2/workspaces/"+Identifiers.required(workspaceId,"workspaceId")+"/vars",null).path("data"))
   keys.add(v.path("attributes").path("category").asText()+":"+v.path("attributes").path("key").asText());
  return keys;
 }
 /** Creates a workspace variable ({@code POST /api/v2/workspaces/:id/vars}); the value is sent, never logged. */
 void createVariable(String workspaceId,String category,String key,String value,boolean sensitive) throws IOException,InterruptedException {
  ObjectNode request=HttpJsonClient.JSON.createObjectNode();
  ObjectNode data=request.putObject("data");data.put("type","vars");
  data.putObject("attributes").put("key",key).put("value",value).put("category",category).put("hcl",false).put("sensitive",sensitive);
  transport.request("POST","/api/v2/workspaces/"+Identifiers.required(workspaceId,"workspaceId")+"/vars",request);
 }
 private static String workspacesPath(String organization){
  return "/api/v2/organizations/"+HttpJsonClient.pathSegment(organization)+"/workspaces";
 }
 private static String workspaceId(JsonNode data,String organization,String name) throws IOException {
  if(!"workspaces".equals(data.path("type").asText())||!name.equals(data.path("attributes").path("name").asText(null)))
   throw new IOException("OTF did not return workspace '"+name+"' of organization '"+organization+"'");
  return Identifiers.required(data.path("id").asText(null),"OTF workspace ID");
 }
 static JobResult parse(JsonNode response) throws IOException {
  return HttpJsonClient.parse(response,SUCCESS,FAILED,APPROVAL);
 }
}

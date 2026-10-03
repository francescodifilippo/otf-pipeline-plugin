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
 public void confirm(String id) throws IOException,InterruptedException {
  transport.request("POST","/api/v2/runs/"+Identifiers.required(id,"remoteId")+"/actions/apply",null);
 }
 private static JobResult parse(JsonNode response) throws IOException {
  return HttpJsonClient.parse(response,SUCCESS,FAILED,Set.of("needs_confirmation","policy_checked"));
 }
}

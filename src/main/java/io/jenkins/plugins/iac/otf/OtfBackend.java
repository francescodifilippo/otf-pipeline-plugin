package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.iac.core.*;
import java.util.Map;
@Extension public final class OtfBackend implements IacBackend {
 @Override public String id(){return "otf";}
 /** A workspaceId is used as given; a workspaceName is looked up in its organization (read-only). */
 @Override public ResolvedTarget resolve(String connectionId,String targetId,Map<String,String> parameters,Run<?,?> run) throws Exception {
  if(targetId!=null)return new ResolvedTarget(targetId,parameters);
  String organization=parameters.get("organization"),name=parameters.get("workspaceName");
  if(organization==null||name==null)throw new IllegalArgumentException("otfProvision needs workspaceId, or workspaceName with organization");
  try(HttpJsonClient c=OtfConnections.get().client(connectionId,run)){
   return new ResolvedTarget(new OtfApi(c).workspaceIdByName(organization,name),parameters);
  }
 }
 @Override public JobResult submit(SubmissionRequest request,Run<?,?> run) throws Exception {
  try(HttpJsonClient c=OtfConnections.get().client(request.connectionId(),run)){
    return new OtfApi(c).submit(request.targetId(),request.parameters().getOrDefault("mode","plan"));
  }
 }
 @Override public JobResult status(RemoteOperation operation,Run<?,?> run) throws Exception {
  try(HttpJsonClient c=OtfConnections.get().client(operation.connectionId(),run)){
   return new OtfApi(c).status(operation.remoteId());
  }
 }
}

package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.iac.core.*;
@Extension public final class OtfBackend implements IacBackend {
 @Override public String id(){return "otf";}
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

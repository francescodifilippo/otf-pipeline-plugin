package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.iac.core.*;
import java.util.Map;
@Extension public final class OtfBackend implements IacBackend {
 @Override public String id(){return "otf";}
 @Override public OtfConnections connections(){return OtfConnections.get();}
 @Override public JobResult submit(String server,String workspaceId,Map<String,String> opts,Run<?,?> run) throws Exception {
  try(HttpJsonClient c=connections().client(server,run)){
    return new OtfApi(c).submit(workspaceId,opts.getOrDefault("mode","plan"));
  }
 }
 @Override public JobResult status(String server,String organizationId,String remoteId,Run<?,?> run) throws Exception {
  try(HttpJsonClient c=connections().client(server,run)){return new OtfApi(c).status(remoteId);}
 }
}

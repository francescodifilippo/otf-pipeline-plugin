package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.AbstractProvisionStep;
import java.util.*;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
/**
 * Declarative stage option: options { otfProvision(...) }. The workspace is either {@code workspaceId} or
 * {@code workspaceName} with {@code organization}, which {@link OtfBackend#resolve} looks up when the stage runs.
 */
public final class OtfProvisionStep extends AbstractProvisionStep {
 private String mode="plan",organization,workspaceName;
 /** workspaceId is an optional setter so that Pipeline Syntax does not list it as required. */
 @DataBoundConstructor public OtfProvisionStep(String server){super(server);}
 /** Convenience for Java callers; Pipeline binds workspaceId through {@link #setWorkspaceId}. */
 public OtfProvisionStep(String server,String workspaceId){super(server,workspaceId);}
 public String getServer(){return getConnectionId();}
 public String getWorkspaceId(){return getTargetId();}
 public String getOrganization(){return organization;}
 public String getWorkspaceName(){return workspaceName;}
 public String getMode(){return mode;}
 @DataBoundSetter public void setWorkspaceId(String v){setTargetId(v);}
 /** OTF organization names: letters, digits, hyphens and underscores. */
 @DataBoundSetter public void setOrganization(String v){
   organization=name(v,"organization","[A-Za-z0-9_-]+","letters, numbers, hyphens or underscores");
 }
 /** OTF workspace names: letters, digits, hyphens, underscores and dots. */
 @DataBoundSetter public void setWorkspaceName(String v){
   workspaceName=name(v,"workspaceName","[A-Za-z0-9_.-]+","letters, numbers, hyphens, underscores or dots");
   if(".".equals(workspaceName)||"..".equals(workspaceName))
     throw new IllegalArgumentException("workspaceName '"+workspaceName+"' cannot be looked up by name; use workspaceId");
 }
 @DataBoundSetter public void setMode(String v){
   if(!"plan".equals(v)&&!"apply".equals(v)) throw new IllegalArgumentException("mode must be plan/apply");
   mode=v;
 }
 @Override protected String provider(){return "otf";}
 @Override protected Map<String,String> parameters(){
  String workspaceId=getTargetId();
  if(workspaceId!=null&&workspaceName!=null)
   throw new IllegalArgumentException("Use either workspaceId or workspaceName in the otfProvision stage options, not both");
  if(workspaceId==null&&workspaceName==null)
   throw new IllegalArgumentException("otfProvision needs workspaceId, or workspaceName with organization, in the stage options");
  if(workspaceName!=null&&organization==null)
   throw new IllegalArgumentException("workspaceName needs organization (name of the OTF organization that contains the workspace) in the otfProvision stage options");
  if(workspaceId!=null&&organization!=null)
   throw new IllegalArgumentException("organization is used only with workspaceName; remove it from the otfProvision stage options when workspaceId is set");
  Map<String,String> p=new LinkedHashMap<>();p.put("mode",mode);
  if(workspaceName!=null){p.put("organization",organization);p.put("workspaceName",workspaceName);}
  return p;
 }
 /** Null for null/blank; otherwise 1..255 characters matching {@code pattern}. OTF itself sets no length limit. */
 private static String name(String value,String name,String pattern,String allowed){
  if(value==null||value.isBlank())return null;
  if(value.length()>255||!value.matches(pattern))
   throw new IllegalArgumentException(name+" must contain 1..255 "+allowed);
  return value;
 }
 @Extension public static final class DescriptorImpl extends StepDescriptor {
  @Override public String getFunctionName(){return "otfProvision";}
  @Override public String getDisplayName(){return "OTF remote execution (Declarative stage option)";}
  @Override public boolean takesImplicitBlockArgument(){return true;}
  @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class);}
 }
}

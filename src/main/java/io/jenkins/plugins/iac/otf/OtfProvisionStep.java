package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.AbstractProvisionStep;
import java.util.*;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
/** Declarative stage option: options { otfProvision(...) } */
public final class OtfProvisionStep extends AbstractProvisionStep {
 private String mode="plan";
 @DataBoundConstructor public OtfProvisionStep(String server,String workspaceId){super(server,workspaceId);}
 public String getServer(){return getConnectionId();}
 public String getWorkspaceId(){return getTargetId();}
 public String getMode(){return mode;}
 @DataBoundSetter public void setMode(String v){
   if(!"plan".equals(v)&&!"apply".equals(v)) throw new IllegalArgumentException("mode must be plan/apply");
   mode=v;
 }
 @Override protected String provider(){return "otf";}
 @Override protected Map<String,String> parameters(){return Map.of("mode",mode);}
 @Extension public static final class DescriptorImpl extends StepDescriptor {
  @Override public String getFunctionName(){return "otfProvision";}
  @Override public String getDisplayName(){return "OTF remote execution (Declarative stage option)";}
  @Override public boolean takesImplicitBlockArgument(){return true;}
  @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class);}
 }
}

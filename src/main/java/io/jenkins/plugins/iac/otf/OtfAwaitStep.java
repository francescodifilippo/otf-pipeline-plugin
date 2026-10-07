package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.AbstractAwaitStep;
import java.util.*;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
/** Declarative stage option: options { otfAwait(...) } */
public final class OtfAwaitStep extends AbstractAwaitStep {
 @DataBoundConstructor public OtfAwaitStep(String server,String operationKey){super(server,operationKey);}
 public String getServer(){return getConnectionId();}
 @Override protected String provider(){return "otf";}
 @Extension public static final class DescriptorImpl extends StepDescriptor {
  @Override public String getFunctionName(){return "otfAwait";}
  @Override public String getDisplayName(){return "Await previous OTF operation (Declarative stage option)";}
  @Override public boolean takesImplicitBlockArgument(){return true;}
  @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class);}
 }
}

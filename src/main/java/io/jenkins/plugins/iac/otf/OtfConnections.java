package io.jenkins.plugins.iac.otf;
import hudson.Extension;
import io.jenkins.plugins.iac.core.AbstractIacConnections;
import hudson.ExtensionList;
@Extension public final class OtfConnections extends AbstractIacConnections {
 @Override public String getDisplayName(){return "OTF connections";}
 public static OtfConnections get(){return ExtensionList.lookupSingleton(OtfConnections.class);}
}

package io.jenkins.plugins.hugo;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author suren
 */
public class HugoBuilder extends Builder implements SimpleBuildStep
{
    public static final String TEMP_PUBLIC = ".public";

    private String hugoHome;

    @DataBoundConstructor
    public HugoBuilder() {}

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws InterruptedException, IOException
    {
        hugoBuild(run, launcher, listener, workspace);
    }

    /**
     * Build the Hugo site through hugo cmd line
     * @param run Job Run
     * @param launcher Launcher
     * @param listener Job Listener
     * @param workspace Job Workspace
     * @throws IOException In case of io error
     * @throws InterruptedException In case of job running be interrupt
     */
    private void hugoBuild(@Nonnull Run<?, ?> run, Launcher launcher, TaskListener listener, @Nonnull FilePath workspace)
            throws IOException, InterruptedException
    {
        PrintStream logger = listener.getLogger();
        EnvVars env = run.getEnvironment(listener);

        String hugoCmd;
        if(getHugoHome() == null || "".equals(getHugoHome().trim()))
        {
            hugoCmd = "hugo";
        }
        else
        {
            hugoCmd = getHugoHome() + "hugo";
        }

        hugoCmd += " --destination " + HugoBuilder.TEMP_PUBLIC;

        int exitCode = launcher.launch().pwd(workspace)
                .cmdAsSingleString(hugoCmd).envs(env).stdout(logger).stderr(logger).start().join();
        if(exitCode != 0) {
            listener.fatalError("Hugo build error, exit code: " + exitCode);
        }
    }

    public String getHugoHome()
    {
        return hugoHome;
    }

    @DataBoundSetter
    public void setHugoHome(String hugoHome)
    {
        this.hugoHome = hugoHome;
    }

    @Extension
    @Symbol("hugo")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder>
    {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType)
        {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName()
        {
            return Messages.hugo_builder_name();
        }
    }
}
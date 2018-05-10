package io.jenkins.plugins.hugo;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.*;

public class HugoBuilder extends Builder implements SimpleBuildStep
{
    @DataBoundConstructor
    public HugoBuilder() {
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException
    {
        PrintStream logger = listener.getLogger();

        // check whether has submodule
        Git git = new Git(listener, null);
        GitClient client = git.in(workspace).getClient();

        boolean hasGitModules = client.hasGitModules();
        if(hasGitModules)
        {
            logger.println("Has subModules.");

            client.submoduleInit();
            client.submoduleUpdate();
        }

        logger.println("prepare to execute hugo");
        Runtime runtime = Runtime.getRuntime();

        File docDir = new File(workspace.getRemote());
        Process process = runtime.exec("hugo", null, docDir);
        InputStream input = process.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line = null;
        while((line = reader.readLine()) != null)
        {
            logger.println(line);
        }

        if(hasGitModules)
        {
            logger.println("Prepare to commit and push");

            FilePath publishDir = workspace.child("public");

            client = git.in(publishDir).getClient();

            String branch = "suren-" + System.currentTimeMillis();
            logger.println("create new branch");
            client.checkout().branch(branch);

            client.add(publishDir.getRemote());
            client.commit("Auto generate by suren");
        }
    }

    @Extension
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
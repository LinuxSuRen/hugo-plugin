package io.jenkins.plugins.hugo;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
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
    private String publishDir = "public";
    private String publishBranch = "master";
    private String credentialsId;

    private String hugoHome;
    private String authorName;
    private String authorEmail;
    private String committerName;
    private String committerEmail;

    private String commitLog;

    @DataBoundConstructor
    public HugoBuilder(String credentialsId)
    {
        this.credentialsId = credentialsId;
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
//        if(hasGitModules)
//        {
//            logger.println("Has subModules.");
//
//            // TODO here is some problem, we should use system method for now
//            client.submoduleInit();
//            client.submoduleUpdate();
//        }

        // TODO here should check whether submodule'name is publish directory
        if(hasGitModules)
        {
            logger.println("Prepare to commit and push");

            FilePath publishPath = workspace.child(publishDir);

            client = git.in(publishPath).getClient();

            String branch = publishBranch;
            logger.println("create new branch");

            client.checkout().branch(branch).deleteBranchIfExist(true).ref("origin/master").execute();

            logger.println("prepare to execute hugo");
            hugoBuild(run, listener, workspace);

            logger.println("remote: " + publishPath.getRemote());
            logger.println("add everything.");

            String url = client.getRemoteUrl("origin");

            if(credentialsId != null)
            {
                StandardUsernameCredentials credential = getCredential(logger);
                if(credential != null)
                {
                    client.setCredentials(credential);
                    client.setAuthor(getAuthorName(), getAuthorEmail());
                    client.setCommitter(getCommitterName(), getCommitterEmail());

                    logger.println("already set credential : " + credential.getUsername());
                }
                else
                {
                    logger.println("can not found credential");
                }
            }

            logger.println("remote is " + url);

            client.add(".");
            client.commit(getCommitLog());

            try
            {

                client.push().to(new URIish(url)).ref("origin/" + branch).execute();
            }
            catch (URISyntaxException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            logger.println("No submodule found, just run hugo build.");

            hugoBuild(run, listener, workspace);
        }
    }

    /**
     * Build the Hugo site through hugo cmd line
     * @param run Job Run
     * @param listener Job Listener
     * @param workspace Job Workspace
     * @throws IOException In case of io error
     * @throws InterruptedException In case of job running be interrupt
     */
    private void hugoBuild(@Nonnull Run<?, ?> run, TaskListener listener, @Nonnull FilePath workspace)
            throws IOException, InterruptedException
    {
        Runtime runtime = Runtime.getRuntime();
        PrintStream logger = listener.getLogger();

        EnvVars env = run.getEnvironment(listener);

        String[] envp = null;
        if(env.values().size() > 0)
        {
            envp = new String[env.values().size()];
            Set<String> keys = env.keySet();
            int index = 0;
            for(String key : keys)
            {
                envp[index++] = key + "=" + env.get(key);
            }
        }

        File docDir = new File(workspace.getRemote());
        String hugoCmd;
        if(getHugoHome() == null || "".equals(getHugoHome().trim()))
        {
            hugoCmd = "hugo";
        }
        else
        {
            hugoCmd = getHugoHome() + "hugo";
        }

        Process process = runtime.exec(hugoCmd, envp, docDir);
        InputStream input = process.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line;
        while((line = reader.readLine()) != null)
        {
            logger.println(line);
        }
    }

    private StandardUsernameCredentials getCredential(PrintStream logger)
    {
        List<StandardUsernameCredentials> allCredentials = CredentialsProvider.lookupCredentials
                (StandardUsernameCredentials.class, Jenkins.get(), ACL.SYSTEM, new ArrayList<>());

        Credentials credential = CredentialsMatchers.firstOrNull(
                allCredentials, CredentialsMatchers.withId(getCredentialsId()));

        if(credential != null)
        {
            return (StandardUsernameCredentials) credential;
        }
        else
        {
            logger.println("can not found credential");
        }

        return null;
    }

    public String getPublishDir()
    {
        return publishDir;
    }

    @DataBoundSetter
    public void setPublishDir(String publishDir)
    {
        this.publishDir = publishDir;
    }

    public String getPublishBranch()
    {
        return publishBranch;
    }

    @DataBoundSetter
    public void setPublishBranch(String publishBranch)
    {
        this.publishBranch = publishBranch;
    }

    public String getCredentialsId()
    {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId)
    {
        this.credentialsId = credentialsId;
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

    public String getAuthorName()
    {
        return authorName;
    }

    @DataBoundSetter
    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public String getAuthorEmail()
    {
        return authorEmail;
    }

    @DataBoundSetter
    public void setAuthorEmail(String authorEmail)
    {
        this.authorEmail = authorEmail;
    }

    public String getCommitterName()
    {
        return committerName;
    }

    @DataBoundSetter
    public void setCommitterName(String committerName)
    {
        this.committerName = committerName;
    }

    public String getCommitterEmail()
    {
        return committerEmail;
    }

    @DataBoundSetter
    public void setCommitterEmail(String committerEmail)
    {
        this.committerEmail = committerEmail;
    }

    public String getCommitLog()
    {
        return commitLog;
    }

    @DataBoundSetter
    public void setCommitLog(String commitLog)
    {
        this.commitLog = commitLog;
    }

    @Extension
    @Symbol("hugo")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder>
    {
        public ListBoxModel doFillCredentialsIdItems() {
            FreeStyleProject project = new FreeStyleProject(Jenkins.get(),"fake-" + UUID.randomUUID().toString());

            return new StandardListBoxModel().includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM, project,
                            StandardUsernameCredentials.class,
                            new ArrayList<>(),
                            CredentialsMatchers.withScopes(CredentialsScope.GLOBAL));
        }

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
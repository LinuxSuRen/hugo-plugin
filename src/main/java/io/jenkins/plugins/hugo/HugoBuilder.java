package io.jenkins.plugins.hugo;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author suren
 */
public class HugoBuilder extends Builder implements SimpleBuildStep
{
    private String publishDir = "public";
    private String publishBranch = "master";
    private String credentialsId;

    private String hugoHome;

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

        logger.println("prepare to execute hugo");
        Runtime runtime = Runtime.getRuntime();

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
        String line = null;
        while((line = reader.readLine()) != null)
        {
            logger.println(line);
        }

        if(hasGitModules)
        {
            logger.println("Prepare to commit and push");

            FilePath publishPath = workspace.child(publishDir);

            client = git.in(publishPath).getClient();

            String branch = publishBranch;
            logger.println("create new branch");

            boolean branchExists = false;
            Set<Branch> branches = client.getBranches();
            if(branches != null && branches.size() > 0)
            {
                Iterator<Branch> it = branches.iterator();
                while(it.hasNext())
                {
                    if(it.next().getName().equals(publishBranch))
                    {
                        branchExists = true;
                        break;
                    }
                }
            }

            if(branchExists)
            {
                client.checkout().ref(branch).execute();
            }
            else
            {
                client.checkout().ref(null).branch(branch).execute();
            }

            client.add(publishPath.getRemote());
            client.commit("Auto generate by suren");
            try
            {
                String url = client.getRemoteUrl("origin");

                if(credentialsId != null)
                {
                    StandardUsernameCredentials credential = getCredential(logger);
                    if(credential != null)
                    {
                        client.setCredentials(credential);
                        client.setAuthor("author", "author@author.com");
                        client.setCommitter("name", "email@emai.com");

                        logger.println("already set credential : " + credential.getUsername());
                    }
                    else
                    {
                        logger.println("can not found credential");
                    }
                }

                logger.println("remote is " + url);

                client.push().to(new URIish(url)).ref(branch).execute();
            }
            catch (URISyntaxException e)
            {
                e.printStackTrace();
            }
        }
    }

    private StandardUsernameCredentials getCredential(PrintStream logger)
    {
        List<StandardUsernameCredentials> allCredentials = CredentialsProvider.lookupCredentials
                (StandardUsernameCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, new ArrayList<DomainRequirement>());

        Credentials credential = CredentialsMatchers.firstOrNull(
                allCredentials, CredentialsMatchers.withId(getCredentialsId()));

        if(credential instanceof StandardUsernameCredentials)
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

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder>
    {
        public ListBoxModel doFillCredentialsIdItems() {
            FreeStyleProject project = new FreeStyleProject(Jenkins.getInstance(), "fake-" + UUID.randomUUID().toString());

            return new StandardListBoxModel().includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM, project,
                            StandardUsernameCredentials.class,
                            new ArrayList<DomainRequirement>(),
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
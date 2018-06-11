package io.jenkins.plugins.hugo;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
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
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
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
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author suren
 */
public class HugoGitPublisher extends Recorder implements SimpleBuildStep {
    private String targetUrl;
    private String publishDir;
    private String publishBranch;
    private String credentialsId;

    private String authorName;
    private String authorEmail;
    private String committerName;
    private String committerEmail;

    private String commitLog;

    @DataBoundConstructor
    public HugoGitPublisher(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws InterruptedException, IOException {
        FilePath tmpPath = workspace.child("." + publishDir);

        Git git = new Git(listener, null);
        GitClient client = git.in(tmpPath).getClient();
        PrintStream logger = listener.getLogger();

        setCredential(client, credentialsId, logger);

        client.init();
        client.clone_().url(targetUrl).execute();
        client.checkoutBranch(publishBranch, "origin/" + publishBranch);

        workspace.child(publishDir).copyRecursiveTo(tmpPath);

        if(getAuthorName() != null) {
            client.setAuthor(getAuthorName(), getAuthorEmail());
        }

        if(getCommitterName() != null) {
            client.setCommitter(getCommitterName(), getCommitterEmail());
        }

        client.add(".");
        client.commit(commitLog);

        logger.println("Prepare to commit and push");

        try {
            client.push().to(new URIish(targetUrl)).ref(publishBranch).force(true).execute();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void setCredential(GitClient client, String credentialId, PrintStream logger) {
        if(credentialId == null || "".equals(credentialId.trim())) {
            logger.println("No credential provide.");
            return;
        }

        StandardUsernameCredentials credential = getCredential(logger);
        if(credential != null) {
            client.setCredentials(credential);
        } else {
            logger.println(String.format("Can not found credential by id [%s].", credentialId));
        }
    }

    private void branchSwitch(GitClient client, String targetBranch, PrintStream logger) throws InterruptedException {
        boolean targetBranchExist = false;
        Set<Branch> branches = client.getBranches();
        if(branches != null) {
            targetBranchExist = branches.stream().anyMatch((branch) -> branch.getName().equals(targetBranch));
        }

        if(!targetBranchExist) {
            logger.println(String.format("Target branch [%s] don't need to create.", targetBranch));

            client.branch(targetBranch);
        }

        client.checkout(targetBranch);

        logger.println(String.format("Already switch to branch [%s].", targetBranch));
    }

    private StandardUsernameCredentials getCredential(PrintStream logger) {
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

    public String getTargetUrl() {
        return targetUrl;
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

    @DataBoundSetter
    public void setCredentialsId(String credentialsId)
    {
        this.credentialsId = credentialsId;
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
    @Symbol("hugoGitPublsh")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher>
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
            return Messages.hugo_publisher_git();
        }
    }
}
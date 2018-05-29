package io.jenkins.plugins.hugo;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author suren
 */
public class HugoBuildTest
{
    private String repo = "https://gitee.com/arch2surenpi/hugo-plugin-test";

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    @Ignore
    public void build() throws Exception
    {
        FreeStyleProject project = rule.createFreeStyleProject();

        HugoBuilder builder = new HugoBuilder();

        GitSCM scm = new GitSCM(repo);

        GitSCMExtension item = new SubmoduleOption(false,
                true, true,
                null, 10, true);
        scm.getExtensions().add(item);
        project.setScm(scm);

        StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, null,
                null, "", "");
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

        project.getBuildersList().add(builder);

        rule.createOnlineSlave();

        FreeStyleBuild build = rule.buildAndAssertSuccess(project);
        rule.assertLogContains("hugo", build);
    }
}

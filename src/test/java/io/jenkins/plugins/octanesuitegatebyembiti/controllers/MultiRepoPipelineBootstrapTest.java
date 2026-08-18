package io.jenkins.plugins.octanesuitegatebyembiti.controllers;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class MultiRepoPipelineBootstrapTest {
  @Rule public JenkinsRule jenkins = new JenkinsRule();

  @Test(timeout = 60_000L)
  public void validatedYamlValuesSurviveLoadedDeclarativePipelineWorkspaceDetachment()
      throws Exception {
    WorkflowJob job = jenkins.createProject(WorkflowJob.class);
    job.setDefinition(
        new CpsFlowDefinition(
            """
            node {
              sh '''
                rm -rf dir1 dir2
                mkdir -p dir1 dir2
                printf '%s\\n' \\
                  'OCTANE_SHARED_SPACE_NAME: "Default Shared Space"' \\
                  'OCTANE_WORKSPACE_NAME: "Abbybot Mail Service"' \\
                  'OCTANE_CRITICAL_SUITE_RUN_ID: "76645"' \\
                  > dir2/variables.yaml
              '''
              writeFile file: 'dir1/Jenkinsfile', text: '''
              def bootstrappedConfigurationJson(environment) {
                return environment.getProperty(
                    'OCTANE_BOOTSTRAP_CONFIGURATION_JSON')?.toString()?.trim()
              }

              pipeline {
                agent any
                environment {
                  PARAMS_FILE = 'variables.yaml'
                }
                stages {
                  stage('Loaded Target') {
                    steps {
                      sh 'test -f "$OCTANE_PIPELINE_SOURCE_DIR/variables.yaml"'
                      script {
                        String configurationJson = bootstrappedConfigurationJson(env)
                        if (!configurationJson) {
                          error 'Bootstrap configuration was not transported.'
                        }
                        Map configuration = readJSON(text: configurationJson) as Map
                        configuration.each { String key, def value ->
                          env.setProperty(key, value == null ? '' : value.toString())
                        }
                        echo "BOOTSTRAP_SPACE=${env.getProperty('OCTANE_SHARED_SPACE_NAME')}"
                        echo "BOOTSTRAP_WORKSPACE=${env.getProperty('OCTANE_WORKSPACE_NAME')}"
                        echo "BOOTSTRAP_CRITICAL=${env.getProperty('OCTANE_CRITICAL_SUITE_RUN_ID')}"
                        echo "TARGET_RUNTIME_WORKSPACE=${pwd()}"
                      }
                    }
                  }
                }
              }
              '''
              sh 'cp -- dir2/variables.yaml dir1/variables.yaml'
              Map configuration = [
                OCTANE_SHARED_SPACE_NAME: 'Default Shared Space',
                OCTANE_WORKSPACE_NAME: 'Abbybot Mail Service',
                OCTANE_CRITICAL_SUITE_RUN_ID: '76645'
              ]
              String configurationJson = writeJSON(json: configuration, returnText: true)
              List<String> pipelineEnvironment = [
                "OCTANE_BOOTSTRAP_CONFIGURATION_JSON=${configurationJson}"
              ]
              dir('dir1') {
                String pipelineSourceDirectory = pwd()
                withEnv(
                    pipelineEnvironment +
                    ["OCTANE_PIPELINE_SOURCE_DIR=${pipelineSourceDirectory}"]) {
                  load 'Jenkinsfile'
                }
              }
            }
            """,
            true));

    WorkflowRun run = jenkins.buildAndAssertSuccess(job);

    jenkins.assertLogContains("BOOTSTRAP_SPACE=Default Shared Space", run);
    jenkins.assertLogContains("BOOTSTRAP_WORKSPACE=Abbybot Mail Service", run);
    jenkins.assertLogContains("BOOTSTRAP_CRITICAL=76645", run);
    jenkins.assertLogContains("TARGET_RUNTIME_WORKSPACE=", run);
    jenkins.assertLogContains("@2", run);
  }
}

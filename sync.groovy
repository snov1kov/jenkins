import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*
import hudson.plugins.sshslaves.*;
import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins

//def env = System.getenv()
def env = binding.variables;
project= env["buildproject"] ?: "testproject"
gitUrl=env["buildgiturl"] ?: "http://git.localhost.com/git"
branchName=env["buildbranch"] ?: "master"
repoOnecPath=env["buildRDATA"] ?: "c:\\temp\\"


def viewname = project;
if (branchName != "master"){
    viewname = viewname + "-"+branchName;
}


job("check-$viewname") {
   logRotator(-1, 10)
   label("slave")

    environmentVariables {
        overrideBuildParameters(true)
        envs(RDATA:repoOnecPath)
    }

    configure{
        it / triggers << 'org.jenkinsci.plugins.fstrigger.triggers.FileNameTrigger'(plugin: 'fstrigger@0.39') {
          spec("H/5 * * * *")
            fileInfo << 'fileInfo'{
            fileInfo << 'org.jenkinsci.plugins.fstrigger.triggers.FileNameTriggerInfo'{
               filePathPattern(repoOnecPath+"1cv8ddb.1CD")
               strategy("LATEST")
               inspectingContentFile('true')
               doNotCheckLastModificationDate('false')
               contentFileTypes << 'contentFileTypes'{
               contentFileTypes << 'org.jenkinsci.plugins.fstrigger.triggers.filecontent.SimpleFileContent'{
               }}
               }
            }
        }
    }

   def multiline='''def env = binding.variables;
def rdata = System.getenv("RDATA")
println "sudo chgrp -R ubuntu $rdata";

try {
  pritnln "sudo chgrp -R ubuntu $rdata";
  def proc = "sudo chgrp -R ubuntu $rdata".execute();
   proc.waitFor();
   println proc.text;
  
} catch(Exception ex) {
  println("Catching the exception");
}


    String sourceDir = rdata;
    String destinationDir = "./build/repo"
    new AntBuilder().copy(todir: destinationDir) {
        fileset(dir: sourceDir)
        exclude(name:"*.snp")
    }
'''
    steps {
        groovyCommand(multiline) {
            groovyInstallation('2.4.7')
        }
        //shell('rsync -avz --links $RDATA $WORKSPACE/build/repo/')
    }

    publishers {
        archiveArtifacts('build/repo/**')
        downstream("sync-$viewname", 'SUCCESS')
        }
        //downstream("xunit-$project", 'SUCCESS')
        //buildPipelineTrigger("sync-$project,")
}

job("sync-$viewname") {
   logRotator(-1, 10)
   label("slave")

    environmentVariables {
        overrideBuildParameters(true)
        envs(RDATA:repoOnecPath)
        envs(DISPLAY:':1')
        envs(WINEDLLOVERRIDES:'"mscoree,mshtml="')
    }

    scm {
       git {
            remote {
                url(gitUrl)
                branch(branchName)
                name('origin')
            }
         extensions {
            localBranch(branchName)
         }
       }
    }
    multiline='''
    def result = "git config --global --get user.name".execute().text;
    if (result.isEmpty()){
        'git config --global user.name "Jenkins Bot" && git config --global user.email bot@localhost'.execute().text;
    }
mono $WORKSPACE/build/oscript/bin/oscript.exe $WORKSPACE/build/oscript/lib/src/gitsync/src/gitsync.os export $WORKSPACE/build/repo $WORKSPACE/cf/ -debug off -format hierarchical
#-gitpublish off
    '''
    steps{
        copyArtifacts("check-$viewname") {
            buildSelector {
                    latestSuccessful(true)
                }
        }
        copyArtifacts("utils"){
            buildSelector {
                latestSuccessful(true)
            }
        }
        shell(multiline)
   }
   publishers {
        git {
            pushOnlyIfSuccess()
            branch('origin', branchName)
        }
   }
}

job("sync-win-$viewname") {
   logRotator(-1, 10)
   label("win")

    environmentVariables {
        overrideBuildParameters(true)
        envs(RDATA:repoOnecPath)
    }

    scm {
       git {
            remote {
                url(gitUrl)
                branch(branchName)
                name('origin')
            }
        extensions {
            localBranch(branchName)
        }
      }
    }
    multiline='''
chcp 1251
git config --local user.name "Jenkins Bot" && git config --local user.email bot@localhost
%WORKSPACE%/build/oscript/bin/oscript.exe %WORKSPACE%/build/oscript/lib/src/gitsync/src/gitsync.os export WORKSPACE%/build/repo WORKSPACE%/cf/ -debug off -format hierarchical
    '''
    steps{
        copyArtifacts("check-$viewname") {
            buildSelector {
                    latestSuccessful(true)
                }
        }
        copyArtifacts("utils"){
            buildSelector {
                latestSuccessful(true)
            }
        }
        batchFile(multiline)
   }
   publishers {
        git {
            pushOnlyIfSuccess()
            branch('origin', branchName)
        }
   }
}


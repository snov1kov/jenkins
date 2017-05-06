job("utils") {
    
    logRotator(-1, 4)
    
    triggers {
        cron('@daily')
    }

    scm {
        git {
            remote {
                url("https://github.com/EvilBeaver/oscript-library.git")
              	branch("*/develop")
                name('origin')
            }
            extensions {
                localBranch("develop")
                submoduleOptions {
                    disable(true)
                }
            }
        }
    }

multilineGroovy = '''import jenkins.*;
import jenkins.model.*;
import hudson.* ;
import hudson.model.*;
import hudson.plugins.groovy.GroovyInstallation;
import hudson.plugins.groovy.*;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;

def groovyDesc = jenkins.model.Jenkins.instance.getExtensionList(hudson.plugins.groovy.Groovy.DescriptorImpl.class)[0]

def isp = new InstallSourceProperty()
def autoInstaller = new hudson.plugins.groovy.GroovyInstaller("2.4.7")
isp.installers.add(autoInstaller)

def proplist = new DescribableList<ToolProperty<?>, ToolPropertyDescriptor>()
proplist.add(isp)

def installation = new GroovyInstallation("2.4.7", "", proplist)

groovyDesc.setInstallations(installation)
groovyDesc.save();
println "install system groovy";
'''

multiline='''def download(String remoteUrl, String localUrl) {
  new File("$localUrl").withOutputStream { out ->
      new URL(remoteUrl).withInputStream { from ->  out << from; }
  }
}
download("http://oscript.io/downloads/night-build/zip", "oscript.zip");

def ant = new AntBuilder();
ant.mkdir(dir:"./build/oscript/lib/");

//proc = "git submodule update --init --recursive".execute();
//proc.waitFor();
//println proc.text

proc = "git submodule update --init --remote --recursive".execute();
proc.waitFor();
println proc.text


ant.copydir(src: "./src", dest: "./build/oscript/lib/")

ant.unzip( src:"oscript.zip",
          dest: "./build/oscript1/",
         overwrite:"true", 
         encoding:"cp1251");
String sourceDir = "./build/oscript1/bin";
String destinationDir = "./build/oscript/bin";
ant.copy(todir: destinationDir) {
        fileset(dir: sourceDir)
}

directory = new File("./build/oscript1");
directory.deleteDir();

proc = "git clone --recursive https://github.com/oscript-library/deployka.git ./build/oscript/lib/deployka -b develop".execute();
proc.waitFor();
println proc.text;

//f = new File('./build/oscript/bin/oscript.cfg');
//f.append('\\nlib.system = ../lib/src\\n');
'''

   steps{
       systemGroovyCommand(multilineGroovy)
        groovyCommand(multiline) {
            groovyInstallation('2.4.7')
        }
   }
      
    publishers {
        archiveArtifacts('build/oscript/**')
    }

}

job("9.init-updatejenkins") {
    
    logRotator(-1, 4)
    
    triggers {
        cron('@daily')
    }
    
   steps{
       dsl('''
import jenkins.*;
import hudson.*;
import jenkins.model.*;
import hudson.model.*;

pm = Jenkins.instance.pluginManager
pm.doCheckUpdatesServer()
plugins = pm.plugins
plugins = Jenkins.instance.updateCenter.getUpdates()

def needRestart = false;
plugins.each {
  
  
  println it.name;
  Jenkins.instance.updateCenter.getPlugin(it.name).getNeededDependencies().each {
    println " -->"+it.name;
    it.deploy()
  }
  needRestart = true;
  it.deploy()
  
}

if (Jenkins.instance.updateCenter.isRestartRequiredForCompletion() || needRestart) {
    hudson.model.Hudson.instance.doSafeRestart(null)
}
''')
   }
}


queue('utils')

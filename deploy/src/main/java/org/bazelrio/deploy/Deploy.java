package org.bazelrio.deploy;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session.Command;

@AutoBazelRepository
class Deploy {
  // Steps in addition to copying shared libraries
  // 1. Deploy binary
  // 2. Reboot code
  private static final int EXTRA_STEPS = 2;

  private final ProgressBar m_mainProgressBar;
  private final Deployer m_deployer;

  private final List<String> m_dynamicLibraryPaths;
  private final File m_robotBinary;
  private final String m_robotBinaryDestination;

  private final boolean m_verbose;
  private final boolean m_dryRun;

  private static final String EXPECTED_JRE_VERSION = "17.0.9u7-1";
  private static final String JRE_ARTIFACT =
      "external/roborio_jre/file/roborio_jre";

  private static final class DryRunResponses {
    private static final String HAS_JRE_RESPONSE = "MISSING";
    // private static final String HAS_JRE_RESPONSE = "OK";

    // private static final String JRE_VERSION = "BadVersion";
    private static final String JRE_VERSION = EXPECTED_JRE_VERSION;

    // private static final String ROBORIO_IMAGE = "IMAGEVERSION = \"FRC_roboRIO_2023_v3.1\"";
    private static final String ROBORIO_IMAGE = "IMAGEVERSION = \"FRC_roboRIO_2024_v1.1\"";
  }

  Deploy(File robotBinary, List<String> dynamicLibraryPaths, boolean verbose, boolean dryRun)
      throws IOException {
    m_mainProgressBar =
        new ProgressBarBuilder()
            .setTaskName("Deploying")
            .setInitialMax(dynamicLibraryPaths.size() + EXTRA_STEPS)
            .setStyle(ProgressBarStyle.ASCII)
            .setUpdateIntervalMillis(100)
            .build();
    m_mainProgressBar.stepTo(0);

    m_deployer = new Deployer(verbose, dryRun);

    m_verbose = verbose;
    m_dryRun = dryRun;
    m_dynamicLibraryPaths = dynamicLibraryPaths;
    m_robotBinary = robotBinary;
    m_robotBinaryDestination = String.format("/home/lvuser/%s", robotBinary.getName());
  }

  public void deploy(int teamNumber, boolean isJava, Runfiles runfiles) throws IOException {
    if (!m_deployer.establishSession(teamNumber)) {
      System.err.println("Couldn't find a roboRIO");
      System.exit(-1);
    }

    RoborioImageValidator fwChecker = new RoborioImageValidator(m_dryRun, m_verbose, DryRunResponses.ROBORIO_IMAGE);
    if (!fwChecker.checkRoborioVersion(m_deployer)) {
      System.err.println("Coudln't verify roboRIO image");
      System.exit(-1);
    }

    // Stop and remove existing robot binary
    killRobotProgram();

    if (isJava) {
      maybeSetupJre(runfiles);
    }

    // Write new robotCommand
    setupRobotCommand(isJava);

    // Copy new robot binary
    deployRobotBinary(isJava);

    // Copy dynamic libraries
    deployDynamicLibraries(runfiles);

    // Restart robot code
    startRobotProgram();
    System.out.println("Done.");
    m_mainProgressBar.close();
    System.out.print("Deploy completed!");
  }

  private void killRobotProgram() throws IOException {
    if (m_verbose) {
      System.out.println("\nKill Roborio Program");
    }
    m_deployer.runCommand(
        ". /etc/profile.d/natinst-path.sh; /usr/local/frc/bin/frcKillRobot.sh -t");
    m_deployer.runCommand(String.format("rm -f %s", m_robotBinaryDestination));
  }

  private void setupRobotCommand(boolean isJava) throws IOException {
    if (m_verbose) {
      System.out.println("\nDepoly Robot Command");
    }

    if (isJava) {
      m_deployer.runCommand(
          "echo '"
              + "/usr/local/frc/JRE/bin/java "
              + "-XX:+UseSerialGC "
              + "-Djava.lang.invoke.stringConcat=BC_SB "
              + "-Djava.library.path=/usr/local/frc/third-party/lib "
              + " -jar "
              + m_robotBinaryDestination
              + "' > /home/lvuser/robotCommand");
    } else {
      m_deployer.runCommand("echo " + m_robotBinaryDestination + " > /home/lvuser/robotCommand");
    }
    m_deployer.runCommand(
        "chmod +x /home/lvuser/robotCommand; chown lvuser /home/lvuser/robotCommand");
  }

  private void maybeSetupJre(Runfiles runfiles) throws IOException {
    if (m_verbose) {
      System.out.println("\nChecking JRE on roboRIO");
    }

    Command command =
        m_deployer.runCommand(
            "if [[ -f \"/usr/local/frc/JRE/bin/java\" ]]; then echo OK; else echo MISSING; fi");
    String response;
    if (m_dryRun) {
      response = DryRunResponses.HAS_JRE_RESPONSE;
    } else {
      response = IOUtils.readFully(command.getInputStream()).toString();
    }

    if (m_verbose) {
      System.out.println("     └── " + response);
    }

    if ("MISSING".equals(response)) {
      installJre(runfiles);
    } else if ("OK".equals(response)) {
      Command jreVersionCommand = m_deployer.runCommand("opkg list-installed | grep openjdk");
      String jreVersion;
      if (m_dryRun) {
        jreVersion = DryRunResponses.JRE_VERSION;
      } else {
        jreVersion = IOUtils.readFully(jreVersionCommand.getInputStream()).toString();
      }

      if (EXPECTED_JRE_VERSION.equals(jreVersion)) {
        if (m_verbose) {
          System.out.println("     └── JRE Versions Match");
        }
      } else {
        if (m_verbose) {
          System.out.println(
              "     └── Current JRE version "
                  + jreVersion
                  + " does not match expected "
                  + EXPECTED_JRE_VERSION);
        }
        installJre(runfiles);
      }
    }
  }

  private void installJre(Runfiles runfiles) throws IOException {
    if (m_verbose) {
      System.out.println("\nInstalling JRE");
    }
    File jreFile = runfile(runfiles, JRE_ARTIFACT);
    m_deployer.copyFile(jreFile, "/tmp/frcjre.ipk");
    m_deployer.runCommand(
        "opkg remove frc*-openjdk*; opkg install /tmp/frcjre.ipk; rm /tmp/frcjre.ipk @ /tmp");
  }

  private void deployRobotBinary(boolean isJava) throws IOException {
    m_mainProgressBar.setExtraMessage(m_robotBinary.getName());
    if (m_verbose) {
      System.out.println("\nDeploy Robot Program");
    }
    m_deployer.copyFile(m_robotBinary, m_robotBinaryDestination);
    m_deployer.runCommand(String.format("chmod +x %s", m_robotBinaryDestination));
    m_deployer.runCommand(String.format("chown lvuser:ni %s", m_robotBinaryDestination));

    if (!isJava) {
      m_deployer.runCommand(String.format("setcap cap_sys_nice+eip %s", m_robotBinaryDestination));
    }

    m_mainProgressBar.step();
  }

  private void deployDynamicLibraries(Runfiles runfiles) throws IOException {
    if (m_verbose) {
      System.out.println("\nDeploy Native Libraries");
    }

    for (String dynamicLibraryPath : m_dynamicLibraryPaths) {
      File dynamicLibrary = new File(dynamicLibraryPath);
      m_mainProgressBar.setExtraMessage("Deploying " + dynamicLibrary.getName());
      String dynamicLibraryDestination =
          String.format("/usr/local/frc/third-party/lib/%s", dynamicLibrary.getName());
      m_deployer.copyFile(dynamicLibrary, dynamicLibraryDestination);
      m_mainProgressBar.step();
    }

    m_deployer.runCommand(
        "chmod -R 777 \"/usr/local/frc/third-party/lib\" || true; "
            + "chown -R lvuser:ni \"/usr/local/frc/third-party/lib\"");
    m_deployer.runCommand("ldconfig");
  }

  private void startRobotProgram() throws IOException {
    System.out.print("\nRestarting robot code... ");
    m_mainProgressBar.setExtraMessage("Restarting robot code");
    m_deployer.runCommand("sync");
    m_deployer.runCommand(
        ". /etc/profile.d/natinst-path.sh; /usr/local/frc/bin/frcKillRobot.sh -t -r");
    m_mainProgressBar.step();
  }

  public static File runfile(Runfiles runfiles, String location) {
    Path runfilePath = Path.of("__main__").resolve(location).normalize();
    ArrayList<String> pathParts = new ArrayList<>();
    runfilePath.iterator().forEachRemaining(part -> pathParts.add(part.toString()));
    return new File(runfiles.rlocation(String.join("/", pathParts)));
  }
}

package org.bazelrio.deploy;

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPUploadClient;

public class Deployer {
  private final SSHClient m_client;
  private SCPUploadClient m_scp;

  protected final boolean m_dryRun;
  protected final boolean m_verbose;

  private static class CommandFailedException extends IOException {
    CommandFailedException(String message) {
      super(message);
    }
  }

  private static class NoopKeyVerifier implements HostKeyVerifier {
    public List<String> findExistingAlgorithms(String hostname, int port) {
      return new ArrayList<>();
    }

    public boolean verify(String hostname, int port, PublicKey key) {
      return true;
    }
  }

  protected Deployer(boolean verbose, boolean dryRun) throws IOException {
    m_verbose = verbose;
    m_dryRun = dryRun;

    m_client = new SSHClient();
    m_client.addHostKeyVerifier(new NoopKeyVerifier());
    m_client.useCompression();
  }

  public boolean establishSession(int teamNumber) {
    String[] addresses = {
      String.format("roborio-%d-frc.local", teamNumber),
      String.format("10.%d.%d.2", teamNumber / 100, teamNumber % 100),
      "172.22.11.2",
      String.format("roborio-%d-frc", teamNumber),
      String.format("roborio-%d-frc.lan", teamNumber),
      String.format("roborio-%d-frc.frc-field.local", teamNumber),
    };

    ProgressBar progressBar =
        new ProgressBarBuilder()
            .setTaskName("roboRIO Search")
            .setInitialMax(addresses.length)
            .setStyle(ProgressBarStyle.ASCII)
            .setUpdateIntervalMillis(100)
            .build();
    progressBar.stepTo(0);

    for (String address : addresses) {
      progressBar.setExtraMessage(address);
      progressBar.step();
      if (m_verbose) {
        System.out.println(String.format("Attempting to connect to %s", address));
      }
      if (attemptConnection(address)) {
        progressBar.stepTo(addresses.length);
        progressBar.close();
        return true;
      }
    }

    progressBar.close();
    return false;
  }

  public Command runCommand(String commandString) throws IOException {
    if (m_client == null) {
      throw new RuntimeException("Bad setup");
    }

    if (m_verbose) {
      System.out.println("    -C-> " + commandString);
    }

    if (m_dryRun) {
      return null;
    }

    Command command = m_client.startSession().exec(commandString);
    command.join();
    int exitStatus = command.getExitStatus();
    if (exitStatus != 0) {
      throw new CommandFailedException(
          String.format("Command %s exited with code %d", commandString, exitStatus));
    }
    // String stdout = IOUtils.readFully(command.getInputStream()).toString();
    return command;
  }

  public void copyFile(File source, String destination) throws IOException {
    copyFile(new FileSystemFile(source), destination);
  }

  public void copyFile(FileSystemFile source, String destination) throws IOException {
    if (m_verbose) {
      System.out.println(
          "    -F-> "
              + source.toString().replace("C:\\users\\pj\\_bazel_pj\\45vg57e7\\external\\", "")
              + " to '"
              + destination
              + "'");
    }

    if (!m_dryRun) {
      m_scp.copy(source, destination);
    }
  }

  private boolean attemptConnection(String address) {
    if (m_dryRun) {
      return true;
    }

    try {
      m_client.setConnectTimeout(1500);
      m_client.connect(address);
      m_client.authPassword("admin", "");
      m_scp = m_client.newSCPFileTransfer().newSCPUploadClient();

      return true;
    } catch (IOException e) {
      if (m_verbose) {
        System.err.println(String.format("Error connecting to %s: %s", address, e));
      }
    }
    return false;
  }
}

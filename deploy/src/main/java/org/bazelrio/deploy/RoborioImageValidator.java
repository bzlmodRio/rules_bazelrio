package org.bazelrio.deploy;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.schmizz.sshj.common.IOUtils;

public class RoborioImageValidator {
  private static final List<String> VALID_IMAGE_VERSIONS = List.of("2024_v1.*");

  private final boolean m_dryRun;
  private final boolean m_verbose;
  private final String m_mockResponse;

  public RoborioImageValidator(boolean dryRun, boolean verbose, String mockResponse) {
    m_dryRun = dryRun;
    m_verbose = verbose;
    m_mockResponse = mockResponse;
  }

  public boolean checkRoborioVersion(Deployer deployer) throws IOException {
    if (m_verbose) {
      System.out.println("Checking roboRIO fw version");
    }
    String imageFile = "/etc/natinst/share/scs_imagemetadata.ini";
    final Pattern pattern =
        Pattern.compile(
            "^IMAGEVERSION\\s*=\\s*\\\"(FRC_)?roboRIO2?_(?<version>\\d{4}_v\\d+(?:\\.\\d+)?)\\\"");

    String content;
    if (m_dryRun) {
      content = m_mockResponse;
    } else {
      content = IOUtils.readFully(deployer.runCommand("cat " + imageFile).getInputStream()).toString();
    }

    for (String line : content.split("\n")) {
      if (m_verbose) {
        System.out.println("    └── Checking line " + line);
      }
      Matcher matcher = pattern.matcher(line.trim());
      if (matcher.matches()) {
        String imageGroup = matcher.group("version");
        if (m_verbose) {
          System.out.println("      └── found version " + imageGroup);
        }
        return verifyImageVersion(imageGroup);
      }
    }

    System.err.println("Could not find image");
    return false;
  }

  private boolean verifyImageVersion(String image) {
    boolean foundMatch =
        VALID_IMAGE_VERSIONS.stream()
            .filter(
                x -> {
                  int index = x.indexOf("*");
                  if (index == -1) {
                    // no wildcard, check if versions are equal
                    return x.equals(image);
                  } else if (index > image.length()) {
                    return false;
                  } else {
                    return x.substring(0, index).equals(image.substring(0, index));
                  }
                })
            .findAny()
            .isPresent();

    if (!foundMatch) {
      throw new RuntimeException(
          "Invalid version! Got "
              + image
              + " which is not in the expected list "
              + VALID_IMAGE_VERSIONS);
    }
    return true;
  }
}

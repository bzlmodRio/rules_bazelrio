load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_COMMITISH = "4.4.2"

RULES_JVM_EXTERNAL_SHA = "735602f50813eb2ea93ca3f5e43b1959bd80b213b836a07a62a29d757670b77b"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-{}".format(RULES_JVM_EXTERNAL_COMMITISH),
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/{}.zip".format(RULES_JVM_EXTERNAL_COMMITISH),
)

load("//:nonbzlmod_setup.bzl", "setup_rules_bazelrio")

setup_rules_bazelrio()
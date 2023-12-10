import os
import argparse
from bazelrio_gentool.cli import add_generic_cli, GenericCliArgs
from bazelrio_gentool.clean_existing_version import clean_existing_version
from bazelrio_gentool.generate_shared_files import get_bazel_dependencies
from bazelrio_gentool.generate_shared_files import (
    write_shared_root_files,
    write_shared_test_files,
)
from bazelrio_gentool.utils import (
    TEMPLATE_BASE_DIR,
    write_file,
    render_template,
    render_templates,
)
from get_group import get_rules_bazelrio_group


def main():
    SCRIPT_DIR = os.environ["BUILD_WORKSPACE_DIRECTORY"]
    REPO_DIR = os.path.join(SCRIPT_DIR, "..")

    parser = argparse.ArgumentParser()
    add_generic_cli(parser)
    args = parser.parse_args()

    clean_existing_version(
        REPO_DIR,
        extra_dir_blacklist=["deploy", "conditions", "private"],
        file_blacklist=[
            "halsim_defs.bzl",
            "java_rules.bzl",
            "nonbzlmod_setup.bzl",
            "robot_rules.bzl",
        ],
    )

    group = get_rules_bazelrio_group()

    write_shared_root_files(REPO_DIR, group)
    write_shared_test_files(REPO_DIR, group)

    template_files = [
        "WORKSPACE",
        "MODULE.bazel",
        "tests/WORKSPACE",
        "tests/MODULE.bazel",
    ]
    print(get_rules_bazelrio_group())

    render_templates(
        template_files,
        REPO_DIR,
        os.path.join(SCRIPT_DIR, "templates"),
        group=group,
        bazel_dependencies=get_bazel_dependencies(),
    )


if __name__ == "__main__":
    main()

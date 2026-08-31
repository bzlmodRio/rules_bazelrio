import argparse
import os

from bazelrio_gentool.clean_existing_version import clean_existing_version
from bazelrio_gentool.cli import GenericCliArgs, add_generic_cli
from bazelrio_gentool.generate_module_project_files import (
    create_default_mandatory_settings,
)
from bazelrio_gentool.generate_shared_files import (
    get_bazel_dependencies,
    write_shared_root_files,
    write_shared_test_files,
)
from bazelrio_gentool.manual_cleanup_helper import manual_cleanup_helper
from bazelrio_gentool.utils import TEMPLATE_BASE_DIR, render_templates
from get_group import get_rules_bazelrio_group


def main():
    SCRIPT_DIR = os.environ["BUILD_WORKSPACE_DIRECTORY"]
    REPO_DIR = os.path.join(SCRIPT_DIR, "..")

    parser = argparse.ArgumentParser()
    add_generic_cli(parser)
    args = parser.parse_args()

    mandatory_dependencies = create_default_mandatory_settings(GenericCliArgs(args))

    clean_existing_version(
        REPO_DIR,
        extra_dir_blacklist=["deploy", "conditions", "private", "jnidebugger"],
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
        "MODULE.bazel",
        "tests/MODULE.bazel",
    ]

    render_templates(
        template_files,
        REPO_DIR,
        os.path.join(SCRIPT_DIR, "templates"),
        group=group,
        bazel_dependencies=get_bazel_dependencies(),
        mandatory_dependencies=mandatory_dependencies,
    )

    template_files = [
        ".bazelrc-java",
        "tests/.bazelrc-java",
    ]
    render_templates(
        template_files, REPO_DIR, os.path.join(TEMPLATE_BASE_DIR, "library_wrapper")
    )

    manual_fixes(REPO_DIR)


def manual_fixes(repo_dir):
    manual_cleanup_helper(
        os.path.join(repo_dir, ".bazelrc-java"),
        lambda contents: contents.replace(
            "# build --javacopt=-Werror",
            "build --javacopt=-Werror",
        ),
    )

    manual_cleanup_helper(
        os.path.join(repo_dir, "tests", ".bazelrc-java"),
        lambda contents: contents.replace(
            "# build --javacopt=-Werror",
            "build --javacopt=-Werror",
        ),
    )

    manual_cleanup_helper(
        os.path.join(repo_dir, ".github", "workflows", "build.yml"),
        lambda contents: contents.replace(
            'command: "test"',
            'command: "build"',
        ),
    )


if __name__ == "__main__":
    main()

load("@rules_bazelrio//:halsim_defs.bzl", "halsim_cc_binary", "halsim_java_binary")
load("@rules_bazelrio//private:get_dynamic_deps.bzl", "get_dynamic_deps")
load("@rules_cc//cc:defs.bzl", "cc_binary")
load("@rules_java//java:defs.bzl", "java_binary")

def _get_dynamic_dependencies_impl(ctx):
    shared_lib_native_deps = get_dynamic_deps(ctx.attr.target)
    return [DefaultInfo(files = depset(shared_lib_native_deps))]

_get_dynamic_dependencies = rule(
    attrs = {
        "target": attr.label(
            mandatory = True,
        ),
    },
    implementation = _get_dynamic_dependencies_impl,
)

def __other_deploy_thing_impl(ctx):
    print("Other deploy")

    #     bin_name = ":robot"
    dry_run = True
    verbose = True
    skip_dynamic_libraries = False
    is_java = False
    team_number = 213

    output_file = ctx.actions.declare_file(ctx.label.name + ".output")

    data = [bin_name]
    # if is_java:
    #     data.append("@roborio_jre//file")

    inputs = []
    inputs.append(ctx.files.robot_binary[0])
    inputs.extend(ctx.files.dynamic_deps)

    args = ctx.actions.args()
    args.add("--robot_binary", ctx.files.robot_binary[0].path)
    args.add("--team_number", team_number)
    args.add("--dynamic_libraries")
    for dep in ctx.files.dynamic_deps:
        args.add(dep.path)

    print(args)

    if dry_run:
        args.add("--dry_run")
    if verbose:
        args.add("--verbose")
    if skip_dynamic_libraries:
        args.add("--skip_dynamic_libraries")
    if is_java:
        args.add("--is_java")

    ctx.actions.run(
        mnemonic = "ExampleCompile",
        executable = ctx.executable._tool,
        arguments = [args],
        inputs = inputs,
        outputs = [output_file],
    )

    return [DefaultInfo(
        executable = output_file,
    )]

other_deploy_thing = rule(
    implementation = __other_deploy_thing_impl,
    attrs = {
        "dynamic_deps": attr.label(
            mandatory = True,
        ),
        "robot_binary": attr.label(
            mandatory = True,
            allow_single_file = True,
        ),
        "_tool": attr.label(
            default = Label("@rules_bazelrio//deploy"),
            # allow_single_file = True,
            executable = True,
            cfg = "exec",
        ),
    },
    executable = True,
)

def __call_deploy_command(name, bin_name, lib_name, team_number, visibility, skip_dynamic_libraries, is_java, dry_run, verbose):
    discover_dynamic_deps_task_name = lib_name + ".discover_dynamic_deps"
    _get_dynamic_dependencies(
        name = discover_dynamic_deps_task_name,
        target = lib_name,
    )

    other_deploy_thing(
        name = name + ".deploy",
        robot_binary = name + "_deploy.jar",
        dynamic_deps = discover_dynamic_deps_task_name,
        # lib_name = name,
        # team_number = team_number,
        # visibility = visibility,
        # skip_dynamic_libraries = skip_dynamic_libraries,
        # is_java = True,
        # dry_run = dry_run,
        # verbose = verbose,
    )
    # data = [bin_name, discover_dynamic_deps_task_name]
    # if is_java:
    #     data.append("@roborio_jre//file")

def robot_cc_binary(name, team_number, lib_name, halsim_deps = [], visibility = None, skip_dynamic_libraries = False, dry_run = False, verbose = False, **kwargs):
    deps = [":" + lib_name]

    cc_binary(
        name = name,
        deps = deps,
        visibility = visibility,
        **kwargs
    )

    if halsim_deps:
        halsim_cc_binary(
            name = name + ".sim",
            halsim_deps = halsim_deps,
            deps = deps,
            visibility = visibility,
        )

    # _deploy_command(
    #     name = name + ".deploy",
    #     bin_name = name,
    #     lib_name = lib_name,
    #     team_number = team_number,
    #     robot_command = "{}",
    #     visibility = visibility,
    #     is_java = False,
    #     dry_run = dry_run,
    #     verbose = verbose,
    # )

def robot_java_binary(name, team_number, main_class, runtime_deps = [], halsim_deps = [], visibility = None, skip_dynamic_libraries = False, dry_run = False, verbose = False, **kwargs):
    java_binary(
        name = name,
        main_class = main_class,
        runtime_deps = runtime_deps,
        visibility = visibility,
        **kwargs
    )

    if halsim_deps:
        halsim_java_binary(
            visibility = visibility,
            name = name + ".sim",
            halsim_deps = halsim_deps,
            main_class = main_class,
            runtime_deps = runtime_deps,
            tags = ["no-sandbox"],
            jvm_flags = [
                "-Djava.library.path=.",
            ],
        )

    __call_deploy_command(
        name = name,
        bin_name = name + "_deploy.jar",
        lib_name = name,
        team_number = team_number,
        visibility = visibility,
        skip_dynamic_libraries = skip_dynamic_libraries,
        is_java = True,
        dry_run = dry_run,
        verbose = verbose,
    )

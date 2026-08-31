load("@rules_cc//cc/common:cc_info.bzl", "CcInfo")
load("@rules_java//java/common:java_info.bzl", "JavaInfo")

def get_dynamic_deps(target):
    shared_lib_native_deps = []

    if CcInfo in target:
        for linker_input in target[CcInfo].linking_context.linker_inputs.to_list():
            for library in linker_input.libraries:
                if library.dynamic_library and not library.static_library:
                    shared_lib_native_deps.append(library.dynamic_library)
    if JavaInfo in target:
        for library in target[JavaInfo].transitive_native_libraries.to_list():
            if library.dynamic_library and not library.static_library:
                shared_lib_native_deps.append(library.dynamic_library)

    return shared_lib_native_deps

def _get_dynamic_dependencies_impl(ctx):
    shared_lib_native_deps = get_dynamic_deps(ctx.attr.target)

    return [DefaultInfo(files = depset(shared_lib_native_deps))]

get_dynamic_dependencies = rule(
    attrs = {
        "target": attr.label(
            mandatory = True,
        ),
    },
    implementation = _get_dynamic_dependencies_impl,
)

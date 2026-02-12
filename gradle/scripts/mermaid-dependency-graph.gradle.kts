tasks.register("generateMermaidGraph") {
    group = "help"
    description = "Generates a Mermaid dependency graph of project modules."

    // Collect data at configuration time to be more compatible with modern Gradle
    // though using project at execution time is still tricky in script plugins.
    val currentProject = project
    val isRoot = currentProject == rootProject
    val subProjects = if (isRoot) subprojects.toList() else emptyList()

    doLast {
        val edges = mutableSetOf<Pair<String, String>>()
        val visited = mutableSetOf<Project>()

        fun collectDependencies(p: Project) {
            if (!visited.add(p)) return

            p.configurations.forEach { config ->
                config.dependencies.withType<ProjectDependency>().forEach { dep ->
                    val depProject = dep.dependencyProject
                    if (p.path != depProject.path) {
                        edges.add(p.path to depProject.path)
                        collectDependencies(depProject)
                    }
                }
            }
        }

        if (isRoot) {
            subProjects.forEach { collectDependencies(it) }
        } else {
            collectDependencies(currentProject)
        }

        println("\n```mermaid")
        println("graph TD")
        edges.sortedBy { "${it.first}-->${it.second}" }.forEach { (from, to) ->
            val fromId = from.removePrefix(":").replace(":", "_").replace("-", "_").ifEmpty { "root" }
            val toId = to.removePrefix(":").replace(":", "_").replace("-", "_")
            println("    $fromId --> $toId")
        }
        println("```\n")
    }
}

tasks.register("generateDotGraph") {
    group = "help"
    description = "Generates a DOT dependency graph of project modules."

    val currentProject = project
    val edges = mutableSetOf<Pair<String, String>>()
    val visited = mutableSetOf<String>()
    val entryPoints = mutableSetOf<String>()
    val thirdPartyNodes = mutableSetOf<String>()

    // Define third-party libraries to track
    // Key: Group ID (e.g., "io.ktor") or exact "Group:Name" (e.g., "androidx.compose.ui:ui")
    // Value: Display Name for the graph
    val thirdPartyLibs = mapOf(
        //"io.ktor" to "Ktor",
        //"org.jetbrains.kotlinx:kotlinx-coroutines-core" to "Coroutines",
        "org.jetbrains.kotlinx:kotlinx-serialization-json" to "Serialization",
        "dev.onvoid.webrtc" to "WebRTC Java",
        //"org.jetbrains.androidx.lifecycle" to "Lifecycle",
        //"org.jetbrains.androidx.navigation" to "Navigation",
        //"ch.qos.logback" to "Logback"
    )

    fun collectDependencies(p: Project) {
        if (!visited.add(p.path)) return

        // Detect entry points (Application modules)
        if (p.plugins.hasPlugin("application") || p.plugins.hasPlugin("com.android.application")) {
            entryPoints.add(p.path)
        }

        p.configurations.forEach { config ->
            // Project Dependencies
            config.dependencies.withType<ProjectDependency>().forEach { dep ->
                val depProject = dep.dependencyProject
                val from = p.path
                val to = depProject.path
                if (from != to) {
                    edges.add(from to to)
                    collectDependencies(depProject)
                }
            }

            // Third-party Dependencies
            config.dependencies.withType<ExternalModuleDependency>().forEach { dep ->
                val group = dep.group ?: return@forEach
                val name = dep.name
                val id = "$group:$name"
                
                var label: String? = null
                
                // 1. Try exact match (Group:Name)
                if (thirdPartyLibs.containsKey(id)) {
                    label = thirdPartyLibs[id]
                } 
                // 2. Try group match (starts with)
                else {
                    val groupMatch = thirdPartyLibs.entries.find { (k, _) ->
                        !k.contains(":") && (group == k || group.startsWith("$k."))
                    }
                    if (groupMatch != null) {
                        label = groupMatch.value
                    }
                }

                if (label != null) {
                    edges.add(p.path to label)
                    thirdPartyNodes.add(label)
                }
            }
        }
    }

    if (currentProject == rootProject) {
        currentProject.subprojects.forEach { collectDependencies(it) }
    } else {
        collectDependencies(currentProject)
    }

    doLast {
        println("\n// Copy content below into a .dot file or viewer")
        println("digraph ProjectDependencies {")
        println("  // Global graph attributes")
        println("  rankdir=LR;")
        println("  node [shape=box, style=\"rounded,filled\", fillcolor=\"#E3F2FD\", fontname=\"Helvetica\"];")
        println("  edge [color=\"#546E7A\"];")
        println("")

        // Highlight entry points
        if (entryPoints.isNotEmpty()) {
            println("  // Entry Points")
            entryPoints.sorted().forEach { entryPoint ->
                println("  \"$entryPoint\" [fillcolor=\"#C8E6C9\"];")
            }
            println("")
        }

        // Highlight Third-party Nodes
        if (thirdPartyNodes.isNotEmpty()) {
            println("  // Third-party Libraries")
            println("  node [fillcolor=\"#FFF9C4\", shape=ellipse];") // Light yellow, ellipse
            thirdPartyNodes.sorted().forEach { node ->
                println("  \"$node\";")
            }
            println("  node [shape=box, style=\"rounded,filled\", fillcolor=\"#E3F2FD\"]; // Reset style")
            println("")
        }
        
        edges.sortedBy { "${it.first}->${it.second}" }.forEach { (from, to) ->
            println("  \"$from\" -> \"$to\";")
        }
        println("}\n")
    }
}

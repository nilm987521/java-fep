import org.gradle.api.tasks.Exec

plugins {
    base
}

tasks.register<Exec>("npmInstall") {
    description = "Install npm dependencies"
    workingDir = projectDir
    commandLine("npm", "install")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

tasks.register<Exec>("build") {
    description = "Build the webview"
    workingDir = projectDir
    commandLine("npm", "run", "build")
    dependsOn("npmInstall")
    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("tsconfig.json")
    inputs.file("webpack.config.js")
    outputs.dir("dist")
}

tasks.register<Exec>("watch") {
    description = "Watch for changes and rebuild"
    workingDir = projectDir
    commandLine("npm", "run", "watch")
    dependsOn("npmInstall")
}

tasks.register<Delete>("clean") {
    delete("dist", "node_modules")
}

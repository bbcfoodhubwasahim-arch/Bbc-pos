tasks.register("clean") {
    doLast {
        delete(layout.buildDirectory)
    }
}

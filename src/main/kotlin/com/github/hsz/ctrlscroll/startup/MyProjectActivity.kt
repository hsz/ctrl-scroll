package com.github.hsz.ctrlscroll.startup

import com.github.hsz.ctrlscroll.services.CtrlScrollService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Activity that runs when a project is opened.
 * Initializes the CtrlScrollService.
 */
class MyProjectActivity : ProjectActivity {
    private val log = logger<MyProjectActivity>()

    override suspend fun execute(project: Project) {
        log.info("Initializing CtrlScrollService for project: ${project.name}")
        // Get the service instance to initialize it
        CtrlScrollService.getInstance()
    }
}

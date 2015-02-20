package nebula.plugin.dependencylock.tasks

import groovy.json.JsonSlurper
import org.apache.log4j.spi.LoggerFactory
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.lang.Override
import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.options.Option

/**
 * The update task is a generate task, it simply reads in the old locked dependencies and then overwrites the desired
 * dependencies per user request.
 */
class UpdateLockTask extends GenerateLockTask {
    private static Logger logger = Logging.getLogger(UpdateLockTask)

    String description = 'Apply updates to a preexisting lock file and write to build/<specified name>'
    Set<String> dependencies

    @Option(option = "dependencies", description = "Specify which dependencies to update via a comma-separated list")
    void setDependencies(String depsFromOption) {
        setDependencies(depsFromOption.tokenize(',') as Set)
    }

    void setDependencies(Set<String> dependencyList) {
        this.dependencies = dependencyList
    }

    @Override
    void lock() {
        // If the user specifies dependencies to update, ignore any filter specified by the build file and use our
        // own generated from the list of dependencies.
        def updates = getDependencies()

        if (updates) {
            filter = { group, artifact, version ->
                updates.contains("${group}:${artifact}".toString())
            }
        }
        super.lock()
    }

    @Override
    void writeLock(updatedDeps) {
        File currentLock = new File(project.projectDir, dependenciesLock.name)
        def lockedDeps = loadLock(currentLock)
        super.writeLock(lockedDeps + (updatedDeps as Map))
    }

    private static loadLock(File lock) {
        def lockKeyMap = [:].withDefault { [transitive: [] as Set, firstLevelTransitive: [] as Set, childrenVisited: false] }

        try {
            def json = new JsonSlurper().parseText(lock.text)
            json.each { key, value ->
                def (group, artifact) = key.tokenize(':')
                lockKeyMap.put(new LockKey(group: group, artifact: artifact), value)
            }
        } catch (ex) {
            logger.debug('Unreadable json file: ' + lock.text)
            logger.error('JSON unreadable')
            throw new GradleException("${lock.name} is unreadable or invalid json, terminating run", ex)
        }

        lockKeyMap
    }
}

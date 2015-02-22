package nebula.plugin.dependencylock.tasks

import nebula.plugin.dependencylock.dependencyfixture.Fixture
import nebula.test.ProjectSpec


class UpdateLockTaskSpec extends ProjectSpec {
    final String taskName = 'generateLock'

    def setupSpec() {
        Fixture.createFixtureIfNotCreated()
    }

    def setup() {
        project.apply plugin: 'java'
        project.repositories { maven { url Fixture.repo } }
    }

    UpdateLockTask createTask() {
        def task = project.tasks.create(taskName, UpdateLockTask)
        task.dependenciesLock = new File(project.buildDir, 'dependencies.lock')
        task.configurationNames = [ 'testRuntime' ]
        task
    }

    def 'default behavior is an additive update of the lock file'() {
        project.dependencies {
            compile 'test.example:foo:2.0.0'
        }

        def task = createTask()

        def lockFile = new File(project.projectDir, 'dependencies.lock')
        lockFile.text = '''\
            {
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "1.0.0", "requested": "1.0.0" }
            }
        '''.stripIndent()

        when:
        task.execute()

        then:
        String lockText = '''\
            {
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "2.0.0", "requested": "2.0.0" }
            }
        '''.stripIndent()

        task.dependenciesLock.text == lockText
    }

    def 'default behavior respects the filter'() {
        project.dependencies {
            compile 'test.example:baz:2.0.0'
            compile 'test.example:foo:2.0.0'
        }

        def task = createTask()
        task.filter = { group, artifact, version ->
            artifact == 'foo'
        }

        def lockFile = new File(project.projectDir, 'dependencies.lock')
        lockFile.text = '''\
            {
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "1.0.0", "requested": "1.0.0" }
            }
        '''.stripIndent()

        when:
        task.execute()

        then:
        String lockText = '''\
            {
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "2.0.0", "requested": "2.0.0" }
            }
        '''.stripIndent()

        task.dependenciesLock.text == lockText
    }

    def 'specifying a set of dependencies restricts the dependencies updated'(dependencies, lockText) {
        project.dependencies {
            compile 'test.example:baz:2.0.0'
            compile 'test.example:foo:2.0.0'
            compile 'test.example:bar:1.1.0'
        }

        def task = createTask()
        task.dependencies = dependencies

        def lockFile = new File(project.projectDir, 'dependencies.lock')
        lockFile.text = '''\
            {
              "test.example:bar": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "1.0.0", "requested": "1.0.0" }
            }
        '''.stripIndent()

        when:
        task.execute()

        then:
        task.dependenciesLock.text == lockText

        where:
        dependencies                                || lockText
        [ 'test.example:foo' ]                      || '''\
            {
              "test.example:bar": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "2.0.0", "requested": "2.0.0" }
            }
        '''.stripIndent()
        [ 'test.example:foo', 'test.example:baz' ]  || '''\
            {
              "test.example:bar": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:baz": { "locked": "2.0.0", "requested": "2.0.0" },
              "test.example:foo": { "locked": "2.0.0", "requested": "2.0.0" }
            }
        '''.stripIndent()
    }

    def 'when dependencies are specified, the filter is ignored' () {
        project.dependencies {
            compile 'test.example:baz:2.0.0'
            compile 'test.example:foo:2.0.0'
        }

        def task = createTask()
        task.filter = { group, artifact, version ->
            false
        }

        def lockFile = new File(project.projectDir, 'dependencies.lock')
        lockFile.text = '''\
            {
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "1.0.0", "requested": "1.0.0" }
            }
        '''.stripIndent()

        task.dependencies = [ 'test.example:foo' ]

        when:
        task.execute()

        then:
        String lockText = '''\
            {
              "test.example:baz": { "locked": "1.0.0", "requested": "1.0.0" },
              "test.example:foo": { "locked": "2.0.0", "requested": "2.0.0" }
            }
        '''.stripIndent()

        task.dependenciesLock.text == lockText
    }

    def 'dependencies can be specified via a comma-separated list'(input, dependencies) {
        def task = createTask()

        when:
        task.setDependencies(input as String)

        then:
        task.dependencies == dependencies as Set

        where:
        input                             || dependencies
        'com.example:foo'                 || ['com.example:foo']
        'com.example:baz,com.example:foo' || ['com.example:baz', 'com.example:foo']
    }
}

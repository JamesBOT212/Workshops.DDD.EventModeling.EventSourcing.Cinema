package com.dddheroes.cinema

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition
import org.junit.jupiter.api.Test

class VerticalSliceArchitectureTest {

    private val classes: JavaClasses = ClassFileImporter()
        .importPackages("com.dddheroes.cinema.modules")

    @Test
    fun `write slices should not depend on read slices`() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..write..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..read..")
            .because("Write operations should not depend on read operations to maintain CQRS separation")
            .check(classes)
    }

    @Test
    fun `read slices should not depend on write slices`() {
        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..read..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..write..")
            .because("Read operations should not depend on write operations to maintain CQRS separation")
            .check(classes)
    }

    @Test
    fun `write packages should not depend on other modules`() {
        val fromOtherModule = DescribedPredicate.describe("from other module") { targetClass: JavaClass ->
            val targetModule = extractModuleName(targetClass.packageName)
            targetModule != null &&
            classes.asSequence()
                .filter { it.packageName.contains(".modules.") && it.packageName.contains(".write.") }
                .any { sourceClass ->
                    val sourceModule = extractModuleName(sourceClass.packageName)
                    sourceModule != null &&
                    sourceModule != targetModule &&
                    !targetClass.packageName.contains(".events.") &&
                    sourceClass.directDependenciesFromSelf.any { it.targetClass == targetClass }
                }
        }

        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..modules..")
            .should()
            .dependOnClassesThat(fromOtherModule)
            .because("Write operations should only depend on their own module and events")
            .check(classes)
    }

    @Test
    fun `read packages should not depend on other modules`() {
        val fromOtherModule = DescribedPredicate.describe("from other module") { targetClass: JavaClass ->
            val targetModule = extractModuleName(targetClass.packageName)
            targetModule != null &&
            classes.asSequence()
                .filter { it.packageName.contains(".modules.") && it.packageName.contains(".read.") }
                .any { sourceClass ->
                    val sourceModule = extractModuleName(sourceClass.packageName)
                    sourceModule != null &&
                    sourceModule != targetModule &&
                    !targetClass.packageName.contains(".events.") &&
                    sourceClass.directDependenciesFromSelf.any { it.targetClass == targetClass }
                }
        }

        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..modules..")
            .should()
            .dependOnClassesThat(fromOtherModule)
            .because("Read operations should only depend on their own module and events")
            .check(classes)
    }

    @Test
    fun `events packages should not depend on other modules`() {
        val fromOtherModule = DescribedPredicate.describe("from other module") { targetClass: JavaClass ->
            val targetModule = extractModuleName(targetClass.packageName)
            targetModule != null &&
            classes.asSequence()
                .filter { it.packageName.contains(".modules.") && it.packageName.contains(".events.") }
                .any { sourceClass ->
                    val sourceModule = extractModuleName(sourceClass.packageName)
                    sourceModule != null &&
                    sourceModule != targetModule &&
                    sourceClass.directDependenciesFromSelf.any { it.targetClass == targetClass }
                }
        }

        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..modules..")
            .should()
            .dependOnClassesThat(fromOtherModule)
            .because("Events should only depend on their own module to maintain bounded context isolation")
            .check(classes)
    }

    @Test
    fun `only automation packages may depend on other modules`() {
        val fromOtherModuleNonAutomation = DescribedPredicate.describe("from other module (non-automation)") { targetClass: JavaClass ->
            val targetModule = extractModuleName(targetClass.packageName)
            targetModule != null &&
            classes.asSequence()
                .filter { it.packageName.contains(".modules.") && !it.packageName.contains(".automation.") }
                .any { sourceClass ->
                    val sourceModule = extractModuleName(sourceClass.packageName)
                    sourceModule != null &&
                    sourceModule != targetModule &&
                    sourceClass.directDependenciesFromSelf.any { it.targetClass == targetClass }
                }
        }

        ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage("..modules..")
            .should()
            .dependOnClassesThat(fromOtherModuleNonAutomation)
            .because("Only automation packages should coordinate between different modules")
            .check(classes)
    }

    private fun extractModuleName(packageName: String): String? {
        val modulePattern = Regex("""\.modules\.([^.]+)\.""")
        return modulePattern.find(packageName)?.groupValues?.get(1)
    }
}
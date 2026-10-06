package com.ballastlane.assessment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Executable version of the architecture in CLAUDE.md. No rule may pass vacuously: if a package is renamed the rule
 * matches nothing and ArchUnit fails, instead of silently checking nothing.
 */
@AnalyzeClasses(packages = "com.ballastlane.assessment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnOtherLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domainAndApplicationAreFrameworkFree = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "tools.jackson..", "com.fasterxml.jackson..");

    // Controllers talk to inbound ports only: never to outbound ports, use-case implementations or adapters.
    @ArchTest
    static final ArchRule webOnlyUsesInboundPorts = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application.port.out..", "..application.usecase..",
                    "..infrastructure.persistence..", "..infrastructure.pokeapi..");

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnWeb = noClasses()
            .that().resideInAPackage("..infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.web..");

    @ArchTest
    static final ArchRule pokeApiAdapterDoesNotDependOnWebOrPersistence = noClasses()
            .that().resideInAPackage("..infrastructure.pokeapi..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.web..", "..infrastructure.persistence..");

    @ArchTest
    static final ArchRule useCaseImplementationsAreOnlyWiredByConfig = noClasses()
            .that().resideOutsideOfPackages("..application.usecase..", "..infrastructure.config..")
            .should().dependOnClassesThat().resideInAPackage("..application.usecase..");
}

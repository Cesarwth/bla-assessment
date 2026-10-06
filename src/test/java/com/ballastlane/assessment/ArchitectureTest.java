package com.ballastlane.assessment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.ballastlane.assessment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // allowEmptyShould: layers are filled progressively; the rules must hold from day one.
    @ArchTest
    static final ArchRule domainDoesNotDependOnOtherLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainAndApplicationAreFrameworkFree = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "tools.jackson..", "com.fasterxml.jackson..")
            .allowEmptyShould(true);

    // Controllers talk to use cases; only adapters know about JPA, and the reverse.
    @ArchTest
    static final ArchRule webDoesNotDependOnPersistence = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnWeb = noClasses()
            .that().resideInAPackage("..infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.web..")
            .allowEmptyShould(true);
}

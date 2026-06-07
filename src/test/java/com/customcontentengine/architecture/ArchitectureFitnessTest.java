package com.customcontentengine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.customcontentengine", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureFitnessTest {
    private static final String DOMAIN_PACKAGE = "..domain..";
    private static final String INTERNAL_API_PACKAGE = "..internalapi..";
    private static final String APPLICATION_PACKAGE = "..application..";
    private static final String BUILTIN_PACKAGE = "..builtin..";
    private static final String ADAPTER_PACKAGE = "..adapter..";
    private static final String BOOTSTRAP_PACKAGE = "..bootstrap..";
    private static final String NMS_PACKAGE = "net." + "minecraft..";
    private static final String SCHEDULER_ACCESS_TYPE = "Scheduler" + "Access";
    private static final String RUN_ASYNC_METHOD = "run" + "Async";
    private static final String RUN_ON_ENTITY_METHOD = "run" + "OnEntity";
    private static final String SERVICE_LOADER_TYPE = "Service" + "Loader";

    private static final String[] PLATFORM_AND_STORAGE_PACKAGES = {
            "org.bukkit..",
            "io.papermc..",
            "org.spigotmc..",
            "io.papermc.paper.threadedregions..",
            NMS_PACKAGE,
            "org.yaml.snakeyaml..",
            "org.bukkit.configuration..",
            "org.bukkit.persistence..",
            "com.customcontentengine.adapter.."
    };

    @ArchTest
    static final ArchRule domain_is_pure =
            noClasses()
                    .that().resideInAPackage(DOMAIN_PACKAGE)
                    .should().dependOnClassesThat().resideInAnyPackage(PLATFORM_AND_STORAGE_PACKAGES)
                    .because("domain must not know Bukkit, Paper, Folia, NMS, YAML, PDC, or adapters");

    @ArchTest
    static final ArchRule internalapi_is_platform_free =
            noClasses()
                    .that().resideInAPackage(INTERNAL_API_PACKAGE)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.bukkit..",
                            "io.papermc..",
                            "org.spigotmc..",
                            "io.papermc.paper.threadedregions..",
                            NMS_PACKAGE,
                            "org.yaml.snakeyaml..",
                            "org.bukkit.configuration..",
                            "org.bukkit.persistence..")
                    .because("internalapi contracts must stay pure and platform independent");

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage(APPLICATION_PACKAGE)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.customcontentengine.adapter..",
                            "org.bukkit..",
                            "io.papermc..",
                            "org.spigotmc..",
                            "io.papermc.paper.threadedregions..",
                            NMS_PACKAGE)
                    .because("application may orchestrate through ports, not platform or adapter APIs");

    @ArchTest
    static final ArchRule builtin_stays_outside_platform_and_core_services =
            noClasses()
                    .that().resideInAPackage(BUILTIN_PACKAGE)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.customcontentengine.adapter..",
                            "com.customcontentengine.bootstrap..",
                            "com.customcontentengine.application..",
                            "com.customcontentengine.port..",
                            "org.bukkit..",
                            "io.papermc..",
                            "org.spigotmc..",
                            "io.papermc.paper.threadedregions..")
                    .because("builtin mechanics are official modules and must use only pure contracts/capabilities");

    @ArchTest
    static final ArchRule adapter_is_not_imported_by_inner_layers_or_builtin =
            noClasses()
                    .that().resideInAnyPackage(DOMAIN_PACKAGE, INTERNAL_API_PACKAGE, APPLICATION_PACKAGE, BUILTIN_PACKAGE)
                    .should().dependOnClassesThat().resideInAPackage(ADAPTER_PACKAGE)
                    .because("adapter dependencies must point inward and only bootstrap may compose adapters");

    @ArchTest
    static final ArchRule bootstrap_may_compose_dependencies =
            classes()
                    .that().resideInAPackage(BOOTSTRAP_PACKAGE)
                    .should().haveSimpleNameEndingWith("Plugin")
                    .because("bootstrap is the composition root for wiring modules together");

    @ArchTest
    static final ArchRule scheduler_port_exposes_only_region_scheduling =
            classes()
                    .that().haveFullyQualifiedName("com.customcontentengine.port.SchedulerPort")
                    .should(onlyDeclareMethodsNamed("runOnRegion"))
                    .because("unsupported scheduler entry points remain outside the MVP");

    @ArchTest
    static final ArchRule no_direct_scheduler_access_type_exists =
            noClasses()
                    .should().haveSimpleName(SCHEDULER_ACCESS_TYPE)
                    .because("mechanics must not receive scheduler access");

    @ArchTest
    static final ArchRule builtin_mechanics_implement_mechanic =
            classes()
                    .that().resideInAPackage("..builtin.mechanic..")
                    .and().haveSimpleNameEndingWith("Mechanic")
                    .should().beAssignableTo(Mechanic.class)
                    .because("mechanics must implement the internal Mechanic contract");

    @ArchTest
    static final ArchRule builtin_mechanics_declare_capabilities =
            classes()
                    .that().resideInAPackage("..builtin.mechanic..")
                    .and().haveSimpleNameEndingWith("Mechanic")
                    .should(declareNonEmptyCapabilities())
                    .because("mechanics must declare required capabilities through their descriptor");

    @ArchTest
    static final ArchRule builtin_mechanics_do_not_access_services_registries_or_scheduler_directly =
            noClasses()
                    .that().resideInAPackage("..builtin.mechanic..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.customcontentengine.application.block..",
                            "com.customcontentengine.application.item..",
                            "com.customcontentengine.domain.registry..",
                            "com.customcontentengine.port..")
                    .because("mechanics must use narrow capabilities, not services, registries, or SchedulerPort");

    @Test
    void forbidden_scheduler_and_extension_names_do_not_exist_in_source() throws IOException {
        assertNoSourceMatches(
                Pattern.compile("\\b(" + SCHEDULER_ACCESS_TYPE + "|" + RUN_ASYNC_METHOD + "|"
                        + RUN_ON_ENTITY_METHOD + "|" + SERVICE_LOADER_TYPE + ")\\b"),
                "Unsupported scheduler and extension entry points are forbidden");
    }

    @Test
    void production_code_does_not_use_nms_or_runtime_introspection() throws IOException {
        assertNoSourceMatches(
                Pattern.compile("\\bnet\\." + "minecraft\\b|\\bClass\\.forName\\b|\\bget"
                        + "Declared\\w*\\b|\\bjava\\.lang\\.re" + "flect\\b|\\b\\.in" + "voke\\s*\\("),
                "NMS and runtime introspection are forbidden in production code");
    }

    private static ArchCondition<JavaClass> onlyDeclareMethodsNamed(String expectedMethodName) {
        return new ArchCondition<>("only declare methods named " + expectedMethodName) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                List<String> unexpectedMethodNames = item.getMethods().stream()
                        .filter(method -> !method.getName().equals(expectedMethodName))
                        .map(method -> method.getFullName())
                        .toList();
                events.add(new SimpleConditionEvent(
                        item,
                        unexpectedMethodNames.isEmpty(),
                        item.getName() + " declares unexpected methods " + unexpectedMethodNames));
            }
        };
    }

    private static ArchCondition<JavaClass> declareNonEmptyCapabilities() {
        return new ArchCondition<>("declare non-empty required capabilities") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean satisfied = declaresNonEmptyCapabilitiesInSource(item);
                events.add(new SimpleConditionEvent(
                        item,
                        satisfied,
                        item.getName() + " must expose a descriptor with non-empty requiredCapabilities"));
            }
        };
    }

    private static boolean declaresNonEmptyCapabilitiesInSource(JavaClass item) {
        Path sourceFile = sourceFileFor(item);
        try {
            String source = Files.readString(sourceFile);
            return source.contains("new MechanicDescriptor(") && source.contains("Capability.");
        } catch (IOException exception) {
            return false;
        }
    }

    private static Path sourceFileFor(JavaClass item) {
        return Path.of("src/main/java", item.getName().replace('.', '/') + ".java");
    }

    private static void assertNoSourceMatches(Pattern forbiddenPattern, String message) throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<String> violations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> matchingLines(path, forbiddenPattern))
                    .toList();
            assertEquals(List.of(), violations, message);
        }
    }

    private static Stream<String> matchingLines(Path path, Pattern forbiddenPattern) {
        try {
            List<String> lines = Files.readAllLines(path);
            return numberedLines(path, lines)
                    .filter(line -> forbiddenPattern.matcher(line).find());
        } catch (IOException exception) {
            fail("Could not read " + path + ": " + exception.getMessage());
            return Stream.empty();
        }
    }

    private static Stream<String> numberedLines(Path path, List<String> lines) {
        return Stream.iterate(0, index -> index + 1)
                .limit(lines.size())
                .map(index -> path + ":" + (index + 1) + ": " + lines.get(index));
    }
}

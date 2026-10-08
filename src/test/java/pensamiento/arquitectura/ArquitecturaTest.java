package pensamiento.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Dirección de dependencias de la sección 4 del documento, verificada en el perfil test. */
@AnalyzeClasses(packages = "pensamiento", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final String[] ADAPTADORES = {"pensamiento.argdown..", "pensamiento.graficos..", "pensamiento.ia..", "pensamiento.biblioteca..", "pensamiento.trabajos.."};

    @ArchTest
    static final ArchRule nucleo_no_depende_de_nadie =
            classes().that().resideInAPackage("pensamiento.nucleo..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("pensamiento.nucleo..", "java..")
                    .because("nucleo es dominio puro: sin Spring, sin JPA, sin Ollama");

    @ArchTest
    static final ArchRule nucleo_no_importa_spring_jpa_ni_ollama =
            noClasses().that().resideInAPackage("pensamiento.nucleo..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.flywaydb..", "tools.jackson..", "reactor..");

    @ArchTest
    static final ArchRule catalogo_solo_depende_de_nucleo =
            noClasses().that().resideInAPackage("pensamiento.catalogo..")
                    .should().dependOnClassesThat().resideInAnyPackage("pensamiento.web..", "pensamiento.flujos..", "pensamiento.expediente..",
                            "pensamiento.tecnicas..", "pensamiento.ia..", "pensamiento.graficos..", "pensamiento.biblioteca..", "pensamiento.argdown..", "pensamiento.trabajos..");

    @ArchTest
    static final ArchRule tecnicas_dependen_de_nucleo_y_catalogo =
            classes().that().resideInAPackage("pensamiento.tecnicas..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("pensamiento.tecnicas..", "pensamiento.nucleo..", "pensamiento.catalogo..", "java..", "org.springframework.stereotype..", "org.springframework.context..");

    @ArchTest
    static final ArchRule adaptadores_solo_implementan_puertos_de_nucleo =
            noClasses().that().resideInAnyPackage(ADAPTADORES)
                    .should().dependOnClassesThat().resideInAnyPackage("pensamiento.web..", "pensamiento.flujos..", "pensamiento.expediente..", "pensamiento.catalogo..", "pensamiento.tecnicas..")
                    .because("argdown, graficos, ia, biblioteca y trabajos son adaptadores detrás de puertos de nucleo");

    @ArchTest
    static final ArchRule flujos_y_expediente_no_dependen_de_web =
            noClasses().that().resideInAnyPackage("pensamiento.flujos..", "pensamiento.expediente..")
                    .should().dependOnClassesThat().resideInAPackage("pensamiento.web..");

    @ArchTest
    static final ArchRule solo_el_adaptador_ia_habla_con_spring_ai =
            noClasses().that().resideOutsideOfPackage("pensamiento.ia..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.ai..");

    @ArchTest
    static final ArchRule las_tecnicas_reciben_la_ia_solo_por_el_contexto =
            noFields().that().areDeclaredInClassesThat().resideInAPackage("pensamiento.tecnicas..")
                    .should().haveRawType(pensamiento.nucleo.puertos.Ia.class)
                    .because("el ejecutor recibe la IA solo por Contexto.ia() (sección 4), para que cada llamada decida si hay modelo");

    @ArchTest
    static final ArchRule ningun_constructor_de_tecnicas_recibe_la_ia =
            noConstructors().that().areDeclaredInClassesThat().resideInAPackage("pensamiento.tecnicas..")
                    .should().haveRawParameterTypes(com.tngtech.archunit.base.DescribedPredicate.describe("incluye Ia",
                            (java.util.List<com.tngtech.archunit.core.domain.JavaClass> tipos) -> tipos.stream()
                                    .anyMatch(t -> t.isEquivalentTo(pensamiento.nucleo.puertos.Ia.class))))
                    .because("el ejecutor recibe la IA solo por Contexto.ia() (sección 4)");

    @ArchTest
    static final ArchRule la_web_no_conoce_el_adaptador_de_ia =
            noClasses().that().resideInAPackage("pensamiento.web..")
                    .should().dependOnClassesThat().resideInAPackage("pensamiento.ia..")
                    .because("la web ve el puerto Ia (con el cortacircuitos detrás), nunca el adaptador de Ollama");

    @ArchTest
    static final ArchRule nadie_fuera_de_graficos_lanza_procesos_hijo_directamente =
            noClasses().that().resideOutsideOfPackages("pensamiento.graficos..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
                    .because("Graphviz y pdftotext corren solo a través de ProcesoHijo, con tiempo máximo");
}

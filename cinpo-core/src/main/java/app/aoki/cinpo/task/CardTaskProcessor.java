package app.aoki.cinpo.task;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Build-time processor that validates and indexes {@link CardTaskDef} classes into META-INF/cinpo/<kind>.
 */
@SupportedAnnotationTypes("app.aoki.cinpo.task.CardTaskDef")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class CardTaskProcessor extends AbstractProcessor {

    private static final String CARD_TASK = "app.aoki.cinpo.task.CardTask";

    private final Map<String, List<String>> kindToFqcns = new LinkedHashMap<>();
    private boolean written;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(CardTaskDef.class)) {
            if (!(element instanceof TypeElement typeElement)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@CardTaskDef can only be applied to a class: " + element,
                        element);
                continue;
            }

            CardTaskDef annotation = typeElement.getAnnotation(CardTaskDef.class);
            if (annotation == null || !validateTask(typeElement, annotation)) {
                continue;
            }

            String kind = annotation.value();
            String fqcn = typeElement.getQualifiedName().toString();
            kindToFqcns.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(fqcn);
        }

        if (roundEnv.processingOver() && !written) {
            written = true;
            writeIndexes();
        }
        return true;
    }

    private boolean validateTask(TypeElement typeElement, CardTaskDef annotation) {
        String fqcn = typeElement.getQualifiedName().toString();
        boolean valid = true;

        if (typeElement.getKind() != ElementKind.CLASS) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@CardTaskDef can only be applied to a class: " + fqcn,
                    typeElement);
            return false;
        }

        if (typeElement.getNestingKind() != NestingKind.TOP_LEVEL) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@CardTaskDef class must be top-level: " + fqcn,
                    typeElement);
            valid = false;
        }

        Set<Modifier> modifiers = typeElement.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC) || !modifiers.contains(Modifier.FINAL)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@CardTaskDef class must be public final: " + fqcn,
                    typeElement);
            valid = false;
        }

        TypeElement cardTask = processingEnv.getElementUtils().getTypeElement(CARD_TASK);
        if (cardTask == null) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "CardTask type is not available on the annotation processor classpath",
                    typeElement);
            return false;
        }
        if (!processingEnv.getTypeUtils().isSubtype(typeElement.asType(), cardTask.asType())) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@CardTaskDef class must extend " + CARD_TASK + ": " + fqcn,
                    typeElement);
            valid = false;
        }

        if (!hasPublicNoArgConstructor(typeElement)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@CardTaskDef class must declare a public no-argument constructor: " + fqcn,
                    typeElement);
            valid = false;
        }

        if (annotation.value().isBlank()) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@CardTaskDef kind must not be blank: " + fqcn,
                    typeElement);
            valid = false;
        }

        return valid;
    }

    private boolean hasPublicNoArgConstructor(TypeElement typeElement) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(typeElement.getEnclosedElements());
        if (constructors.isEmpty()) {
            return typeElement.getModifiers().contains(Modifier.PUBLIC);
        }
        return constructors.stream()
                .anyMatch(constructor -> constructor.getParameters().isEmpty()
                        && constructor.getModifiers().contains(Modifier.PUBLIC));
    }

    private void writeIndexes() {
        for (Map.Entry<String, List<String>> entry : kindToFqcns.entrySet()) {
            List<String> fqcns = entry.getValue().stream()
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .toList();
            try {
                FileObject file = processingEnv.getFiler().createResource(
                        StandardLocation.CLASS_OUTPUT, "", "META-INF/cinpo/" + entry.getKey());
                try (Writer writer = file.openWriter()) {
                    for (String fqcn : fqcns) {
                        writer.write(fqcn);
                        writer.write('\n');
                    }
                }
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to write CINPO task index for kind '" + entry.getKey() + "': " + e.getMessage());
            }
        }
    }
}

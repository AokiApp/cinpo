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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
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
                continue;
            }
            CardTaskDef annotation = typeElement.getAnnotation(CardTaskDef.class);
            if (annotation == null || !isValidTask(typeElement)) {
                continue;
            }
            String fqcn = processingEnv.getElementUtils().getBinaryName(typeElement).toString();
            kindToFqcns.computeIfAbsent(annotation.value(), ignored -> new ArrayList<>()).add(fqcn);
        }

        if (roundEnv.processingOver() && !written) {
            written = true;
            writeIndexes();
        }
        return true;
    }

    private boolean isValidTask(TypeElement typeElement) {
        String fqcn = typeElement.getQualifiedName().toString();
        TypeElement cardTask = processingEnv.getElementUtils().getTypeElement(CARD_TASK);
        if (cardTask == null || !processingEnv.getTypeUtils().isSubtype(typeElement.asType(), cardTask.asType())) {
            error(typeElement, "@CardTaskDef class must extend " + CARD_TASK + ": " + fqcn);
            return false;
        }
        if (!hasPublicNoArgConstructor(typeElement)) {
            error(typeElement, "@CardTaskDef class must declare a public no-argument constructor: " + fqcn);
            return false;
        }
        return true;
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

    private void error(Element element, String message) {
        if (element == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
        } else {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
        }
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
                error(null, "Failed to write CINPO task index for kind '" + entry.getKey() + "': " + e.getMessage());
            }
        }
    }
}

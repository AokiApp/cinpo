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
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Build-time processor that indexes {@link CardTaskDef} classes into META-INF/cinpo/<kind>.
 */
@SupportedAnnotationTypes("app.aoki.cinpo.task.CardTaskDef")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class CardTaskProcessor extends AbstractProcessor {

    private final Map<String, List<String>> kindToFqcns = new LinkedHashMap<>();
    private boolean written;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(CardTaskDef.class)) {
            if (!(element instanceof TypeElement typeElement)) {
                continue;
            }
            CardTaskDef annotation = typeElement.getAnnotation(CardTaskDef.class);
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

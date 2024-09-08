/*
 * Copyright 2024-2024 Chris de Vreeze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cdevreeze.tryannotationprocessors.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Annotation processor that logs record components.
 * <p>
 * See <a href="https://cloudogu.com/en/blog/java-annotation-processors-1-intro">annotation processors (part 1)</a>,
 * <a href="https://cloudogu.com/en/blog/java-annotation-processors-2-creating-configurations">annotation processors (part 2)</a> and
 * <a href="https://cloudogu.com/en/blog/java-annotation-processors-3-generating-code">annotation processors (part 3)</a>.
 * <p>
 * Also see <a href="https://www.baeldung.com/java-annotation-processing-builder">java-annotation-processing-builder</a>.
 *
 * @author Chris de Vreeze
 */
@SupportedAnnotationTypes("eu.cdevreeze.tryannotationprocessors.annotation.ImmutableRecord")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class AnnotatedRecordComponentInfoProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (isRecordType(element)) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.NOTE,
                            String.format("Type annotated as ImmutableRecord: %s", element),
                            element
                    );

                    String parentName =
                            Optional.ofNullable(element.getEnclosingElement()).map(Element::getSimpleName).map(Object::toString).orElse("<no enclosing element>");
                    Stream.of(element)
                            .map(e -> (TypeElement) e)
                            .flatMap(e -> e.getRecordComponents().stream())
                            .forEachOrdered(e -> processingEnv.getMessager().printMessage(
                                    Diagnostic.Kind.NOTE,
                                    String.format(
                                            "Annotated record %s (parent %s); component found: %s (type %s)",
                                            element.getSimpleName(),
                                            parentName,
                                            e.getSimpleName(),
                                            e.asType()
                                    ),
                                    e
                            ));
                } else {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.WARNING,
                            String.format("Non-record type annotated as ImmutableRecord: %s", element),
                            element
                    );
                }
            }
        }

        return false;
    }

    private boolean isRecordType(Element element) {
        return element.getKind().equals(ElementKind.RECORD);
    }
}

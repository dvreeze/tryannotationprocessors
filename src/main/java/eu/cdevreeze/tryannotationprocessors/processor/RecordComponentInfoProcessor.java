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
import java.util.List;
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
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class RecordComponentInfoProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<? extends Element> recordElements =
                findAllRecordElements(roundEnv.getRootElements().stream().toList());

        for (Element element : recordElements) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    String.format("Record type found: %s", element),
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
                                    "Record %s (parent %s); component found: %s (type %s)",
                                    element.getSimpleName(),
                                    parentName,
                                    e.getSimpleName(),
                                    e.asType()
                            ),
                            e
                    ));
        }

        return false;
    }

    private List<? extends TypeElement> findAllRecordElements(List<? extends Element> rootElements) {
        return rootElements.stream()
                .flatMap(e -> {
                    if (e.getKind().equals(ElementKind.PACKAGE)) {
                        return e.getEnclosedElements().stream()
                                .flatMap(this::recordElementStream);
                    } else if (e instanceof TypeElement) {
                        return recordElementStream(e);
                    } else {
                        return Stream.empty();
                    }
                }).distinct().toList();
    }

    private Stream<? extends TypeElement> recordElementStream(Element element) {
        // Conceptually like an XPath descendant-or-self axis, but for Java language elements rather XML
        // Does not follow inheritance tree for members
        // Recursive
        return Stream.concat(
                Stream.of(element).filter(this::isRecordType).map(e -> (TypeElement) e),
                element.getEnclosedElements().stream()
                        .flatMap(this::recordElementStream)
        );
    }

    private boolean isRecordType(Element element) {
        return element.getKind().equals(ElementKind.RECORD);
    }
}

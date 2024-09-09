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
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Annotation processor that generates a program that logs record components, without using reflection.
 * <p>
 * See <a href="https://cloudogu.com/en/blog/java-annotation-processors-1-intro">annotation processors (part 1)</a>,
 * <a href="https://cloudogu.com/en/blog/java-annotation-processors-2-creating-configurations">annotation processors (part 2)</a> and
 * <a href="https://cloudogu.com/en/blog/java-annotation-processors-3-generating-code">annotation processors (part 3)</a>.
 * <p>
 * Also see <a href="https://www.baeldung.com/java-annotation-processing-builder">java-annotation-processing-builder</a>.
 * <p>
 * See <a href="https://www.baeldung.com/java-poet">java-poet</a> for more information about JavaPoet.
 *
 * @author Chris de Vreeze
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class RecordComponentInfoProgramGenerator extends AbstractProcessor {

    private record RecordComponentInfo(String simpleName, String type) {
    }

    private record RecordInfo(
            String recordSimpleName,
            Optional<String> recordParentSimpleNameOption,
            List<RecordComponentInfo> recordComponents
    ) {
    }

    private AtomicBoolean ready;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        ready = new AtomicBoolean(false);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<? extends Element> recordElements =
                findAllRecordElements(roundEnv.getRootElements().stream().toList());

        List<RecordInfo> recordInfos = recordElements.stream()
                .map(recordElem -> {
                    Optional<String> parentNameOption =
                            Optional.ofNullable(recordElem.getEnclosingElement()).map(Element::getSimpleName).map(Object::toString);

                    List<RecordComponentInfo> components = Stream.of(recordElem)
                            .map(re -> (TypeElement) re)
                            .flatMap(re -> re.getRecordComponents().stream())
                            .map(e -> new RecordComponentInfo(e.getSimpleName().toString(), e.asType().toString()))
                            .toList();

                    return new RecordInfo(
                            recordElem.getSimpleName().toString(),
                            parentNameOption,
                            components
                    );
                })
                .toList();

        JavaFile javaFile = createJavaFile("eu.cdevreeze.generated.console", recordInfos);
        try {
            if (!ready.getAndSet(true)) {
                javaFile.writeTo(processingEnv.getFiler());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return false;
    }

    private JavaFile createJavaFile(String packageName, List<RecordInfo> recordInfos) {
        return JavaFile.builder(packageName, createJavaTypeSpec(recordInfos))
                .indent("    ")
                .build();
    }

    private TypeSpec createJavaTypeSpec(List<RecordInfo> recordInfos) {
        // TODO (for Java 21) add @Generated annotation to source file
        return TypeSpec.classBuilder("ShowRecordInformation")
                .addModifiers(Modifier.PUBLIC)
                .addType(createRecordComponentInfoClass())
                .addType(createRecordInfoClass())
                .addMethod(
                        fillRecordInfos(
                                MethodSpec.methodBuilder("main")
                                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                        .addParameter(String[].class, "args"),
                                recordInfos
                        )
                                .beginControlFlow("for (int i = 0; i < recordInfos.size(); i++)")
                                .addStatement("System.out.println()")
                                .addStatement("System.out.println(\"Record simple name: \" + recordInfos.get(i).getRecordSimpleName())")
                                .addStatement("System.out.println(\"Record parent simple name: \" + recordInfos.get(i).getRecordParentSimpleNameOption().orElse(\"\"))")
                                .addStatement("System.out.println(\"Components:\")")
                                .beginControlFlow("for (int j = 0; j < recordInfos.get(i).getRecordComponents().size(); j++)")
                                .addStatement("System.out.println(\"Simple name: \" + recordInfos.get(i).getRecordComponents().get(j).getSimpleName())")
                                .addStatement("System.out.println(\"Type:        \" + recordInfos.get(i).getRecordComponents().get(j).getType())")
                                .endControlFlow()
                                .endControlFlow()
                                .build()
                )
                .build();
    }

    private MethodSpec.Builder fillRecordInfos(MethodSpec.Builder methodBuilder, List<RecordInfo> recordInfos) {
        methodBuilder.addStatement("List<RecordInfo> recordInfos = new $T<>()", ArrayList.class)
                .addStatement("List<RecordComponentInfo> recordComponentInfos = new $T<>()", ArrayList.class);

        for (var recordInfo : recordInfos) {
            methodBuilder.addStatement("recordComponentInfos.clear()");

            for (var recordCompInfo : recordInfo.recordComponents()) {
                methodBuilder.addStatement(
                        "recordComponentInfos.add(new RecordComponentInfo($S, $S))",
                        recordCompInfo.simpleName(),
                        recordCompInfo.type()
                );
            }

            methodBuilder.addStatement(
                    "recordInfos.add(new RecordInfo($S, Optional.ofNullable($S), recordComponentInfos))",
                    recordInfo.recordSimpleName(),
                    recordInfo.recordParentSimpleNameOption().orElse(null)
            );
        }

        return methodBuilder;
    }

    private TypeSpec createRecordComponentInfoClass() {
        return TypeSpec.classBuilder("RecordComponentInfo")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addField(String.class, "simpleName", Modifier.PRIVATE, Modifier.FINAL)
                .addField(String.class, "type", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(
                        MethodSpec.constructorBuilder()
                                .addParameter(String.class, "simpleName")
                                .addParameter(String.class, "type")
                                .addStatement("this.simpleName = simpleName")
                                .addStatement("this.type = type")
                                .build()
                )
                .addMethod(
                        MethodSpec.methodBuilder("getSimpleName")
                                .returns(String.class)
                                .addStatement("return simpleName")
                                .build()
                )
                .addMethod(
                        MethodSpec.methodBuilder("getType")
                                .returns(String.class)
                                .addStatement("return type")
                                .build()
                )
                .build();
    }

    private TypeSpec createRecordInfoClass() {
        return TypeSpec.classBuilder("RecordInfo")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addField(String.class, "recordSimpleName", Modifier.PRIVATE, Modifier.FINAL)
                .addField(
                        ParameterizedTypeName.get(ClassName.get(Optional.class), TypeName.get(String.class)),
                        "recordParentSimpleNameOption",
                        Modifier.PRIVATE,
                        Modifier.FINAL
                )
                .addField(
                        ParameterizedTypeName.get(ClassName.get(List.class), ClassName.bestGuess("RecordComponentInfo")),
                        "recordComponents",
                        Modifier.PRIVATE,
                        Modifier.FINAL
                )
                .addMethod(
                        MethodSpec.constructorBuilder()
                                .addParameter(String.class, "recordSimpleName")
                                .addParameter(
                                        ParameterizedTypeName.get(ClassName.get(Optional.class), TypeName.get(String.class)),
                                        "recordParentSimpleNameOption"
                                )
                                .addParameter(
                                        ParameterizedTypeName.get(ClassName.get(List.class), ClassName.bestGuess("RecordComponentInfo")),
                                        "recordComponents"
                                )
                                .addStatement("this.recordSimpleName = recordSimpleName")
                                .addStatement("this.recordParentSimpleNameOption = recordParentSimpleNameOption")
                                .addStatement("this.recordComponents = List.copyOf(recordComponents)")
                                .build()
                )
                .addMethod(
                        MethodSpec.methodBuilder("getRecordSimpleName")
                                .returns(String.class)
                                .addStatement("return recordSimpleName")
                                .build()
                )
                .addMethod(
                        MethodSpec.methodBuilder("getRecordParentSimpleNameOption")
                                .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), TypeName.get(String.class)))
                                .addStatement("return recordParentSimpleNameOption")
                                .build()
                )
                .addMethod(
                        MethodSpec.methodBuilder("getRecordComponents")
                                .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName.bestGuess("RecordComponentInfo")))
                                .addStatement("return List.copyOf(recordComponents)")
                                .build()
                )
                .build();
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

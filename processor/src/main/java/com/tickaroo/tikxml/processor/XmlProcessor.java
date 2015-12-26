/*
 * Copyright (C) 2015 Hannes Dorfmann
 * Copyright (C) 2015 Tickaroo, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.tickaroo.tikxml.processor;

import com.google.auto.service.AutoService;
import com.tickaroo.tikxml.annotation.ScanMode;
import com.tickaroo.tikxml.annotation.Xml;
import com.tickaroo.tikxml.processor.model.AnnotatedClass;
import com.tickaroo.tikxml.processor.model.AnnotatedClassImpl;
import com.tickaroo.tikxml.processor.scanning.ScanStrategy;
import com.tickaroo.tikxml.processor.scanning.ScanStrategyFactory;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Annotation processor for @Xml annotated images
 *
 * @author Hannes Dorfmann
 * @since 1.0
 */
@AutoService(Processor.class)
public class XmlProcessor extends AbstractProcessor {

  /**
   * The default scan mode
   */
  private static final String OPTION_DEFAULT_SCAN_MODE = "defaultScanMode";

  private Messager messager;
  private Filer filer;
  private Elements elementUtils;
  private Types typeUtils;
  private ScanStrategyFactory scanStrategyFactory;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    filer = processingEnv.getFiler();
    elementUtils = processingEnv.getElementUtils();
    typeUtils = processingEnv.getTypeUtils();
    scanStrategyFactory = new ScanStrategyFactory(elementUtils, typeUtils);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> types = new HashSet<>();
    types.add(Xml.class.getCanonicalName());
    return types;
  }

  @Override
  public Set<String> getSupportedOptions() {
    Set<String> options = new HashSet<>();
    options.add(OPTION_DEFAULT_SCAN_MODE);
    return options;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    try {

      // Read options
      String optionScanAsString = processingEnv.getOptions().get(OPTION_DEFAULT_SCAN_MODE);
      ScanMode defaultScanMode;
      if (optionScanAsString == null || optionScanAsString.length() == 0) {
        defaultScanMode = ScanMode.COMMON_CASE;
      } else {
        defaultScanMode = ScanMode.valueOf(optionScanAsString);
      }

      if (defaultScanMode == ScanMode.DEFAULT) {
        throw new ProcessingException(null, "The option '%s' is not allowed. Must either be %s or %s",
            OPTION_DEFAULT_SCAN_MODE, optionScanAsString, ScanMode.ANNOTATIONS_ONLY.toString(),
            ScanMode.COMMON_CASE.toString());
      }


      Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(Xml.class);

      for (Element element : elementsAnnotatedWith) {
        AnnotatedClass clazz = new AnnotatedClassImpl(element);

        // Scan class
        ScanStrategy strategy = scanStrategyFactory.getStrategy(clazz, defaultScanMode);
        strategy.scan(clazz);
      }

    } catch (ProcessingException e) {
      printError(e);
    }


    return false;
  }


  /**
   * Prints the error message
   *
   * @param exception The exception that has caused an error
   */
  private void printError(ProcessingException exception) {
    messager.printMessage(Diagnostic.Kind.ERROR, exception.getMessage(), exception.getElement());
  }
}
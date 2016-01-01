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

package com.tickaroo.tikxml.processor.scanning

import com.tickaroo.tikxml.annotation.Xml
import com.tickaroo.tikxml.processor.ProcessingException
import com.tickaroo.tikxml.processor.model.AnnotatedClass
import com.tickaroo.tikxml.processor.model.Field
import com.tickaroo.tikxml.processor.model.access.GetterSetterFieldAccessPolicy
import com.tickaroo.tikxml.processor.model.access.MinPackageVisibilityFieldAccessPolicy
import com.tickaroo.tikxml.processor.utils.*
import java.util.*
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.text.*

/**
 * Scans an [com.tickaroo.tikxml.annotation.Xml] annotated class and fulfills the  [AnnotatedClass]
 * @author Hannes Dorfmann
 * @since 1.0
 */
class FieldScanner(protected val elementUtils: Elements, protected val typeUtils: Types, private val fieldDetectorStrategyFactory: FieldDetectorStrategyFactory) {

    /**
     * Scans the child element of the passed [AnnotatedClass] to find [com.tickaroo.tikxml.processor.model.Field]
     */
    @Throws(ProcessingException::class)
    fun scan(annotatedClass: AnnotatedClass) {

        var currentElement: TypeElement = annotatedClass.element

        // Scan the currently annotated class
        doScan(annotatedClass, currentElement)

        if (annotatedClass.inheritance) {
            // Scan super classes with @Xml annotation
            currentElement.getSuperClasses(typeUtils)
                    .filter { it.getAnnotation(Xml::class.java) != null }
                    .forEach { doScan(annotatedClass, it) }
        }


    }

    private fun doScan(annotatedClass: AnnotatedClass, currentElement: TypeElement) {

        val fieldWithMethodAccessRequired = ArrayList<Field>()
        val methodsMap = HashMap<String, ExecutableElement>()
        var constructorFound = false


        currentElement.enclosedElements.forEach {

            if (it.isEmptyConstructorWithMinimumPackageVisibility()) {
                constructorFound = true
            } else if (it.isGetterMethodWithMinimumPackageVisibility() || it.isSetterMethodWithMinimumPackageVisibility()) {
                val method = it as ExecutableElement
                methodsMap.put(method.simpleName.toString(), method)
            } else if (it.isField()) {

                val xmlAnnotation = currentElement.getAnnotation(Xml::class.java) ?: throw ProcessingException(currentElement, "The class ${annotatedClass.qualifiedClassName} should be annotated with @${Xml::class.simpleName}. This is an internal error. Please file an issue on github: https://github.com/Tickaroo/tikxml/issues") // Should be impossible

                val fieldDetectorStratefy = fieldDetectorStrategyFactory.getStrategy(xmlAnnotation.scanMode)

                val field: Field? = fieldDetectorStratefy.isXmlField(it as VariableElement)
                if (field != null) {

                    // check for conflicts
                    val existingField: Field? = annotatedClass.fields[field.name]
                    if (existingField != null) {
                        val conflictingField = existingField.element
                        throw ProcessingException(it, "Conflict: The field '${it.toString()}' "
                                + "in class ${currentElement.qualifiedName} has the same XML "
                                + "name '${field.name}' as the field '${conflictingField.simpleName}' in class "
                                + "${(conflictingField.enclosingElement as TypeElement).qualifiedName}. "
                                + "You can specify another name via annotations.")
                    }

                    // needs setter and getter?
                    if (it.hasMinimumPackageVisibilityModifiers()) {
                        field.accessPolicy = MinPackageVisibilityFieldAccessPolicy(field.element)
                        annotatedClass.fields.put(field.name, field)
                    } else {
                        fieldWithMethodAccessRequired.add(field) // processed afterwards
                    }

                }
            }
        }


        if (!constructorFound) {
            throw ProcessingException(annotatedClass.element, "${annotatedClass.qualifiedClassName} " +
                    "must provide an empty (parameterless) constructor with minimum default (package) visibility")
        }

        // Search for getters and setters
        fieldWithMethodAccessRequired.forEach {

            val element = it.element
            val elementName: String = element.simpleName.toString()
            val nameWithoutHungarian = getFieldNameWithoutHungarianNotation(element)

            var getter = findMethodForField(nameWithoutHungarian, "get", methodsMap)
            if (getter == null) {
                getter = findMethodForHungarianField(elementName, "get", methodsMap)
                if (getter == null) {
                    getter = findMethodForHungarianFieldUpperCase(elementName, "get", methodsMap)
                }
            }

            // Test with "is" prefix
            // TODO only run this if boolean type
            if (getter == null) {
                var getter = findMethodForField(nameWithoutHungarian, "is", methodsMap)
                if (getter == null) {
                    getter = findMethodForHungarianField(elementName, "is", methodsMap)
                    if (getter == null) {
                        getter = findMethodForHungarianFieldUpperCase(elementName, "is", methodsMap)
                    }
                }
            }

            if (getter == null) {
                throw ProcessingException(element, "The field '${element.simpleName.toString()}' "
                        + "in class ${(element.enclosingElement as TypeElement).qualifiedName.toString()} "
                        + "has private or protected visibility. Hence a corresponding getter method must be provided"
                        + " with minimum package visibility (or public visibility if this is a super class in a different package)"
                        + " with the name ${bestMethodName(elementName, "get")}() or ${bestMethodName(elementName, "is")}() "
                        + "in case of a boolean. Unfortunately, there is no such getter method. Please provide one!")
            }

            if (!getter.isParameterlessMethod()) {
                throw ProcessingException(element, "The getter method '${getter.toString()}' for field '${element.simpleName.toString()}'"
                        + "in class ${element.getSurroundingClassQualifiedName()} "
                        + "must be parameterless (zero parameters).")
            }

            if (getter.isProtected() || getter.isPrivate() || (getter.isDefaultVisibility() && !getter.isSamePackageAs(element, elementUtils))) {
                throw ProcessingException(element, "The getter method '${getter.toString()}' for field '${element.simpleName.toString()}' "
                        + "in class ${element.getSurroundingClassQualifiedName()} "
                        + "must have minimum package visibility (or public visibility if this is a super class in a different package)")

            }

            // Setter method
            var setter = findMethodForField(nameWithoutHungarian, "set", methodsMap)
            if (setter == null) {
                setter = findMethodForHungarianField(elementName, "set", methodsMap)
                if (setter == null) {
                    setter = findMethodForHungarianFieldUpperCase(elementName, "set", methodsMap)
                }
            }

            if (setter == null) {
                throw ProcessingException(element, "The field '${element.simpleName.toString()}' "
                        + "in class ${(element.enclosingElement as TypeElement).qualifiedName.toString()} "
                        + "has private or protected visibility. Hence a corresponding setter method must be provided "
                        + " with the name ${bestMethodName(elementName, "set")}(${element.asType()}) and "
                        + "minimum package visibility (or public visibility if this is a super class in a different package)"
                        + "Unfortunately, there is no such setter method. Please provide one!")
            }

            if (!setter.isMethodWithOneParameterOfType(element.asType(), typeUtils)) {
                throw ProcessingException(element, "The setter method '${setter.toString()}' for field '${element.simpleName.toString()}' "
                        + "in class ${element.getSurroundingClassQualifiedName()} "
                        + "must have exactly one parameter of type '${element.asType()}'")
            }

            if (setter.isProtected() || setter.isPrivate() || (setter.isDefaultVisibility() && !setter.isSamePackageAs(element, elementUtils))) {
                throw ProcessingException(element, "The setter method '${setter.toString()}' for field '${element.simpleName.toString()}'"
                        + "in class ${element.getSurroundingClassQualifiedName()} "
                        + "must have minimum package visibility (or public visibility if this is a super class in a different package)")

            }

            val conflictingField = annotatedClass.fields[it.name]
            if (conflictingField != null) {
                throw ProcessingException(element, "Conflict: The field '${element.toString()}' "
                        + "in class ${currentElement.qualifiedName} has the same XML "
                        + "name '${it.name}' as the field '${conflictingField.element.simpleName}' in class "
                        + "${(conflictingField.element.enclosingElement as TypeElement).qualifiedName}. "
                        + "You can specify another name via annotations.")
            }


            it.accessPolicy = GetterSetterFieldAccessPolicy(getter, setter)
            annotatedClass.fields.put(it.name, it)
        }
    }

    /**
     * Finds a method for a field. Removes hungarion notation. If field name was mFoo this method checks for a method called setFoo()
     */
    private fun findMethodForField(fieldName: String, methodNamePrefix: String, setterAndGetters: Map<String, ExecutableElement>): ExecutableElement? {


        val methodName = bestMethodName(fieldName, methodNamePrefix)
        return setterAndGetters[methodName]

    }

    private fun bestMethodName(fieldName: String, methodNamePrefix: String): String {
        val builder = StringBuilder(methodNamePrefix)
        if (fieldName.length == 1) {
            builder.append(fieldName.toUpperCase())
        } else {
            builder.append(Character.toUpperCase(fieldName[0]))
            builder.append(fieldName.substring(1))
        }

        return builder.toString()
    }

    /**
     * If field name was mFoo this method checks for a method called setmFoo()
     */
    private fun findMethodForHungarianField(fieldName: String, methodNamePrefix: String, setterAndGetters: Map<String, ExecutableElement>): ExecutableElement? {


        // Search for setter method with hungarian notion check
        if (fieldName.length > 1 && fieldName.matches(Regex("m[A-Z].*"))) {
            // m not in lower case
            val hungarianMethodName = methodNamePrefix + fieldName;
            return setterAndGetters[hungarianMethodName];
        }
        return null;
    }

    /**
     * If field name was mFoo this method checks for a method called setMFoo()
     */
    private fun findMethodForHungarianFieldUpperCase(fieldName: String, methodNamePrefix: String, setterAndGetters: Map<String, ExecutableElement>): ExecutableElement? {

        // Search for setter method with hungarian notion check
        if (fieldName.length > 1 && fieldName.matches(Regex("m[A-Z].*"))) {

            // M in upper case
            val hungarianMethodName = "set" + Character.toUpperCase(fieldName[0]) + fieldName.substring(1)
            return setterAndGetters[hungarianMethodName]
        }

        return null
    }

    private fun getFieldNameWithoutHungarianNotation(element: VariableElement): String {
        val name = element.simpleName.toString()
        if (name.matches(Regex("^m[A-Z]{1}"))) {
            return name.substring(1, 2).toLowerCase();
        } else if (name.matches(Regex("m[A-Z]{1}.*"))) {
            return name.substring(1, 2).toLowerCase() + name.substring(2);
        }
        return name;
    }

}


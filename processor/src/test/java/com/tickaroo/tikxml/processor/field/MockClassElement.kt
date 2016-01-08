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

package com.tickaroo.tikxml.processor.field

import javax.lang.model.element.*
import javax.lang.model.type.TypeMirror

interface Java8Support {
    fun <T : Annotation> getAnnotationsByType(annotationClass: Class<T>): Array<T>
}
/**
 *
 * @author Hannes Dorfmann
 */
class MockClassElement : TypeElement, Java8Support{

    override fun getSimpleName(): Name? {
        throw UnsupportedOperationException()
    }

    override fun getSuperclass(): TypeMirror? {
        throw UnsupportedOperationException()
    }

    override fun getQualifiedName(): Name? {
        return MockName("mocked.MockedClass")
    }

    override fun getTypeParameters(): MutableList<out TypeParameterElement>? {
        throw UnsupportedOperationException()
    }

    override fun getEnclosingElement(): Element? {
        throw UnsupportedOperationException()
    }

    override fun getInterfaces(): MutableList<out TypeMirror>? {
        throw UnsupportedOperationException()
    }

    override fun getNestingKind(): NestingKind? {
        throw UnsupportedOperationException()
    }

    override fun getEnclosedElements(): MutableList<out Element>? {
        throw UnsupportedOperationException()
    }

    override fun getModifiers(): MutableSet<Modifier>? {
        throw UnsupportedOperationException()
    }

    override fun getKind(): ElementKind? {
        throw UnsupportedOperationException()
    }

    override fun asType(): TypeMirror? {
        throw UnsupportedOperationException()
    }

    override fun <R : Any?, P : Any?> accept(v: ElementVisitor<R, P>?, p: P): R {
        throw UnsupportedOperationException()
    }

    override fun <A : Annotation?> getAnnotation(annotationType: Class<A>?): A {
        throw UnsupportedOperationException()
    }

    override fun getAnnotationMirrors(): MutableList<out AnnotationMirror>? {
        throw UnsupportedOperationException()
    }

    override fun <T : Annotation> getAnnotationsByType(annotationClass: Class<T>): Array<T> {
        throw UnsupportedOperationException()
    }

    override fun toString() = qualifiedName.toString()

}
/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder

import com.h0tk3y.kotlin.staticObjectNotation.AccessFromCurrentReceiverOnly
import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Builder
import com.h0tk3y.kotlin.staticObjectNotation.Configuring
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ConfigureAccessor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataBuilderFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataConstructor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataMemberFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataParameter
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTopLevelFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics.AddAndConfigure.ConfigureBlockRequirement.NOT_ALLOWED
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics.AddAndConfigure.ConfigureBlockRequirement.OPTIONAL
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics.AddAndConfigure.ConfigureBlockRequirement.REQUIRED
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaMemberFunction
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf

interface FunctionExtractor {
    fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction>
    fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor>
    fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction?
}

class CompositeFunctionExtractor(internal val extractors: Iterable<FunctionExtractor>) : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        extractors.flatMapTo(mutableSetOf()) { it.memberFunctions(kClass, preIndex)}

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> =
        extractors.flatMapTo(mutableSetOf()) { it.constructors(kClass, preIndex)}

    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? =
        extractors.asSequence().mapNotNull { it.topLevelFunction(function, preIndex) }.firstOrNull()
}

operator fun FunctionExtractor.plus(other: FunctionExtractor): CompositeFunctionExtractor = CompositeFunctionExtractor(buildList {
    fun include(functionExtractor: FunctionExtractor) = when (functionExtractor) {
        is CompositeFunctionExtractor -> addAll(functionExtractor.extractors)
        else -> add(functionExtractor)
    }
    include(this@plus)
    include(other)
})

class DefaultFunctionExtractor(
    private val includeFilter: MemberFilter,
    private val configureLambdas: ConfigureLambdaHandler
) : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        kClass.memberFunctions.filter { it.visibility == KVisibility.PUBLIC && includeFilter.shouldIncludeMember(it) }
            .map { function -> memberFunction(kClass, function, preIndex, configureLambdas) }

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> =
        kClass.constructors.filter { it.visibility == KVisibility.PUBLIC && includeFilter.shouldIncludeMember(it) }
            .map { constructor ->
                constructor(constructor, kClass, preIndex)
            }

    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction =
        dataTopLevelFunction(function, preIndex)

    private fun memberFunction(
        inType: KClass<*>,
        function: KFunction<*>,
        preIndex: DataSchemaBuilder.PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): SchemaMemberFunction {
        val thisTypeRef = inType.toDataTypeRef()

        val returnType = function.returnType

        checkInScope(returnType, preIndex)
        val returnClass = function.returnType.classifier as KClass<*>
        val fnParams = function.parameters

        val semanticsFromSignature = inferFunctionSemanticsFromSignature(function, function.returnType, inType, preIndex, configureLambdas)
        val maybeConfigureType = if (semanticsFromSignature is FunctionSemantics.AccessAndConfigure) {
            function.annotations.filterIsInstance<Configuring>().singleOrNull()?.propertyName?.let { preIndex.getPropertyType(inType, it) }
                ?: preIndex.getPropertyType(inType, function.name)
        } else null

        val params = fnParams
            .filterIndexed { index, it ->
                it != function.instanceParameter && run {
                    // is value parameter, not a configuring block:
                    val isNotLastParameter = index != fnParams.lastIndex
                    val isNotConfigureLambda = configureLambdas.getTypeConfiguredByLambda(it.type)?.let { it.toDataTypeRefOrError() != maybeConfigureTypeRef } ?: true
                    isNotLastParameter || isNotConfigureLambda
                }
            }
            .map { fnParam -> dataParameter(function, fnParam, returnClass, semanticsFromSignature, preIndex) }

        val isDirectAccessOnly = function.annotations.any { it is AccessFromCurrentReceiverOnly }

        return if (semanticsFromSignature is FunctionSemantics.Builder) {
            DataBuilderFunction(
                thisTypeRef,
                function.name,
                isDirectAccessOnly,
                params.single(),
            )
        } else {
            DataMemberFunction(
                thisTypeRef,
                function.name,
                params,
                isDirectAccessOnly,
                semanticsFromSignature
            )
        }
    }

    private fun constructor(
        constructor: KFunction<Any>,
        kClass: KClass<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataConstructor {
        val params = constructor.parameters
        val dataParams = params.map { param ->
            dataParameter(constructor, param, kClass, FunctionSemantics.Pure(kClass.toDataTypeRef()), preIndex)
        }
        return DataConstructor(dataParams, kClass.toDataTypeRef())
    }

    private fun dataTopLevelFunction(
        function: KFunction<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataTopLevelFunction {
        check(function.instanceParameter == null)

        val returnType = function.returnType
        checkInScope(returnType, preIndex)

        val returnTypeClassifier = function.returnType
        val semanticsFromSignature = FunctionSemantics.Pure(returnTypeClassifier.toDataTypeRefOrError())

        val fnParams = function.parameters
        val params = fnParams.filterIndexed { index, _ ->
            index != fnParams.lastIndex || configureLambdas.getTypeConfiguredByLambda(returnTypeClassifier) == null
        }.map { dataParameter(function, it, function.returnType.toKClass(), semanticsFromSignature, preIndex) }

        return DataTopLevelFunction(
            function.javaMethod!!.declaringClass.packageName,
            function.name,
            params,
            semanticsFromSignature
        )
    }

    private fun dataParameter(
        function: KFunction<*>,
        fnParam: KParameter,
        returnClass: KClass<*>,
        functionSemantics: FunctionSemantics,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataParameter {
        val paramType = fnParam.type
        checkInScope(paramType, preIndex)
        val paramSemantics = getParameterSemantics(functionSemantics, function, fnParam, returnClass, preIndex)
        return DataParameter(fnParam.name, paramType.toDataTypeRefOrError(), fnParam.isOptional, paramSemantics)
    }

    private fun getParameterSemantics(
        functionSemantics: FunctionSemantics,
        function: KFunction<*>,
        fnParam: KParameter,
        returnClass: KClass<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): ParameterSemantics {
        val propertyNamesToCheck = buildList {
            if (functionSemantics is FunctionSemantics.Builder) add(function.name)
            if (functionSemantics is FunctionSemantics.NewObjectFunctionSemantics) fnParam.name?.let(::add)
        }
        propertyNamesToCheck.forEach { propertyName ->
            val isPropertyLike =
                preIndex.getAllProperties(returnClass).any { it.name == propertyName }
            if (isPropertyLike) {
                val storeProperty = checkNotNull(preIndex.getProperty(returnClass, propertyName))
                return ParameterSemantics.StoreValueInProperty(storeProperty)
            }
        }
        return ParameterSemantics.Unknown
    }

    private fun inferFunctionSemanticsFromSignature(
        function: KFunction<*>,
        returnTypeClassifier: KType,
        inType: KClass<*>?,
        preIndex: DataSchemaBuilder.PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): FunctionSemantics {
        return when {
            function.annotations.any { it is Builder } -> {
                check(inType != null)
                FunctionSemantics.Builder(returnTypeClassifier.toDataTypeRefOrError())
            }

            function.annotations.any { it is Adding } -> {
                check(inType != null)

                check(function.returnType != typeOf<Unit>() || configureLambdas.getTypeConfiguredByLambda(function.parameters.last().type) == null) {
                    "an @Adding function with a Unit return type may not accept configuring lambdas"
                }

                val lastParam = function.parameters[function.parameters.lastIndex]
                val hasConfigureLambda =
                    configureLambdas.isConfigureLambdaForType(function.returnType, lastParam.type)
                val blockRequirement = when {
                    !hasConfigureLambda -> NOT_ALLOWED
                    hasConfigureLambda && lastParam.isOptional -> OPTIONAL
                    else -> REQUIRED
                }
                FunctionSemantics.AddAndConfigure(returnTypeClassifier.toDataTypeRefOrError(), blockRequirement)
            }

            function.annotations.any { it is Configuring } -> {
                check(inType != null)

                val annotation = function.annotations.filterIsInstance<Configuring>().singleOrNull()
                check(annotation != null)
                val annotationPropertyName = annotation.propertyName
                val propertyName = annotationPropertyName.ifEmpty { function.name }

                val configuredType = configureLambdas.getTypeConfiguredByLambda(function.parameters.last().type)
                check(configuredType != null) { "a @Configuring function must accept a configuring lambda" }

                val propertyType = preIndex.getPropertyType(inType, propertyName)
                check(propertyType == null || propertyType.isSubtypeOf(configuredType)) { "configure lambda type is inconsistent with property type" }

                val property = preIndex.getProperty(inType, propertyName)
                check(annotationPropertyName.isEmpty() || propertyType != null) { "a property name '$annotationPropertyName' is specified for @Configuring function but no such property was found" }

                val returnType = when (function.returnType) {
                    typeOf<Unit>() -> FunctionSemantics.AccessAndConfigure.ReturnType.UNIT
                    propertyType, configuredType -> FunctionSemantics.AccessAndConfigure.ReturnType.CONFIGURED_OBJECT
                    else -> error("cannot infer the return type of a configuring function; it must be Unit or the configured object type")
                }
                check(function.parameters.filter { it != function.instanceParameter }.size == 1) { "a configuring function may not accept any other parameters" }
                val accessor = if (property != null) ConfigureAccessor.Property(property) else ConfigureAccessor.ConfiguringLambdaArgument(configuredType.toDataTypeRefOrError())
                FunctionSemantics.AccessAndConfigure(accessor, returnType)
            }

            else -> FunctionSemantics.Pure(returnTypeClassifier.toDataTypeRefOrError())
        }
    }

    private fun KType.toKClass() = (classifier ?: error("unclassifiable type $this is used in the schema")) as? KClass<*>
        ?: error("type $this classified as a non-class is used in the schema")

}

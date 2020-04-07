/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.backend.common.lower.inline.DefaultInlineFunctionResolver
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesExtractionFromInlineFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineLambdasLowering
import org.jetbrains.kotlin.backend.common.runPostfix
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

// TODO: This is a bit hacky. Think about adopting persistent IR ideas.
internal class NativeInlineFunctionResolver(override val context: Context) : DefaultInlineFunctionResolver(context) {
    override fun getFunctionDeclaration(symbol: IrFunctionSymbol): IrFunction {
        val function = super.getFunctionDeclaration(symbol)
        val body = function.body ?: return function

        if (function in context.specialDeclarationsFactory.loweredInlineFunctions)
            return function

        context.specialDeclarationsFactory.loweredInlineFunctions.add(function)

        PreInlineLowering(context).lower(body, function)

        ArrayConstructorLowering(context).lower(body, function)

        NullableFieldsForLateinitCreationLowering(context)
                .runPostfix(true).transformFlat(function)
        NullableFieldsDeclarationLowering(context)
                .runPostfix(true).transformFlat(function)
        LateinitUsageLowering(context).lower(body, function)

        SharedVariablesLowering(context).lower(body, function)

        LocalClassesInInlineLambdasLowering(context).lower(body, function)

        if (context.llvmModuleSpecification.containsDeclaration(function)) {
            // Do not extract local classes off of inline functions from cached libraries.
            LocalClassesInInlineFunctionsLowering(context).lower(body, function)
            LocalClassesExtractionFromInlineFunctionsLowering(context).lower(body, function)
        }

        return function
    }
}
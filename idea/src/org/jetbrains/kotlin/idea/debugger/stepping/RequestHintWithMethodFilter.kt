/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.debugger.stepping

import com.intellij.debugger.engine.BreakpointStepMethodFilter
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.RequestHint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.diagnostic.Logger
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.request.StepRequest
import org.intellij.lang.annotations.MagicConstant
import java.lang.reflect.Field

// TODO: Open constructor with depth
internal class RequestHintWithMethodFilter(
        stepThread: ThreadReferenceProxyImpl,
        suspendContext: SuspendContextImpl,
        @MagicConstant(intValues = longArrayOf(StepRequest.STEP_INTO.toLong(), StepRequest.STEP_OVER.toLong(), StepRequest.STEP_OUT.toLong())) depth: Int,
        methodFilter: MethodFilter
) : RequestHint(stepThread, suspendContext, methodFilter) {
    private var targetMethodMatched = false

    init {
        if (depth != StepRequest.STEP_INTO) {
            findFieldWithValue(StepRequest.STEP_INTO, Integer.TYPE)?.setInt(this, depth)
        }
    }

    private fun findFieldWithValue(value: Int, type: Class<*>): Field? {
        return RequestHint::class.java.declaredFields.find { field ->
            if (field.type == type) {
                field.isAccessible = true
                if (field.getInt(this) == value) {
                    return@find true
                }
            }

            false
        }
    }

    override fun getNextStepDepth(context: SuspendContextImpl): Int {
        try {
            val frameProxy = context.frameProxy
            val filter = methodFilter


            // smart step feature stop check
            if (filter != null &&
                frameProxy != null &&
                filter !is BreakpointStepMethodFilter &&
                filter.locationMatches(context.getDebugProcess(), frameProxy.location())
                /*&& !isTheSameFrame(context) TODO: This is a difference with super implementation */) {
                targetMethodMatched = true
                return filter.onReached(context, this)
            }

            super.getNextStepDepth(context)
        }
        catch (ignored: VMDisconnectedException) {
        }
        catch (e: EvaluateException) {
            LOG.error(e)
        }

        return STOP
    }

    override fun wasStepTargetMethodMatched(): Boolean {
        return super.wasStepTargetMethodMatched() || targetMethodMatched
    }
}

private val LOG = Logger.getInstance("#com.intellij.debugger.engine.RequestHint")

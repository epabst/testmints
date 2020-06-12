package com.zegreatrob.testmints.async

import com.zegreatrob.testmints.report.MintReporter
import kotlinx.coroutines.CoroutineScope

class Setup<C : Any>(
        private val contextProvider: suspend () -> C,
        private val scope: CoroutineScope,
        private val additionalActions: suspend C.() -> Unit,
        private val reporter: MintReporter,
        private val templateSetup: suspend () -> Unit = {},
        private val templateTeardown: suspend () -> Unit = {}
) {
    infix fun <R> exercise(codeUnderTest: suspend C.() -> R) = Exercise(
            scope,
            reporter,
            contextProvider,
            additionalActions,
            codeUnderTest,
            templateSetup,
            templateTeardown
    )

}

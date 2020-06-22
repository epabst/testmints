package com.zegreatrob.testmints

import com.zegreatrob.testmints.report.MintReporter

class Setup<C : Any, SC : Any>(
        private val contextProvider: (SC) -> C,
        private val reporter: MintReporter,
        private val additionalSetupActions: C.() -> Unit,
        private val wrapper: ((SC) -> Unit) -> Unit
) {
    infix fun <R> exercise(codeUnderTest: C.() -> R) = Exercise<C, R> { verifyFunc ->
        { teardownFunc ->
            runTest(codeUnderTest, verifyFunc, teardownFunc)
        }
    }

    private fun <R> runTest(
            exerciseFunc: ExerciseFunc<C, R>,
            verifyFunc: VerifyFunc<C, R>,
            teardownFunc: TeardownFunc<C, R>
    ) = checkedInvoke(wrapper) { sharedContext ->
        val reportedExerciseFunc = exerciseFunc.makeReporting(reporter)
        val reportedVerifyFunc = verifyFunc.makeReporting(reporter)
        val reportedTeardownFunc = teardownFunc.plus<SC, C, R>({  }).makeTeardownReporting(reporter)

        val context = setup(sharedContext)

        val result = reportedExerciseFunc(context)
        val failure = reportedVerifyFunc(context, result)

        reportedTeardownFunc(sharedContext, context, result, failure)
    }

    private fun setup(sc: SC): C {
        val context = contextProvider(sc)
        additionalSetupActions(context)
        return context
    }

}

private fun <C : Any, R> ExerciseFunc<C, R>.makeReporting(reporter: MintReporter): ExerciseFunc<C, R> = {
    reporter.exerciseStart(this)
    this@makeReporting(this)
            .also { reporter.exerciseFinish() }
}

private fun <C : Any, R> VerifyFunc<C, R>.makeReporting(mintReporter: MintReporter) = { context: C, result: R ->
    context
            .also { mintReporter.verifyStart(result) }
            .let { captureException { it.(this)(result) } }
            .also { mintReporter.verifyFinish() }
}

private fun <SC : Any, C : Any, R> TeardownFunc<C, R>.plus(templateTeardown: (SC) -> Unit) =
        { sc: SC, c: C, r: R -> captureException { c.(this)(r) } to captureException { templateTeardown(sc) } }

private fun <SC : Any, C : Any, R> ((SC, C, R) -> Pair<Throwable?, Throwable?>).makeTeardownReporting(mintReporter: MintReporter) = { sharedContext: SC, context: C, result: R, failure: Throwable? ->
    context.also { mintReporter.teardownStart() }
            .run { this@makeTeardownReporting(sharedContext, context, result) }
            .also { mintReporter.teardownFinish() }
            .let { handleTeardownExceptions(it, failure) }
}

private fun handleTeardownExceptions(pair: Pair<Throwable?, Throwable?>, failure: Throwable?) {
    val (teardownException, templateTeardownException) = pair
    val problems = exceptionDescriptionMap(teardownException, templateTeardownException, failure)

    if (problems.size == 1) {
        throw problems.values.first()
    } else if (problems.isNotEmpty()) {
        throw CompoundMintTestException(problems)
    }
}

private fun exceptionDescriptionMap(teardownException: Throwable?, templateTeardownException: Throwable?, failure: Throwable?) =
        mapOf(
                "Failure" to failure,
                "Teardown exception" to teardownException,
                "Template teardown exception" to templateTeardownException
        )
                .mapNotNull { (descriptor, exception) -> exception?.let { descriptor to exception } }
                .toMap()

private fun <SC : Any> checkedInvoke(wrapper: ((SC) -> Unit) -> Unit, test: (SC) -> Unit) {
    var testWasInvoked = false
    wrapper.invoke {
        testWasInvoked = true
        test(it)
    }
    if (!testWasInvoked) throw Exception("Incomplete test template: the wrapper function never called the test function")
}

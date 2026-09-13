package com.vuravision.classroom
import com.vuravision.classroom.math.SimpleOfflineMathEngine
import org.junit.Assert.*
import org.junit.Test
class MathEngineTest{
 @Test fun solvesLinear(){assertEquals("x = 4",SimpleOfflineMathEngine().solveLinear("2x+4=12").getOrThrow())}
 @Test fun evaluatesExpression(){assertEquals(9.0,SimpleOfflineMathEngine().evaluate("x^2-4*x+3",6.0).getOrThrow(),1e-9)}
}

package org.bitcoinj.script;

import org.bitcoinj.core.Coin;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.LinkedList;


/**
 * Base class for testing the different OPCodes in the Bitcoin script language. It provides
 * some utility methods for testing and casting between data types.
 *
 * As a general rule, the data in the stack is an array of bytes. The "StackItem" class provides
 * an abstraction over it, so the content can be treated as a number or any other data.
 *
 * the process of testing a OpCode consists of building a Script with the OPCode desired, putting the
 * needed operands into the Stack, and run the Script. The result (top of the stack) is then compared
 * to the expected result, to determine whether the result is OK or not.
 */

public class ScriptTestOpCodes {

    /**
     * Convenience methjod to print the bit pattern of a byte.
     */
    protected String printBits(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        result.append("[").append(new BigInteger(bytes).toString(2)).append("]");
        return result.toString();
    }

    /**
     * Verifies the specified Script works properly, and that the result of the execution (top of the stack) is
     * the expected result.
     */
    protected void checkTestResult(LinkedList<byte[]> originalStack, Script script, byte[] expected, EnumSet<Script.VerifyFlag> flags)
            throws Exception {

        // We execute the script over the stack given...
        Script.executeScript(null, 0, script, originalStack, Coin.ZERO, flags);

        // If the script executed without errors, now we check if the top of the stack matches the expected result...
        boolean result = true;
        byte[] topStack = originalStack.pollLast();
        if (topStack.length == expected.length) {
            for (int i = 0; i < topStack.length; i++) if (topStack[i] != expected[i]) result = false;
        } else result = false;

        // If the result is WRONG, we throw an exception...
        if (!result) {
            throw new Exception("Test ERROR: expected: " +printBits(expected)
                    +  ", actual result: " + printBits(topStack));
        }
    }

    /**
     * Verifies that the Op-Code works properly with the 2 operands provided, and the result (after executing the
     * script engine behind the scenes) is the same as the expected one.
     *
     */
    protected void checkBinaryTestResult(byte[] op1, byte[] op2, int opCode,
                                           byte[] expected, EnumSet<Script.VerifyFlag> flags) throws Exception {
        try {
            LinkedList<byte[]> stack = new LinkedList<>();
            stack.push(op1);
            stack.push(op2);

            // We build a Script that only contains the OP Code
            ScriptBuilder scriptBuilder = new ScriptBuilder();
            Script script = scriptBuilder.op(opCode).build();

            // We run the Script.
            checkTestResult(stack, script, expected, flags);
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Convenience method, using "StackItem" instans for the stack Elemewnts instead of raw byte arrays.
     */
    protected void checkBinaryTestResult(StackItem op1, StackItem op2, int opCode,
                                         StackItem expected, EnumSet<Script.VerifyFlag> flags) throws Exception {
        checkBinaryTestResult(op1.bytes(), op2.bytes(), opCode, expected.bytes(), flags);
    }


    /**
     * Verifies that the op Code works properly with the Operand provided.
     */
    protected void checkUnaryTestResult(byte[] op1, int opCode, byte[] expected, EnumSet<Script.VerifyFlag> flags)
    throws Exception {
        try {
            LinkedList<byte[]> stack = new LinkedList<>();
            stack.push(op1);

            // We build a Script that only contains the OP Code
            ScriptBuilder scriptBuilder = new ScriptBuilder();
            Script script = scriptBuilder.op(opCode).build();

            // We run the Script.
            checkTestResult(stack, script, expected, flags);

        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Convenience method, using "StackItem" instans for the stack Elemewnts instead of raw byte arrays.
     */
    protected void checkUnaryTestResult(StackItem op1, int opCode, StackItem expected, EnumSet<Script.VerifyFlag> flags)
            throws Exception {
        checkUnaryTestResult(op1.bytes(),  opCode, expected.bytes(), flags);
    }
}

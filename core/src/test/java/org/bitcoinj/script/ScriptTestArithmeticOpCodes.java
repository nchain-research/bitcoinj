package org.bitcoinj.script;

import org.junit.Test;

import java.math.BigInteger;

/**
 * Testing class for the Arithmetic Op Codes. Further coverage of OPcodes will be
 * added in the future.
 */
public class ScriptTestArithmeticOpCodes extends ScriptTestOpCodes{


    /**
     * Checks the OP_MUL Op Code.
     * For 2 oprands given, it checks different properties: Commutability, indentity, etc.
     */
    private void CheckOP_MUL(BigInteger op1, BigInteger op2, BigInteger expected) throws Exception {
        boolean result = false;

        // Negative values for multiplication
        checkBinaryTestResult(op1, op2, ScriptOpCodes.OP_MUL, expected);
        checkBinaryTestResult(op1, op2.negate(), ScriptOpCodes.OP_MUL, expected.negate());
        checkBinaryTestResult(op1.negate(), op2, ScriptOpCodes.OP_MUL, expected.negate());
        checkBinaryTestResult(op1.negate(), op2.negate(), ScriptOpCodes.OP_MUL, expected);

        // Commutativity:
        checkBinaryTestResult(op2, op1, ScriptOpCodes.OP_MUL, expected);
        checkBinaryTestResult(op2.negate(), op1, ScriptOpCodes.OP_MUL, expected.negate());
        checkBinaryTestResult(op2, op1.negate(), ScriptOpCodes.OP_MUL, expected.negate());
        checkBinaryTestResult(op2.negate(), op1.negate(), ScriptOpCodes.OP_MUL, expected);

        // Multiplication identities:
        checkBinaryTestResult(op1, "0x01", ScriptOpCodes.OP_MUL, op1);

        checkBinaryTestResult(op1, "0x81", ScriptOpCodes.OP_MUL, op1.negate());
        checkBinaryTestResult(op1, "0x00", ScriptOpCodes.OP_MUL, "0x00");

        checkBinaryTestResult("0x01", op2, ScriptOpCodes.OP_MUL, op2);
        checkBinaryTestResult("0x81", op2, ScriptOpCodes.OP_MUL, op2.negate());
        checkBinaryTestResult("0x00", op2, ScriptOpCodes.OP_MUL, "0x00");
    }

    /**
     * Convenient method, for using HEX representations for the numbers (in String format)
     */
    private void CheckOP_MUL(String op1, String op2, String expected) throws Exception {
        CheckOP_MUL(castToBigInteger(op1),castToBigInteger(op2), castToBigInteger(expected));
    }

    @Test
    public void checkOP_MUL() throws Exception{
        CheckOP_MUL("0x05","0x06", "0x1E");
        CheckOP_MUL("0x05","0x00", "0x00");
    }

}

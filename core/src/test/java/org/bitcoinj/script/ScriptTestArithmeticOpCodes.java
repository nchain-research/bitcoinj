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
    private void checkOP_MUL(String op1, String op2, String expected) throws Exception {
        boolean result = false;

        BigInteger op1BI = castToBigInteger(op1);
        BigInteger op2BI = castToBigInteger(op2);
        BigInteger expectedBI = castToBigInteger(expected);

        // Negative values for multiplication
        checkBinaryTestResult(op1BI, op2BI, ScriptOpCodes.OP_MUL, expectedBI);
        checkBinaryTestResult(op1BI, op2BI.negate(), ScriptOpCodes.OP_MUL, expectedBI.negate());
        checkBinaryTestResult(op1BI.negate(), op2BI, ScriptOpCodes.OP_MUL, expectedBI.negate());
        checkBinaryTestResult(op1BI.negate(), op2BI.negate(), ScriptOpCodes.OP_MUL, expectedBI);

        // Commutativity:
        checkBinaryTestResult(op2BI, op1BI, ScriptOpCodes.OP_MUL, expectedBI);
        checkBinaryTestResult(op2BI.negate(), op1BI, ScriptOpCodes.OP_MUL, expectedBI.negate());
        checkBinaryTestResult(op2BI, op1BI.negate(), ScriptOpCodes.OP_MUL, expectedBI.negate());
        checkBinaryTestResult(op2BI.negate(), op1BI.negate(), ScriptOpCodes.OP_MUL, expectedBI);

        // Multiplication identities:
        checkBinaryTestResult(op1BI, "0x01", ScriptOpCodes.OP_MUL, op1BI);

        checkBinaryTestResult(op1BI, "0x81", ScriptOpCodes.OP_MUL, op1BI.negate());
        checkBinaryTestResult(op1BI, "0x00", ScriptOpCodes.OP_MUL, "0x00");

        checkBinaryTestResult("0x01", op2BI, ScriptOpCodes.OP_MUL, op2BI);
        checkBinaryTestResult("0x81", op2BI, ScriptOpCodes.OP_MUL, op2BI.negate());
        checkBinaryTestResult("0x00", op2BI, ScriptOpCodes.OP_MUL, "0x00");
    }

    @Test
    public void testOP_MUL() throws Exception{
        checkOP_MUL("0x05","0x06", "0x1E");
        checkOP_MUL("0x05","0x00", "0x00");
    }

}

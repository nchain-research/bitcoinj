package org.bitcoinj.script;

import org.junit.Test;

import java.util.EnumSet;

/**
 * Testing class for Bitwise Op Codes. Further coverage of OPcodes will be
 * added in the future.
 */
public class ScriptTestBitwiseOpCodes extends ScriptTestOpCodes {

    // FLAGS used for testing
    private static EnumSet<Script.VerifyFlag> FLAGS = Script.ALL_VERIFY_FLAGS;

    private void checkOP_INVERT(Object op1, Object expected) throws Exception {

        StackItem op1El = op1 instanceof StackItem ? (StackItem) op1 :  StackItem.ofHex((String) op1);
        StackItem expEl = expected instanceof StackItem ? (StackItem) expected : StackItem.ofHex((String) expected);
        checkUnaryTestResult(op1El, ScriptOpCodes.OP_INVERT, expEl, FLAGS);
    }

    @Test
    public void testOP_INVERT() throws Exception {
        checkOP_INVERT("", "");
        checkOP_INVERT("0xFF", "0x00");
        checkOP_INVERT("0x00", "0xFF");
        checkOP_INVERT("0xFF,0xA0,0xCE,0xA0,0x96,0x12", "0x00,0x5F,0x31,0x5F,0x69,0xED");
    }
}

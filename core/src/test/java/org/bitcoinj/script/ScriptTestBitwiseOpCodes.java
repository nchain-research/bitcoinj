package org.bitcoinj.script;

import org.junit.Test;

public class ScriptTestBitwiseOpCodes extends ScriptTestOpCodes {

    private void checkOP_INVERT(String op1, String expected) throws Exception {
        checkUnaryTestResult(op1, ScriptOpCodes.OP_INVERT, expected);
    }

    @Test
    public void testOP_INVERT() throws Exception {
        checkOP_INVERT("0xFF", "0x00");
        checkOP_INVERT("0x00", "0xFF");
    }
}

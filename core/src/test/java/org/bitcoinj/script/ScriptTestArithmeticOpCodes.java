package org.bitcoinj.script;

import org.junit.Test;
import java.util.EnumSet;

/**
 * Testing class for the Arithmetic Op Codes. Further coverage of OPcodes will be
 * added in the future.
 */
public class ScriptTestArithmeticOpCodes extends ScriptTestOpCodes{

    // FLAGS used for testing
    private static EnumSet<Script.VerifyFlag> FLAGS = Script.ALL_VERIFY_FLAGS;

    private void checkOP_MUL(String op1, String op2, String expected) throws Exception {

        StackItem op1El = StackItem.ofHex(op1);
        StackItem op2El = StackItem.ofHex(op2);
        StackItem expEl = StackItem.ofHex(expected);

        // Negative values for multiplication

        checkBinaryTestResult(op1El, op2El, ScriptOpCodes.OP_MUL, expEl, FLAGS);
        checkBinaryTestResult(op1El, op2El.negative(), ScriptOpCodes.OP_MUL, expEl.negative(), FLAGS);
        checkBinaryTestResult(op1El.negative(), op2El, ScriptOpCodes.OP_MUL,  expEl.negative(), FLAGS);
        checkBinaryTestResult(op1El.negative(), op2El.negative(), ScriptOpCodes.OP_MUL, expEl, FLAGS);

        // Commutativity:
        checkBinaryTestResult(op2El, op1El, ScriptOpCodes.OP_MUL,expEl, FLAGS);
        checkBinaryTestResult(op2El.negative(), op1El, ScriptOpCodes.OP_MUL, expEl.negative(), FLAGS);
        checkBinaryTestResult(op2El, op1El.negative(),  ScriptOpCodes.OP_MUL, expEl.negative(), FLAGS);
        checkBinaryTestResult(op2El.negative(), op1El.negative(), ScriptOpCodes.OP_MUL, expEl, FLAGS);

        // Multiplication identities:
        checkBinaryTestResult(op1El, StackItem.ONE(),  ScriptOpCodes.OP_MUL, op1El, FLAGS);
        checkBinaryTestResult(op1El, StackItem.MINUSONE(),  ScriptOpCodes.OP_MUL, op1El.negative(), FLAGS);
        checkBinaryTestResult(StackItem.EMPTY(), op2El,  ScriptOpCodes.OP_MUL, StackItem.EMPTY(), FLAGS);

        checkBinaryTestResult(StackItem.ONE(), op2El,  ScriptOpCodes.OP_MUL, op2El, FLAGS);
        checkBinaryTestResult(StackItem.MINUSONE(), op2El,  ScriptOpCodes.OP_MUL, op2El.negative(), FLAGS);
        checkBinaryTestResult(op1El, StackItem.EMPTY(),  ScriptOpCodes.OP_MUL, StackItem.EMPTY(), FLAGS);
    }


    @Test
    public void testOP_MUL() throws Exception{
        checkOP_MUL("0x05","0x06", "0x1E");
        checkOP_MUL("0x05","0x26", "0xBE,0x00");

        checkOP_MUL("0x45","0x26", "0x3E,0x0A");
        checkOP_MUL("0x02","0x56,0x24", "0xAC,0x48");
        checkOP_MUL("0x05","0x26,0x03,0x32", "0xBE,0x0F,0xFA,0x00");
        checkOP_MUL("0x06","0x26,0x03,0x32,0x04", "0xE4,0x12,0x2C,0x19");
        checkOP_MUL("0xA0,0xA0","0xF5,0xE4", "0x20,0xB9,0xDD,0x0C");
        checkOP_MUL("0x05,0x26","0x26,0x03,0x32", "0xBE,0xB3,0x71,0x6D,0x07");
        checkOP_MUL("0x06,0x26","0x26,0x03,0x32,0x04", "0xE4,0xB6,0xA3,0x85,0x9F,0x00");
        checkOP_MUL("0x05,0x26,0x09","0x26,0x03,0x32", "0xBE,0xB3,0xC7,0x89,0xC9,0x01");
        checkOP_MUL("0x06,0x26,0x09","0x26,0x03,0x32,0x04", "0xE4,0xB6,0xF9,0xA1,0x61,0x26");
        checkOP_MUL("0x06,0x26,0x09,0x34","0x26,0x03,0x32,0x04", "0xE4,0xB6,0xF9,0x59,0x05,0x4F,0xDA,0x00");
    }

}

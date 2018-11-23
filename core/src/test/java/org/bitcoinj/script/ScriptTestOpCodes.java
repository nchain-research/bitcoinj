package org.bitcoinj.script;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Utils;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import static org.bitcoinj.core.Utils.HEX;

/**
 * Base class for testing the different OPCodes in the Bitcoin script language. It provides
 * some utility methods for testing and casting between data types.
 */
public class ScriptTestOpCodes {

    /**
     * Converts a byte Array into a BigInteger. The byte array follows theconventions used in the Bitcoin protocol:
     * - little endian codification: The least significatn bytes are on the left
     * - Signed magnitude representation: most significant bit contains the sign (1 = negative)
     */
    protected BigInteger castToBigInteger(byte[] chunk, boolean enforceMinimal) {
        final int DEFAULT_MAX_NUM_ELEMENT_SIZE = 4;
        if (chunk.length > DEFAULT_MAX_NUM_ELEMENT_SIZE)
            throw new ScriptException("Script attempted to use an integer larger than 4 bytes");
        if (enforceMinimal && !Utils.checkMinimallyEncodedLE(chunk, DEFAULT_MAX_NUM_ELEMENT_SIZE))
            throw new ScriptException("Number is not minimally encoded");
        //numbers on the stack or stored LE so convert as MPI requires BE.
        byte[] bytesBE = Utils.reverseBytes(chunk);
        return Utils.decodeMPI(bytesBE, false);
    }

    /**
     * Converts an number (represented as a Hex format in String) into a BigInteger.
     * The conversion follows the conventions used in the Bitcoin Script language:
     * - little endian codification: The least significatn bytes are on the left
     * - Signed magnitude representation: most significant bit contains the sign (1 = negative)
     */
    protected BigInteger castToBigInteger(String hex) throws IOException {
        int index = 0;
        if (hex.startsWith("0x")) index = 2;
        byte[] bytes = HEX.decode(hex.substring(index).toLowerCase());
        BigInteger result = castToBigInteger(bytes, false);
        return result;
    }

    /**
     * Converts a bigInteger into a byte array ready to be pushed into a Script stack, following the
     * conventions used in the Bitcoin protocol.
     * - little endian codification: The least significatn bytes are on the left
     * - Signed magnitude representation: most significant bit contains the sign (1 = negative)
     */
    protected byte[] castToByteArray(BigInteger number) {
        byte[] result = Utils.reverseBytes(Utils.encodeMPI(number, false));
        return result;
    }


    protected byte[] castToByteArray(long number) {
        byte[] bytes = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(number).array();
        List<Byte> bytesNotZero = new ArrayList();
        for (byte b : bytes) if (b != 0) bytesNotZero.add(b);
        byte[] result = new byte[bytesNotZero.size()];
        for (int i = 0; i < result.length; i++) result[i] = bytesNotZero.get(i);
        return result;
    }

    /**
     * Verifies the specified Script works properly, and that the result of the execution (top of the stack) is
     * the expected result. The script is executed with ALL the Verification Flags active.
     */
    protected void checkTestResultForAllFlags(LinkedList<byte[]> originalStack, Script script, byte[] expected)
            throws ScriptException {

        // We execute the script over the stack given...
        Script.executeScript(null, 0, script, originalStack, Coin.ZERO, Script.ALL_VERIFY_FLAGS);

        // We check that the Top of the stack contains the expected result...
        BigInteger topStackBI =  castToBigInteger(originalStack.pollLast(), false);
        BigInteger expectedBI = castToBigInteger(expected, false);
        if (!topStackBI.equals(expectedBI))
            throw new ScriptException("ERROR in Script Verification - Expected: " + expected + " actual Result: " + topStackBI);
    }

    /**
     * Verifies that the Op-Code works properly with the 2 operands provided, and the result (after executing the
     * script engine behind the scenes) is the same as the expected one.
     */
    protected void checkBinaryTestResult(BigInteger[] originalStack, BigInteger op1, BigInteger op2, int opCode, BigInteger expected)
            throws ScriptException {
        boolean result = false;
        try {
            // we feed the stack with the original one, and the 2 operands...
            LinkedList<byte[]> stack = new LinkedList<>();
            if (originalStack != null)
                for (BigInteger number : originalStack) stack.push(castToByteArray(number));
            stack.push(castToByteArray(op1));
            stack.push(castToByteArray(op2));

            // We build a Script that only contains the OP Code
            ScriptBuilder scriptBuilder = new ScriptBuilder();
            Script script = scriptBuilder.op(ScriptOpCodes.OP_MUL).build();

            // We run the Script.
            checkTestResultForAllFlags(stack, script, castToByteArray(expected));
        } catch (ScriptException se) {
            throw new ScriptException("ERROR in OPCode Verification - Expected: " + expected + " actual Result: " + result);
        }
    }


    /**
     * Convenience method, without specifiying previous content int the Stack (no previous content in it before
     * executing the Script)
     */

    protected void checkBinaryTestResult(BigInteger op1, BigInteger op2, int opCode, BigInteger expected) throws ScriptException {
        checkBinaryTestResult(null, op1, op2, opCode, expected);
    }
    protected void checkBinaryTestResult(BigInteger op1, String op2, int opCode, String expected) throws ScriptException, IOException {
        checkBinaryTestResult(op1, castToBigInteger(op2), opCode, castToBigInteger(expected));
    }
    protected void checkBinaryTestResult(BigInteger op1, String op2, int opCode, BigInteger expected) throws ScriptException, IOException {
        checkBinaryTestResult(op1, castToBigInteger(op2), opCode, expected);
    }
    protected void checkBinaryTestResult(String op1, BigInteger op2, int opCode, String expected) throws ScriptException, IOException {
        checkBinaryTestResult(castToBigInteger(op1), op2, opCode, castToBigInteger(expected));
    }
    protected void checkBinaryTestResult(String op1, BigInteger op2, int opCode, BigInteger expected) throws ScriptException, IOException {
        checkBinaryTestResult(castToBigInteger(op1), op2, opCode, expected);
    }
}

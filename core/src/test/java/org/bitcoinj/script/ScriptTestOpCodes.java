package org.bitcoinj.script;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Utils;


import java.io.IOException;
import java.math.BigInteger;

import java.util.LinkedList;

import static org.bitcoinj.core.Utils.HEX;


/**
 * Base class for testing the different OPCodes in the Bitcoin script language. It provides
 * some utility methods for testing and casting between data types.
 *
 * As a general rule, the data in the stack is an array of bytes. The interpretation of each element of the stack
 * depends on the operand we are applying: It can be treated as a number (for arithmetic operations), or just as
 * an array of bytes (for signature verification). For that reason, the operands are treated as Objects, and the
 * treatment is different depending on the actual operand data type:
 * - String: The operands is treated as a NUMBER in HEXADECIMAL notation, following the rules fo te Bitcoin
 *           Scripting language( little endian and sign bit)
 * - BigInteger: It's treated as a Number, following the rules for the Bitoin Scripting language.
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

    /**
     * Converts a number in Hexadecimal (in String format) into a byte array ready to be pushed into a Script stack,
     * following the conventions used in the Bitcoin protocol.
     * - little endian codification: The least significatn bytes are on the left
     * - Signed magnitude representation: most significant bit contains the sign (1 = negative)
     */
    protected byte[] castToByteArray(String hex) throws IOException {
        return castToByteArray(castToBigInteger(hex));
    }

    protected byte[] castToByteArray(Object obj) throws Exception {
        if (obj instanceof String) return castToByteArray((String) obj);
        if (obj instanceof BigInteger) return castToByteArray((BigInteger) obj);
        throw new Exception("Type not supported: " + obj.getClass().getName());
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
        boolean result = true;
        byte[] topStack = originalStack.pollLast();
        if (topStack.length == expected.length) {
            for (int i = 0; i < topStack.length; i++) if (topStack[i] != expected[i]) result = false;
        }
        if (!result)
            throw new ScriptException("ERROR in Script Verification. Top Stack and expected result do NOT match.");
    }

    /**
     * Verifies that the Op-Code works properly with the 2 operands provided, and the result (after executing the
     * script engine behind the scenes) is the same as the expected one.
     *
     */
    protected void checkBinaryTestResult(LinkedList<byte[]> originalStack, Object op1, Object op2, int opCode, Object expected)
            throws Exception {
        boolean result = false;
        try {
            LinkedList<byte[]> stack = new LinkedList<>();
            if (originalStack != null) stack.addAll(originalStack);
            stack.push(castToByteArray(op1));
            stack.push(castToByteArray(op2));

            // We build a Script that only contains the OP Code
            ScriptBuilder scriptBuilder = new ScriptBuilder();
            Script script = scriptBuilder.op(opCode).build();

            // We run the Script.
            checkTestResultForAllFlags(stack, script, castToByteArray(expected));
        } catch (ScriptException se) {
            throw new ScriptException("ERROR in OPCode Verification - Expected: " + expected + " actual Result: " + result);
        }
    }

    /**
     * Convenience method, without specifying an original stack trace. This methods works as if there is no data in the
     * stack priori to the execution.
     */
    protected void checkBinaryTestResult(Object op1, Object op2, int opCode, Object expected) throws Exception {
        checkBinaryTestResult(null, op1, op2, opCode, expected);
    }

    /**
     * Verifies that the op Code works properly with the Operand provided.
     */
    protected void checkUnaryTestResult(LinkedList<byte[]> originalStack, Object op1, int opCode, Object expected)
    throws Exception {
        boolean result = false;
        try {
            LinkedList<byte[]> stack = new LinkedList<>();
            if (originalStack != null) stack.addAll(originalStack);
            stack.push(castToByteArray(op1));

            // We build a Script that only contains the OP Code
            ScriptBuilder scriptBuilder = new ScriptBuilder();
            Script script = scriptBuilder.op(opCode).build();

            // We run the Script.
            checkTestResultForAllFlags(stack, script, castToByteArray(expected));

        } catch (ScriptException se) {
            throw new ScriptException("ERROR in OPCode Verification - Expected: " + expected + " actual Result: " + result);
        }
    }

    /**
     * Convenience method, without specifying an original stack trace. This methods works as if there is no data in the
     * stack priori to the execution.
     */
    protected void checkUnaryTestResult(Object op1, int opCode, Object expected) throws Exception {
        checkUnaryTestResult(null, op1, opCode, expected);
    }
}

package wallettemplate;

import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bitcoinj.core.*;
import org.bitcoinj.script.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InteractiveScriptStateController extends ScriptStateListener {
    public TextField scriptPub;
    public TextField scriptSig;
    public Button debugBtn;
    public ComboBox verifyFlags;
    private Text textOpCode;
    //private VBox dialogVbox;

    private String fullScriptString;


    private void debugTrx(ActionEvent actionEvent) {
        if (scriptPub.getText() == null || scriptPub.getText().isEmpty()) {
            scriptPub.getStyleClass().add("validation_error");
            return;
        }

        if (scriptSig.getText() == null || scriptSig.getText().isEmpty()) {
            scriptSig.getStyleClass().add("validation_error");
            return;
        }

        //DUP HASH160 PUSHDATA(20)[f7d52018971f4ab9b56f0036958f84ae0325ccdc] EQUALVERIFY CHECKSIG
        //5 4 ADD 2 DIV
        Script scriptPubKey = ScriptBuilder.createOutputScript(ECKey.fromPrivate(scriptPub.getText().getBytes()));
        //PUSHDATA(71)[304402201c999cf44dc6576783c0f55b8ff836a1e22db87ed67dc3c39515a6676cfb58e902200b4a925f9c8d6895beed841db135051f8664ab349f2e3ea9f8523a6f47f9388301] PUSHDATA(33)[02e58d7b931b5d43780fda0abc50cfd568fcc26fb7da6a71591a43ac8e0738b9a4]
        Script scriptSig = ScriptBuilder.createOutputScript(ECKey.fromPrivate(this.scriptSig.getText().getBytes()));

        LinkedList<byte[]> stack = new LinkedList<>();

        // ScriptStateListener listener = new InteractiveScriptStateListener(true);


        textOpCode = new Text();
       // textOpCode.setText(results.getText() + "\n***Executing scriptSig***\n");
      //  dialogVbox.getChildren().add(textOpCode);

        Script.executeDebugScript(null, 0, scriptSig, stack, Coin.ZERO, Collections.singleton(Script.VerifyFlag.valueOf(verifyFlags.getValue().toString())), this);
     //   results.setText(results.getText() + "\n***Stack for scriptKey***\n" + stack);
    //    results.setText(results.getText() + "\n***Executing scriptPubKey***\n");
        Script.executeDebugScript(null, 0, scriptPubKey, stack, Coin.ZERO, Collections.singleton(Script.VerifyFlag.valueOf(verifyFlags.getValue().toString())), this);

    }


    @Override
    public void onBeforeOpCodeExecuted(boolean willExecute) {

        if (getChunkIndex() == 0) {
            fullScriptString = truncateData(String.valueOf(getScript()));
        //    results.setText(results.getText() + fullScriptString);
        }
        textOpCode = new Text();
        textOpCode.setText("OPCode = " + ScriptOpCodes.getOpCodeName(getCurrentChunk().opcode));
  //      dialogVbox.getChildren().add(textOpCode);
     //   results.setText(results.getText() + String.format("\nExecuting %s operation: [%s]", getCurrentChunk().isOpCode() ? "OP_CODE" : "PUSHDATA", ScriptOpCodes.getOpCodeName(getCurrentChunk().opcode)));
    }

    @Override
    public void onAfterOpCodeExecuted() {

        ScriptBuilder builder = new ScriptBuilder();

        for (ScriptChunk chunk : getScriptChunks().subList(getChunkIndex(), getScriptChunks().size())) {
            builder.addChunk(chunk);
        }

        Script remainScript = builder.build();
        String remainingString = truncateData(remainScript.toString());
        int startIndex = fullScriptString.indexOf(remainingString);
        String markedScriptString = fullScriptString.substring(0, startIndex) + "^" + fullScriptString.substring(startIndex);
        //System.out.println("Remaining code: " + remainingString);
        //results.setText(results.getText() + "Execution point (^): " + markedScriptString);
        System.out.println();

        //dump stacks
        List<byte[]> reverseStack = new ArrayList<>(getStack());
        Collections.reverse(reverseStack);
       // results.setText(results.getText() + "Stack:");

        if (reverseStack.isEmpty()) {
         //   results.setText(results.getText() + "empty");
        } else {
            int index = 0;
            for (byte[] bytes : reverseStack) {

              //  results.setText(results.getText() + String.format("index[%s] len[%s] [%s]", index++, bytes.length, HEX.encode(bytes)));

            }
        }
        System.out.println();

        if (!getAltstack().isEmpty()) {
            reverseStack = new ArrayList<>(getAltstack());
            Collections.reverse(reverseStack);
          //  results.setText(results.getText() + "Alt Stack:");

            for (byte[] bytes : reverseStack) {
                //results.setText(results.getText() + HEX.encode(bytes));
            }
            System.out.println();
        }

        if (!getIfStack().isEmpty()) {
            List<Boolean> reverseIfStack = new ArrayList<>(getIfStack());
            Collections.reverse(reverseIfStack);
         //   results.setText(results.getText() + "If Stack:");

            for (Boolean element : reverseIfStack) {
         //       results.setText(results.getText() + element);
            }
            System.out.println();
        }


    }

    @Override
    public void onExceptionThrown(ScriptException exception) {
        System.out.println("Exception thrown: ");
    }

    @Override
    public void onScriptComplete() {
        List<byte[]> stack = getStack();
        if (stack.isEmpty() || !Script.castToBool(stack.get(stack.size() - 1))) {
     //       results.setText("\n" + results.getText() + "Script failed.");
        } else {
        //    results.setText("\n" + results.getText() + "Script success.");
        }
    }

    private String truncateData(String scriptString) {

        Pattern p = Pattern.compile("\\[(.*?)]");
        Matcher m = p.matcher(scriptString);

        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String data = m.group(0);
            if (data.length() > 10) {
                data = data.substring(0, 5) + "..." + data.substring(data.length() - 5);
            }
            m.appendReplacement(sb, data);
        }
        m.appendTail(sb);

        return sb.toString();
    }


    public void debugClicked(ActionEvent event) {
        debugTrx(event);
    }

}

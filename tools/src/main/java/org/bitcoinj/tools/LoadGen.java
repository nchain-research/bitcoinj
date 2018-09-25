/*
 * Copyright 2018 bitcoinj-cash developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.tools;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.GetDataEventListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.crypto.*;

import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.GbtnParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.LevelDBBlockStore;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import joptsimple.*;

import java.math.BigInteger;
import java.security.SecureRandom;

import org.iq80.leveldb.*;

import javax.annotation.Nullable;

import static org.fusesource.leveldbjni.JniDBFactory.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A command line tool for generating transaction load
 *
 * only suitable for test networks:
 *   - does not encrypt key material
 *   - testing has been minimal
 *   - recovery not implemented
 *   - trusts only one connection
 *
 *
 * four components:
 *  - mempool - maintains set of transactions
 *  - utxodb  - utxo database
 *  - generator - produces transactions
 *  - blockwatcher - watches for confirmations
 *
 * utxodb
 *      - maintains persisent database of our utxo
 *      - when a deposit is made, the utxo is inserted here
 *      - when a transaction has been sent to a node, the utxodb is updated
 *              - spent utxo are removed
 *              - outputs of the transaction are added
 *      - has ready, used, and waitingConfirm pools
 *      - a utxo consists of:
 *          - outpoint
 *          - value
 *          - isConfirmed - boolean
 *          - isDeposit - boolean
 *          - ancestor count - number of unconfirmed ancestors
 *
 * generator
 *      - takes eligible utxo from the utxodb and produces transactions
 *      - sends the transactions to the mempool
 *      - can be paused by the mempool
 *
 *  mempool
 *      - keeps transactions, has one connection to a node
 *      - sends new tx in an inv to the node periodically
 *      - responds to getdata requests
 *          - sends tx to utxodb to update the utxo set
 *          - sends tx to blockwatcher
 *          - keeps tx in mempool in case needed later
 *      - has upper and lower capacity thresholds
 *          - pauses generator when upper has been reached
 *          - resumes generator when goes below lower
 *      - when notified by blockwatcher, removes confirmed tx from mempool
 *
 *  blockwatcher - watches for confirmations
 *      - receives tx from mempool after they have been sent to node
 *      - checks to see if tx confirmed in blocks
 *          - updates utxodb to set status and reset ancestor count for confirmed tx
 *          - sends confirmation to mempool so that it is removed
 */
public class LoadGen {
    private static final Logger log = LoggerFactory.getLogger(LoadGen.class);
    private static OptionSet options;
    private static NetworkParameters params = new GbtnParams();
    private static Context context = new Context(params);
    private static LoadGen loadGen;
    private static int MAX_ANCESTOR_DEPTH = 1;
    private static int TARGET_VALUE = 1000000;      // target value for first tx
    private static int MAX_SPLIT = 20;              // split large value outpoints into max this many

    public class FlowControl {
        private boolean finshAll = false;
        private boolean generateTx = true;
        private CountDownLatch waitForFinish = new CountDownLatch(1);

        public synchronized boolean isGenerateTx() {
            return generateTx;
        }

        public synchronized void setGenerateTx(boolean generateTx) {
            this.generateTx = generateTx;
        }

        public synchronized boolean isFinishAll() {
            return finshAll;
        }

        public synchronized void setFinshAll() {
            this.waitForFinish.countDown();
            this.finshAll = true;
            log.info("FinishAll set");
        }

        public void waitForFinish() {
            try {
                waitForFinish.await();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    static public class OutPoint implements Serializable {
        private Sha256Hash txHash = null;
        private long index;
        private Coin value = null;
        private byte[] pubKeyScriptProgram = null;
        private boolean confirmed = false;
        private boolean deposit = false;
        private int ancestorDepth = MAX_ANCESTOR_DEPTH;

        public Sha256Hash getTxHash() {
            return txHash;
        }

        public void setTxHash(Sha256Hash hash) {
            this.txHash = hash;
        }

        public long getIndex() {
            return index;
        }

        public void setIndex(long index) {
            this.index = index;
        }

        public Coin getValue() {
            return value;
        }

        public void setValue(Coin value) {
            this.value = value;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed) {
            this.confirmed = confirmed;
        }

        public boolean isDeposit() {
            return deposit;
        }

        public void setDeposit(boolean deposit) {
            this.deposit = deposit;
        }

        public int getAncestorDepth() {
            return ancestorDepth;
        }

        public void setAncestorDepth(int ancestorDepth) {
            this.ancestorDepth = ancestorDepth;
        }

        public Script getPubKeyScript() {
            return new Script(this.pubKeyScriptProgram);
        }

        public void setPubKeyScript(Script pubKeyScript) {
            this.pubKeyScriptProgram = pubKeyScript.getProgram();
        }

        public String getId() {
            return new StringBuilder().append("o:")
                    .append(getTxHash().toString())
                    .append(":").append(getIndex())
                    .toString();
        }
    }

    public OutPoint createOutPoint(TransactionOutput output, Transaction tx, boolean isDeposit) {
        OutPoint ret = new OutPoint();
        ret.setTxHash(output.getParentTransactionHash());
        ret.setIndex(output.getIndex());
        ret.setValue(output.getValue());
        ret.setPubKeyScript(output.getScriptPubKey());
        if (tx.getConfidence().getDepthInBlocks()>0) {
            ret.setConfirmed(true);
        } else {
            ret.setConfirmed(false);
        }
        ret.setDeposit(isDeposit);
        return ret;
    }

    public OutPoint deserializeOutPoint(byte[] bytes) throws IOException, ClassNotFoundException {
        OutPoint result = null;
        try(ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream is = new ObjectInputStream(bis)) {
            result = (OutPoint) is.readObject();
        } catch (Exception e) {
            log.error("deserializeOutPoint(byte[])", e);
            throw(e);
        }
        return result;
    }

    class UTXODB implements Runnable {
        private final FlowControl flowControl;
        private final File dbPath;
        private final BlockWatcher blockWatcher;
        private DB db = null;
        private CountDownLatch keyReady = new CountDownLatch(1);
        private DeterministicKey key;
        private BlockingQueue<UTXODBAction> inQ = new LinkedBlockingQueue<>();
        private BlockingQueue<OutPoint> readyOutPoints = new LinkedBlockingQueue<>();
        private Map<Sha256Hash, Set<OutPoint>> spentOutPoints = new HashMap<>();
        private Map<Sha256Hash, Set<OutPoint>> waitingConfirm = new HashMap<>();

        private abstract class UTXODBAction {}
        private class AddOutPoint extends UTXODBAction {
            private OutPoint outPoint;
            public AddOutPoint(OutPoint in) {
                this.outPoint = in;
            }

            public OutPoint getOutPoint() {
                return this.outPoint;
            }
        }
        private class TxSent extends UTXODBAction {
            private Transaction t;
            public TxSent(Transaction t) {
                this.t = t;
            }

            public Transaction getTransaction() {
                return this.t;
            }
        }

        public UTXODB(FlowControl flowControl, final File path, BlockWatcher blockWatcher) {
            assert(blockWatcher != null);
            this.flowControl = flowControl;
            this.dbPath = path;
            this.blockWatcher = blockWatcher;
        }

        synchronized public boolean isError() {
            if (db == null) {
                return true;
            } else {
                return false;
            }
        }

        public DeterministicKey getKey() {
            try {
                keyReady.await();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            return key;
        }

        public OutPoint getReadyOutPoint() {
            OutPoint retval = null;
            try {
                retval = readyOutPoints.poll(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            return retval;
        }

        public void addOutPoint(OutPoint o) {
            inQ.add(new AddOutPoint(o));
        }

        public void txSent(Transaction t) {
            inQ.add(new TxSent(t));
        }

        private String outPointDBKey(Sha256Hash txid, long index) {
            return String.format("o:%s:%d", txid.toString(), index);
        }

        private OutPoint get(Sha256Hash txid, long index) throws Exception {
            byte[] valBytes = db.get(bytes(outPointDBKey(txid, index)));
            if (valBytes != null) {
                return deserializeOutPoint(valBytes);
            }
            return null;
        }

        private void put(String key, byte[] bytes){
            db.put(key.getBytes(), bytes);
        }

        private void put(String key, String value) {
            put(key, value.getBytes());
            log.info(String.format("db: wrote key %s, val %s", key, value));
        }

        private void put(OutPoint o) {
            try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(bos)) {
                os.writeObject(o);
                os.flush();
                put(o.getId(),bos.toByteArray());
            } catch (Exception e) {
                log.error(String.format("UTXODB:put(OutPoint) - cause: %s", e.getCause().toString()));
            }
        }

        // remove the outpoint associated with this input, if it exists
        private void remove(TransactionInput input) {
            db.delete(bytes(outPointDBKey(input.getOutpoint().getHash(),input.getOutpoint().getIndex())));
        }

        // wait for confirmations for these outpoints
        private void waitForConfirm(OutPoint o) {
            Set<OutPoint> val = waitingConfirm.get(o.getTxHash());
            if (val == null) {
                val = new HashSet<>();
                blockWatcher.watchTransaction(o.getTxHash());
            }
            val.add(o);
            waitingConfirm.put(o.getTxHash(), val);
        }

        // send outpoint onwards or hold for confirmation
        private void processOutPoint(OutPoint o) {
            if (!o.isConfirmed() && (o.isDeposit() || o.getAncestorDepth() >= MAX_ANCESTOR_DEPTH)) {
                waitForConfirm(o);
            } else {
                readyOutPoints.add(o);
                log.info("OutPoint added to readyOutPoints");
            }
        }

        // perform the add outpoint command
        private void performAddOutPoint(OutPoint o) {
            put(o);
            processOutPoint(o);
        }

        // a tx has been sent to a node
        private void performTxSent(Transaction tx) throws Exception {
            int maxDepth = 0;
            for (TransactionInput input : tx.getInputs()) {
                OutPoint old = get(input.getOutpoint().getHash(),input.getOutpoint().getIndex());
                if (old != null) {
                    if (!old.isConfirmed() && old.getAncestorDepth() > maxDepth) {
                        maxDepth = old.getAncestorDepth();
                    }
                    remove(input);
                }
            }
            maxDepth++;
            for (TransactionOutput output : tx.getOutputs()) {
                OutPoint o = createOutPoint(output, tx, false);
                o.setAncestorDepth(maxDepth);
                performAddOutPoint(o);
            }
        }

        // read in the exsting db and process the utxo
        private void readExistingDB() throws IOException {
            int counter = 0;
            DBIterator iterator = db.iterator();
            try {
                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    String keyValue = asString(iterator.peekNext().getKey());
                    if (keyValue != null && keyValue.length() > 2 && keyValue.substring(0,2).equals("o:")) {
                        log.info("UTXODB: found OutPoint {}", keyValue);
                        OutPoint o = null;
                        try {
                            o = deserializeOutPoint(iterator.peekNext().getValue());
                        } catch (Exception e) {
                            log.warn("cant deserialize {}, ignoring", keyValue, e);
                        }
                        if (o != null) {
                            processOutPoint(o);
                            counter++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("UTXODB:readExistingDB(): {}", e.getMessage());
            } finally {
                iterator.close();
            }
            log.info("read {} UTXO from db", counter);
        }

        @Override
        public void run() {
            Context.propagate(context);

            Options dbOptions = new Options();
            dbOptions.createIfMissing(true);
            try {
                this.db = factory.open(dbPath, dbOptions);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            if (db == null) return;

            // get the key
            byte[] keyBytes = db.get(bytes("key"));
            if (keyBytes == null) {
                this.key = HDKeyDerivation.createMasterPrivateKey((new BigInteger(256, new SecureRandom())).toByteArray());
                db.put(bytes("key"), this.key.serializePrivate(params));
            } else {
                this.key = DeterministicKey.deserialize(params,keyBytes);
            }
            keyReady.countDown();

            try {
                readExistingDB();
            } catch (IOException e) {
                log.error("UTXDODB:run() - IOException from readExistingDB()", e);
            }

            try {
                while (!flowControl.isFinishAll()) {
                    UTXODBAction a = inQ.poll(100, TimeUnit.MILLISECONDS);
                    if ( a != null ) {
                        if (a instanceof AddOutPoint) {
                            OutPoint o = ((AddOutPoint) a).getOutPoint();
                            performAddOutPoint(o);
                        } else if (a instanceof TxSent) {
                            Transaction tx = ((TxSent) a).getTransaction();
                            performTxSent(tx);
                        }
                    }
                }

                db.close();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    class MemPool implements Runnable {
        private abstract class MemPoolAction {}
        private class AddTransaction extends MemPoolAction {
            private Transaction t;
            public AddTransaction(Transaction t) {
                this.t = t;
            }

            public Transaction getTransaction() {
                return t;
            }
        }
        private class RespondToGetData extends MemPoolAction {
            private GetDataMessage message;
            private BlockingQueue<List<Message>> responseQ;
            public RespondToGetData(GetDataMessage message, BlockingQueue<List<Message>> responseQ) {
                this.message = message;
                this.responseQ = responseQ;
            }

            public GetDataMessage getMessage() {
                return message;
            }

            public BlockingQueue<List<Message>> getResponseQ() {
                return responseQ;
            }
        }
//        private class SendFinished extends MemPoolAction {
//            private Transaction t;
//            private boolean success;
//            public SendFinished(Transaction t, boolean success) {
//                this.t =t;
//                this.success= success;
//            }
//
//            public Transaction getTransaction() {
//                return t;
//            }
//
//            public boolean isSuccess() {
//                return success;
//            }
//        }

        private class TxRecord {
            private final Transaction tx;
            private long invLastSent = 0;
            private long dataLastSent = 0;

            public TxRecord(Transaction t) {
                tx = t;
            }

            public Transaction getTx() {
                return tx;
            }

            public long getInvLastSent() {
                return invLastSent;
            }

            public void setInvLastSent(long invLastSent) {
                this.invLastSent = invLastSent;
            }

            public long getDataLastSent() {
                return dataLastSent;
            }

            public void setDataLastSent(long dataLastSent) {
                this.dataLastSent = dataLastSent;
            }
        }

        private final long SOFT_CAP = 5000;
        private final long HARD_CAP = 10000;
        private final int MAX_INV_ENTRIES = 5000;  // max entries in inv to send, must be <= 50000
        private long numTx = 0;
        private long sentTx = 0;
        private FlowControl flowControl;
        private PeerGroup peerGroup;
        private BlockWatcher blockWatcher;
        private boolean generateTx = true;      // our copy of the flow control tx generation flag
//        private TxRecord sendingTxR = null;   // the transaction being sent
        private long lastInvSent = 0;           // time last inv sent (milliseconds)
        private long lastStatusSent = 0;        // time last status message sent (milliseconds)
        private BlockingQueue<MemPoolAction>    inQ = new LinkedBlockingQueue<>();
        private Map<Sha256Hash, TxRecord>       txMap = new HashMap<>();
        private BlockingQueue<TxRecord>         sendQ = new LinkedBlockingQueue<>();
        private Queue<TxRecord>                 invResendQ = new LinkedList<>();

        public MemPool(FlowControl flowControl, PeerGroup peerGroup, BlockWatcher blockWatcher) {
            this.flowControl = flowControl;
            this.peerGroup = peerGroup;
            this.blockWatcher = blockWatcher;
        }

        public void addTransaction(Transaction t) {
            inQ.add(new AddTransaction(t));
        }

        // callback from Peer when it receives a getdata message
        public List<Message> respondToGetData(GetDataMessage msg) {
            BlockingQueue<List<Message>> responseQ = new ArrayBlockingQueue<>(1);
            inQ.add(new RespondToGetData(msg, responseQ));
            List<Message> result = null;
            try {
                result = responseQ.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("respondToGetData() interrupted");
            }
            return result;
        }

//        public void sendingFinished(Transaction t, boolean success) {
//            inQ.add(new SendFinished(t, success));
//        }

        // do we need to restrict inbound tx?
        private void checkInboundFlow() {
            if (generateTx && (numTx >= HARD_CAP)) {
                generateTx = false;
                flowControl.setGenerateTx(false);
            }
            if (!generateTx && (numTx < SOFT_CAP)) {
                generateTx = true;
                flowControl.setGenerateTx(true);
            }
        }

        // send an inv message at most once a second
        private void maybeSendInv() {
            if (System.currentTimeMillis() > (lastInvSent + 1000)) {
                int count = 0;
                List<TxRecord> toSend = new ArrayList<>(MAX_INV_ENTRIES);
                TxRecord txR = sendQ.poll();
                while ((count < MAX_INV_ENTRIES ) && (txR != null)) {
                    toSend.add(txR);
                    count++;
                    txR = sendQ.poll();
                }
                // resends
                txR = invResendQ.peek();
                while ((count < MAX_INV_ENTRIES) && (txR != null)
                        && (System.currentTimeMillis() > (txR.getInvLastSent() + 60*1000))) {
                    txR = invResendQ.remove();
                    if (txR.getDataLastSent() == 0) {
                        toSend.add(txR);
                        count++;
                    }
                    txR = invResendQ.peek();
                }

                if (count > 0) {
                    InventoryMessage msg = new InventoryMessage(params);
                    lastInvSent = System.currentTimeMillis();
                    for( TxRecord record : toSend) {
                        msg.addTransaction(record.getTx());
                        record.setInvLastSent(lastInvSent);
                        invResendQ.add(record);
                    }
                    for( Peer peer : peerGroup.getConnectedPeers()) {
                        peer.sendMessage(msg);
                    }
                    log.info("sent peers inv message with {} tx", count);
                }
            }
//            if (sendingTxR == null) {
//                TxRecord txR = sendQ.poll();
//                if (txR != null) {
//                    TransactionBroadcast tb = peerGroup.broadcastTransaction(txR.getTx());
//                    Futures.addCallback(tb.future(), new FutureCallback<Transaction>() {
//                        @Override
//                        public void onSuccess(Transaction transaction) {
//                            sendingFinished(transaction, true);
//                        }
//
//                        @Override
//                        public void onFailure(Throwable throwable) {
//                            sendingFinished(null, false);
//                        }
//                    });
//                    sendingTxR = txR;
//                }
//            }
        }

        // send a short status report every once in a while
        private void maybePrintStatus() {
            if (System.currentTimeMillis() > (lastStatusSent + 60000)) {
                log.info("mempool status: {} tx in mempool, {} tx sent", numTx, sentTx);
                lastStatusSent = System.currentTimeMillis();
            }
        }

        // respond to a getdata message from a peer
        private List<Message> processRespondToGetData(GetDataMessage message) {
            List<Message> result = new LinkedList<>();
            for (InventoryItem item : message.getItems()) {
                if (item.type == InventoryItem.Type.Transaction) {
                    TxRecord record = txMap.get(item.hash);
                    if (record != null) {
                        result.add(record.getTx());
                        record.setDataLastSent(System.currentTimeMillis());
                        utxodb.txSent(record.getTx());
                        blockWatcher.watchTransaction(record.getTx().getHash());
                        sentTx++;
                    }
                }
            }
            return result;
        }

        @Override
        public void run() {
            // when new peers connect, configure getdata event listener
            peerGroup.addConnectedEventListener(Threading.SAME_THREAD, new PeerConnectedEventListener() {
                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    peer.addGetDataEventListener(Threading.SAME_THREAD, new GetDataEventListener() {
                        @Nullable
                        @Override
                        public List<Message> getData(Peer peer, GetDataMessage m) {
                            return respondToGetData(m);
                        }
                    });
                }
            });
            // register with existing peers
            for (Peer peer : peerGroup.getConnectedPeers()) {
                peer.addGetDataEventListener(Threading.SAME_THREAD, new GetDataEventListener() {
                    @Nullable
                    @Override
                    public List<Message> getData(Peer peer, GetDataMessage m) {
                        return respondToGetData(m);
                    }
                });
            }
            try {
                while (!flowControl.isFinishAll()) {
                    MemPoolAction action = inQ.poll(100, TimeUnit.MILLISECONDS);
                    if (action != null) {
                        if (action instanceof AddTransaction) {
                            Transaction t = ((AddTransaction) action).getTransaction();
                            TxRecord record = new TxRecord(t);
                            log.debug("mempool: received transaction {}", t.getHashAsString());
                            txMap.put(t.getHash(), record);
                            sendQ.add(record);
                            numTx++;
                        } else if (action instanceof RespondToGetData) {
                            GetDataMessage msg = ((RespondToGetData) action).getMessage();
                            ((RespondToGetData) action).getResponseQ().add(processRespondToGetData(msg));
                        }
//                        else if (action instanceof SendFinished) {
//                            if (((SendFinished) action).success) {
//                                log.info("mempool: sent tx {}", sendingTxR.getTx().getHashAsString());
//                                utxodb.txSent(sendingTxR.getTx());
//                                txMap.remove(sendingTxR.getTx().getHash());
//                                numTx--;
//                                sentTx++;
//                            } else {
//                                sendQ.add(sendingTxR);
//                            }
//                            sendingTxR = null;
//                        }
                    } else {    // nothing in inQ so catch up on other stuff
                        maybeSendInv();
                        maybePrintStatus();
                    }

                    checkInboundFlow();
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    class Generator implements Runnable {
        private FlowControl flowControl;
        private UTXODB utxodb;
        private MemPool memPool;

        public Generator(FlowControl flowControl, MemPool memPool, UTXODB utxodb) {
            assert(flowControl != null);
            assert(utxodb != null);
            assert(memPool != null);
            this.flowControl =flowControl;
            this.utxodb = utxodb;
            this.memPool = memPool;
        }

        @Override
        public void run() {
            Context.propagate(context);
            try {
                while (!flowControl.isFinishAll()){
                    while (!flowControl.isGenerateTx() && ! flowControl.isFinishAll()) {
                        TimeUnit.MILLISECONDS.sleep(100);
                    }
                    if (!flowControl.isFinishAll()) {
                        OutPoint o = utxodb.getReadyOutPoint();
                        if (o != null) {
                            Transaction t;
                            if (o.value.getValue() > 2 * TARGET_VALUE) {
                                t = split(o);
                            } else {
                                t = generate(o);
                            }
                            if (t != null) {
                                memPool.addTransaction(t);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        private Transaction split(OutPoint o){
            long num = o.value.getValue() / TARGET_VALUE > MAX_SPLIT ? MAX_SPLIT : o.value.getValue() / TARGET_VALUE;
            Coin value = o.value.div(num);
            Transaction t = new Transaction(params);
            Coin total = Coin.ZERO;
            for( long i = 1; i < num; i++) {        // leave last outpoint for change & fee
                t.addOutput(value,getWorkAddress());
                total = total.add(value);
            }
            assert(total.getValue() < o.value.getValue());
            // TODO: currently last output is used as fee, calculate properly
            ECKey signKey;
            if (o.deposit) {
                signKey = getDepositKey();
            } else {
                signKey = getWorkKey();
            }
            TransactionOutPoint tOutPoint = new TransactionOutPoint(params, o.getIndex(), o.getTxHash());
            t.addSignedInput(tOutPoint, o.getValue(), o.getPubKeyScript(), signKey);
            return t;
        }

        private Transaction generate(OutPoint o) {
            Transaction t = new Transaction(params);
            // TODO: use proper fee
            t.addOutput(o.value.subtract(Coin.valueOf(400)), getWorkAddress());
            ECKey signKey;
            if (o.deposit) {
                signKey = getDepositKey();
            } else {
                signKey = getWorkKey();
            }
            TransactionOutPoint tOutPoint = new TransactionOutPoint(params, o.getIndex(), o.getTxHash());
            t.addSignedInput(tOutPoint, o.getValue(), o.getPubKeyScript(), signKey);
            return t;
        }
    }

    class BlockWatcher implements Runnable {
        private FlowControl flowControl;

        public BlockWatcher(FlowControl flowControl) {
            this.flowControl = flowControl;
        }

        private abstract class BlockWatcherAction {};
        private class BlockReceived extends BlockWatcherAction {

        }
        public void watchTransaction(Sha256Hash txId) {
            log.info("watch for tx {}", txId.toString());
        }

        @Override
        public void run() {
            try {
                while (!flowControl.isFinishAll()) {
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    private String dataDirName;
    private DeterministicKey key;
    private DeterministicKey depositKey;
    private DeterministicKey workKey;
    private Wallet depositWallet;
    private BlockStore blockStore;
    private BlockChain blockChain;
    private PeerGroup peerGroup;

    private FlowControl flowControl = new FlowControl();
    private UTXODB utxodb;
    private MemPool memPool;
    private BlockWatcher blockWatcher;
    private Generator generator;

    private Thread tMemPool;
    private Thread tBlockWatcher;
    private Thread tGenerator;
    private Thread tUtxodb;



    public LoadGen(String dataDirName) {
        this.dataDirName = dataDirName;
    }

    // command line action
    public enum ActionEnum {
        GETDEPOSITADDRESS,
        GETWORKADDRESS,
        STATUS,
        WITHDRAW,
        RUN,
    }

    public void perform(ActionEnum action) throws Exception {
        switch (action) {
            case GETDEPOSITADDRESS:
                cmdGetDepositAddress();
                break;
            case GETWORKADDRESS:
                cmdGetWorkAddress();
                break;
            case STATUS:
                cmdStatus();
                break;
            case WITHDRAW:
                cmdWithdraw();
                break;
            case RUN:
                cmdRun();
                break;
            default:
                System.err.println("unrecognized command");
        }
    }

    private File getDBDir() {
        File path = new File(getDataDirName(),"utxodb");
        if (!path.exists()) {
            path.mkdirs();
        }
        return path;
    }

    private File getChainDir() {
        File path = new File(getDataDirName(),"chain");
        if (!path.exists()) {
            path.mkdirs();
        }
        return path;
    }

    private void openUTXODB() throws IOException {
        this.blockWatcher = new BlockWatcher(flowControl);
        this.utxodb = new UTXODB(flowControl, getDBDir(), blockWatcher);
        this.tUtxodb = new Thread(utxodb);
        this.tUtxodb.start();
    }

    public void closeAll() throws Exception {
        log.info("closing ...");
        if (peerGroup != null) {
            peerGroup.stop();
        }
        if (blockStore != null) {
            blockStore.close();
        }
        if (tMemPool != null) {
            tMemPool.join();
        }
        if (tBlockWatcher != null) {
            tBlockWatcher.join();
        }
        if (tGenerator != null) {
            tGenerator.join();
        }
        if (tUtxodb != null) {
            tUtxodb.join();
        }
    }

    private void readDBConfig() throws IOException {
        this.key = utxodb.getKey();
    }

    private DeterministicKey getDepositKey() {
        assert(this.key != null);
        if (this.depositKey == null) {
            this.depositKey = key.derive(1);
        }
        return this.depositKey;
    }

    private Address getDepositAddress() {
        return new Address(params, getDepositKey().getIdentifier());
    }

    private DeterministicKey getWorkKey() {
        assert(this.key != null);
        if (this.workKey == null) {
            this.workKey = key.derive(2);
        }
        return this.workKey;
    }

    private Address getWorkAddress() {
        return new Address(params, getWorkKey().getIdentifier());
    }

    /**
     * setup the blockchain, store, and deposit wallet
     */
    private void setupChainWallet() throws Exception {
        blockStore = new LevelDBBlockStore(context, getChainDir());
        depositWallet = new Wallet(context);
        depositWallet.addWatchedAddress(getDepositAddress());
        // listen for deposits
        depositWallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                List<TransactionOutput> myOutputs = tx.getWalletOutputs(wallet);
                for (TransactionOutput output: myOutputs) {
                    utxodb.addOutPoint(createOutPoint(output, tx, true));
                }
            }
        });
        blockChain = new BlockChain(context, depositWallet, blockStore);
    }

    /**
     * connect to the Bitcoin network & setup listeners for deposits
     */
    private void setupConnection() {
        log.info("connecting to bitcoin network...");
        peerGroup = new PeerGroup(context, blockChain);
        peerGroup.setMaxConnections(1);
        peerGroup.addPeerDiscovery(new DnsDiscovery(context.getParams()));
        peerGroup.start();
        log.info("connected to bitcoin network");
    }

    private void cmdGetDepositAddress() throws IOException {
        openUTXODB();
        readDBConfig();
        System.out.println(getDepositAddress().toString());
    }

    private void cmdGetWorkAddress() throws  IOException {
        openUTXODB();
        readDBConfig();
        System.out.println(getWorkAddress().toString());
    }

    private void cmdStatus() throws Exception {
        openUTXODB();
        readDBConfig();
        setupChainWallet();
        System.out.println(String.format("Deposit Address: %s", getDepositAddress().toString()));
        System.out.println(String.format("Work Address: %s", getWorkAddress().toString()));
        System.out.println(String.format("Chain height: %d, Best Block:%s", blockChain.getBestChainHeight(), blockChain.getChainHead().getHeader().getHashAsString()));
    }

    private void cmdWithdraw() {
        System.out.println("not implemented yet");
    }

    private void cmdRun() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                flowControl.setFinshAll();
            }
        });

        openUTXODB();
        readDBConfig();
        System.out.println(String.format("Deposit Address: %s", getDepositAddress().toString()));
        System.out.println(String.format("Work Address: %s", getWorkAddress().toString()));
        setupChainWallet();
        System.out.println(String.format("Chain height: %d, Best Block:%s", blockChain.getBestChainHeight(), blockChain.getChainHead().getHeader().getHashAsString()));
        setupConnection();

        memPool = new MemPool(flowControl, peerGroup, blockWatcher);
        generator = new Generator(flowControl, memPool, utxodb);

        tMemPool = new Thread(memPool);
        tMemPool.start();
        tGenerator = new Thread(generator);
        tGenerator.start();
        tBlockWatcher = new Thread(blockWatcher);
        tBlockWatcher.start();
        flowControl.waitForFinish();
    }

    public String getDataDirName() {
        return dataDirName.replaceFirst("^~", System.getProperty("user.home"));
    }

    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        parser.accepts("help");
        parser.accepts("debuglog");
        OptionSpec<String> dataDirName = parser.accepts("datadir").withRequiredArg().defaultsTo("~/.loadgen");
        options = parser.parse(args);

        if (args.length == 0 || options.has("help") ||
                options.nonOptionArguments().size() < 1 || options.nonOptionArguments().contains("help")) {
            System.out.println(Resources.toString(WalletTool.class.getResource("loadgen-help.txt"), Charsets.UTF_8));
            return;
        }

        ActionEnum action;
        try {
            String actionStr = options.nonOptionArguments().get(0);
            actionStr = actionStr.toUpperCase().replace("-", "_");
            action = ActionEnum.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            System.err.println("Could not understand action name " + options.nonOptionArguments().get(0));
            return;
        }

        loadGen = new LoadGen(options.valueOf(dataDirName));
        loadGen.perform(action);
        loadGen.closeAll();
    }
}

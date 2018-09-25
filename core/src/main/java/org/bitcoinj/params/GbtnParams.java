package org.bitcoinj.params;

import org.bitcoinj.core.Utils;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the Gigablock Test Network, a separate Bitcoin Cash test network that has is used for capacity
 * testing.
 */
public class GbtnParams extends AbstractBitcoinNetParams {
        public GbtnParams() {
            super();
            id = ID_GBTNET;
            packetMagic = 0xfbcec4f9L;
            interval = INTERVAL;
            targetTimespan = TARGET_TIMESPAN;
            maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
            port = 9333;
            addressHeader = 25;
            p2shHeader = 68;
            acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
            dumpedPrivateKeyHeader = 35;
            genesisBlock.setTime(1296688602L);
            genesisBlock.setDifficultyTarget(0x1d00ffffL);
            genesisBlock.setNonce(414098458);
            spendableCoinbaseDepth = 100;
            subsidyDecreaseBlockCount = 210000;
            String genesisHash = genesisBlock.getHashAsString();
            checkState(genesisHash.equals("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"));
            alertSigningKey = Utils.HEX.decode("04302390343f91cc401d56d68b123028bf52e5fca1939df127f63c6467cdf9c8e2c14b61104cf817d0b780da337893ecc4aaff1309e536162dabbdb45200ca2b0a");

            dnsSeeds = new String[] {
                    "gbtn-seed.bitcoinsv.io"
            };
            addrSeeds = null;
            bip32HeaderPub = 0x42696720;
            bip32HeaderPriv = 0x426C6B73;

            majorityEnforceBlockUpgrade = TestNet2Params.TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
            majorityRejectBlockOutdated = TestNet2Params.TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
            majorityWindow = TestNet2Params.TESTNET_MAJORITY_WINDOW;

            // Aug, 1 hard fork
            uahfHeight = 15;
            // Nov, 13 hard fork
            daaUpdateHeight = 2200;
            cashAddrPrefix = "bchgbtn";
        }

        private static GbtnParams instance;
        public static synchronized GbtnParams get() {
            if (instance == null) {
                instance = new GbtnParams();
            }
            return instance;
        }

        @Override
        public String getPaymentProtocolId() {
            return PAYMENT_PROTOCOL_ID_TESTNET;
        }
}

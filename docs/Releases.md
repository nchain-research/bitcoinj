# bitcoinj-cash Release History

### Release Notes 0.14.6 - To be finalized

* support for Monolith (May 2018) upgrade
  * max 32MB blocks
  * re-enabled opcodes, including clean up of script interpreter
  * check for minimally encoded numeric values
* added CashAddress support  
* ScriptStateListener added for Script debugging purposes
* fix Spillman Payment Channels (v1)
* DAA implementation improvements, including support for checkpoints
* remove inactive testnet dns seeder
* improved testing
  * added integration tests
* added capability to provide input value when signing transaction
* remove orchid sub-project
* refactor Proof of Work validation
* updated minimum support JDK to 7
* removed RBF code  

### Release Notes 0.14.5.2 - 2018-01-24

* fix DAA issue with initial syncing

### Release Notes 0.14.5.1 - 2018-01-14

* fix DAA for testnet
* add block height for mainnet DAA activation, replacing MTP

### Release Notes 0.14.5 - 2017-12-11

Initial release of Bitcoin Cash version of bitcoinj.

Forked from bitcoinj (https://github.com/bitcoinj/bitcoinj), incorporates work from the bitcoincash-wallet bitcoinj (https://github.com/bitcoincash-wallet/bitcoincashj) by HashEngineering.

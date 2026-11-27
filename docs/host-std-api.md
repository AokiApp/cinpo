# Host APIs for ISO 7816-4 and GlobalPlatform

`app.aoki.cinpo.apdu.*` and `app.aoki.cinpo.gp.scp.*` are not CINPO concepts. They are thin and literal implementations of ISO 7816-4 command framing, the common ISO command set, and the GlobalPlatform secure channel, so the classes have the shape that the specifications give them and CINPO adds no vocabulary of its own.

The line between CINPO and the standards runs through configuration. Deciding which ISD AID, key version, security level and keys to use is CINPO's business rather than the standard's, and that decision is modelled by `Profile` and `ScpConfig`, declared in `profile/<name>.yaml` and documented in [Writing host-side tasks](tasks.md#api-reference). This page documents only the Java API that puts the resulting bytes on the wire.

A task never builds the transport or the key material itself. Both arrive as inherited fields, `channel` and `secureChannelProfile`, which the loader populates before `run()` is called (see [task dependencies](tasks.md#task-dependencies)). A task uses those objects directly and passes them to the factories described below.

## APDU exchange

### `app.aoki.cinpo.apdu.ApduChannel`

`ApduChannel` is an interface that represents the live connection between the host and the card. You never construct an implementation: each runtime (`jcdksim`, `jcardengine`, `pcsc`) ships its own, and the loader assigns it to the inherited `channel` field before your task runs.

#### `transmit(CommandApdu capdu)`

`transmit` sends one command and returns the complete `ResponseApdu`. It declares no checked exception, so a transport failure can only surface as an unchecked exception from the implementation. The method does not inspect the status word; see [Checking status words](#checking-status-words).

#### `reset()`

`reset` drops the transport and session state so that the next `transmit` starts from a fresh card context, which is the equivalent of a cold restart. It does not erase the card's persistent data.

#### `close()`

`close` releases the resources that the runtime holds. `ApduChannel` extends `AutoCloseable` and this method declares no checked exception, so code that owns a channel may use it in a try-with-resources statement. A task does not own its channel and therefore does not close it.

### `app.aoki.cinpo.apdu.CommandApdu`

`CommandApdu` is an immutable value object that represents a single command APDU. You build it from the four header bytes plus an optional data field and an optional Le, then hand it to `ApduChannel.transmit`; you never assemble the wire bytes yourself. Every accessor returns either an `int` or a copy, so a command cannot be modified after it has been built.

The three constructors validate their arguments the same way. `cla`, `ins`, `p1` and `p2` must fall in `0x00`–`0xFF`, or the constructor throws `IllegalArgumentException`. The data array is copied, `null` is treated as no data, and the maximum length is 65535 bytes. `le` is `null` or a value in `1`–`65536`, and `0` is rejected.

#### `CommandApdu(int cla, int ins, int p1, int p2)`

This constructor stores the four header bytes and leaves both optional fields empty.

#### `CommandApdu(int cla, int ins, int p1, int p2, byte[] data)`

This constructor adds the data field and leaves `le` empty.

#### `CommandApdu(int cla, int ins, int p1, int p2, byte[] data, Integer le)`

This constructor stores all six fields. Passing `null` for either optional argument gives the same result as the corresponding shorter constructor.

#### `cla()`, `ins()`, `p1()`, `p2()`

These four accessors return the header bytes as `int` values in `0x00`–`0xFF`.

#### `data()`

`data` returns a copy of the data field. A command that carries no data returns an empty array rather than `null`.

#### `le()`

`le` returns the expected response length as an `Integer`, or `null` when the command declares none.

#### `hasData()`, `hasLe()`

`hasData` reports whether the data field is non-empty, and `hasLe` reports whether `le` is present.

#### `isExtendedLength()`

`isExtendedLength` reports whether `toBytes()` must use extended length, which happens when the data exceeds 255 bytes or `le` exceeds 256.

#### `toBytes()`

`toBytes` returns the wire encoding of the command and chooses between the short and extended forms according to `isExtendedLength()`.

#### `toHexString()`, `toString()`

Both methods return the hexadecimal form of `toBytes()`, so either one is fine for logging.

#### `CommandApdu.fromBytes(byte[] bytes)`

`fromBytes` parses a command in wire format and returns an equivalent value object. It throws `IllegalArgumentException` when the input is inconsistent: fewer than four bytes, a data field shorter than the declared Lc, an extended header without its two-byte length, or an extended Lc of `0000`.

#### `CommandApdu.fromHexString(String hex)`

`fromHexString` decodes the string and parses the result exactly as `fromBytes` does.

### `app.aoki.cinpo.apdu.ResponseApdu`

`ResponseApdu` is an immutable value object that represents one response APDU: the data field plus SW1 and SW2. `ApduChannel.transmit` returns it, and nearly every task inspects it.

#### `new ResponseApdu(byte[] data, int sw1, int sw2)`

This constructor copies the data array and treats `null` as an empty field. Both status bytes must fall in `0x00`–`0xFF`, or it throws `IllegalArgumentException`.

#### `data()`

`data` returns a copy of the response data, which is an empty array when the card returned none.

#### `sw1()`, `sw2()`

These accessors return the two status bytes separately as `int` values.

#### `sw()`

`sw` combines the status bytes into the value `SW1 << 8 | SW2`, which is the form you compare against constants such as `0x9000`.

#### `toBytes()`

`toBytes` returns the wire form of the response, which is the data followed by SW1 and SW2.

#### `toHexString()`, `toString()`

Both methods return the hexadecimal form of `toBytes()`.

#### `ResponseApdu.fromBytes(byte[] bytes)`

`fromBytes` treats the last two bytes as the status word and everything before them as data. It throws `IllegalArgumentException` when the array holds fewer than two bytes.

#### `ResponseApdu.fromHexString(String hex)`

`fromHexString` decodes the string and parses the result exactly as `fromBytes` does.

### `app.aoki.cinpo.apdu.Iso7816Commands`

`Iso7816Commands` is a utility class whose static methods build the commands a provisioning task commonly needs. Every method returns a `CommandApdu` and none of them transmit, so the status word stays with the caller. The class has a private constructor and cannot be instantiated.

Instruction codes:

| Constant | Value |
|----------|-------|
| `INS_VERIFY` | `0x20` |
| `INS_CHANGE_REFERENCE_DATA` | `0x24` |
| `INS_GET_CHALLENGE` | `0x84` |
| `INS_SELECT` | `0xA4` |
| `INS_READ_BINARY` | `0xB0` |
| `INS_GET_DATA` | `0xCA` |
| `INS_PUT_DATA` | `0xDA` |
| `INS_UPDATE_BINARY` | `0xD6` |

Method and option bytes for SELECT:

| Constant | Value |
|----------|-------|
| `SELECT_METHOD_BY_DF_NAME` | `0x04` |
| `SELECT_METHOD_EF_UNDER_CURRENT_DF` | `0x02` |
| `SELECT_OPTION_FIRST_OR_ONLY_OCCURRENCE` | `0x00` |
| `SELECT_OPTION_RETURN_FCI_TEMPLATE` | `0x04` |
| `SELECT_OPTION_RETURN_NO_DATA` | `0x0C` |

#### `selectDf(byte[] dfName)`

This overload builds a SELECT by DF name and uses `SELECT_OPTION_RETURN_NO_DATA` for the options byte. The AID must be 1–16 bytes, or the method throws `IllegalArgumentException`.

#### `selectDf(byte[] dfName, int selectOptions)`

This overload is the same command with a caller-supplied options byte.

#### `selectDf(byte[] dfName, int selectOptions, Integer le)`

This overload additionally declares a response length, which is passed through to `le`.

#### `selectEf(int fileId)`

This overload builds a SELECT of an element file under the currently selected DF and uses `SELECT_OPTION_RETURN_NO_DATA` for the options byte. The file ID must fall in `0x0000`–`0xFFFF`, or the method throws `IllegalArgumentException`.

#### `selectEf(int fileId, boolean requestFci)`

This overload uses `SELECT_OPTION_RETURN_FCI_TEMPLATE` when `requestFci` is true and `SELECT_OPTION_RETURN_NO_DATA` when it is false.

#### `selectEf(int fileId, boolean requestFci, Integer le)`

This overload additionally declares a response length, which is passed through to `le`.

#### `readBinary(int offset, int le)`

`readBinary` builds a read of `le` bytes from `offset`. The offset must fall in `0`–`32767`, or the method throws `IllegalArgumentException`; the `le` argument is stored unchanged.

#### `readBinaryBySfi(int sfi, int offset, int le)`

This variant addresses the file by short file identifier. The SFI must fall in 0–31 and the offset in 0–255, and either violation throws `IllegalArgumentException`.

#### `updateBinary(int offset, byte[] data)`

`updateBinary` builds a write of `data` at `offset`, with the same offset limit as `readBinary`. The data must be non-empty, so `null` or a zero-length array throws `IllegalArgumentException`.

#### `updateBinaryBySfi(int sfi, int offset, byte[] data)`

This variant uses the SFI form with the same limits as `readBinaryBySfi` and requires non-empty data.

#### `verify(int referenceDataNumber, byte[] verificationData)`

`verify` builds a VERIFY command whose reference number occupies the low seven bits of the second parameter byte. The method validates the number as a byte, so a value above `0x7F` is accepted but collides with its low seven bits. The verification data must be non-empty.

#### `changeReferenceData(int referenceDataNumber, byte[] newReferenceData)`

`changeReferenceData` builds the same shape with INS `0x24`, and the same notes about the reference number and the non-empty data apply.

#### `getChallenge(int le)`

`getChallenge` builds a GET CHALLENGE command that requests `le` bytes.

#### `getData(int tag)`

`getData` builds a GET DATA command for one data object. A single-byte tag must fall in `0x40`–`0xFE` and a two-byte tag in `0x4001`–`0xFFFE`; any other value, including `0xFFFF`, throws `IllegalArgumentException`.

#### `getData(int tag, Integer le)`

This overload additionally declares a response length, which is passed through to `le`.

#### `putData(int tag, byte[] value)`

`putData` builds a PUT DATA command with the same tag range as `getData` and a non-empty value.

## Checking status words

Every response ends in SW1 and SW2, and nothing in the APDU layer interprets those bytes for you. Compare `response.sw()` with the status word the command is supposed to return, and throw when it differs:

```java
private void transmitOk(CommandApdu command) {
    ResponseApdu response = channel.transmit(command);
    if (response.sw() != 0x9000) {
        throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
    }
}
```

Retrying is usually wrong for APDU commands, because a failing status word means that the card rejected the command rather than that the transport hiccuped. The exception you throw then aborts the pipeline and the command exits non-zero; see the [failure model](tasks.md#failure-model).

## GlobalPlatform secure channel

### `app.aoki.cinpo.gp.scp.SecureChannelProfile`

`SecureChannelProfile` is an immutable record that holds the key material for one secure channel. The loader derives it from the active `Profile` and assigns it to `CardTask.secureChannelProfile`, so a task reads that field instead of building a profile; the factories below exist for that derivation and for tests. Every `byte[]` accessor returns a copy.

The canonical constructor validates the whole record. It throws `NullPointerException` for a null `scp`, security domain AID, or key, and `IllegalArgumentException` for an empty AID, a key that is not 16, 24 or 32 bytes, key lengths that disagree, a host challenge that is not 8 bytes, or a sequence counter that is not 2 bytes. A null `hostChallenge` and a null sequence counter are accepted.

#### `scp()`

`scp` returns the protocol variant, `Scp.SCP02` or `Scp.SCP03`. `SecureChannelSession.create` switches on this value, so the profile decides which protocol a session speaks.

#### `securityDomainAid()`

`securityDomainAid` returns the AID of the security domain that the session authenticates against.

#### `keyVersionNumber()`, `keyIdentifier()`, `securityLevel()`

These return `int` values that were validated as bytes, so they lie in `0x00`–`0xFF`. The security level is the session bitmap that the card negotiates during authentication; `0x01` means C-MAC only and `0x03` adds C-DECRYPTION. For SCP03 the key identifier must be `0x00`, although the record only range-checks the byte.

#### `encKey()`, `macKey()`, `dekKey()`

These return copies of the three static keys. `encKey` and `macKey` must have the same length, and a non-null `dekKey` must match them. `dekKey()` returns `null` when the profile carries no DEK.

#### `hostChallenge()`

`hostChallenge` returns the 8-byte challenge that INITIALIZE UPDATE sends to the card. When the caller passes `null`, the profile generates a random value with `SecureRandom`.

#### `scp02SequenceCounter()`

`scp02SequenceCounter` returns the 2-byte counter for SCP02 implicit initiation, or `null` for explicit initiation and for SCP03.

#### `SecureChannelProfile.scp03(securityDomainAid, keyVersionNumber, keyIdentifier, securityLevel, encKey, macKey, dekKey)`

This factory creates an SCP03 profile with a random host challenge and no sequence counter. The `dekKey` argument may be `null`.

#### `SecureChannelProfile.scp02(securityDomainAid, keyVersionNumber, keyIdentifier, securityLevel, encKey, macKey, dekKey, hostChallenge, scp02SequenceCounter)`

This factory creates an SCP02 profile. Pass a 2-byte `scp02SequenceCounter` for implicit initiation, or `null` for explicit initiation. SCP02 is deprecated in the GlobalPlatform specifications, so prefer `scp03` for new work.

### `app.aoki.cinpo.gp.scp.SecureChannelSession`

`SecureChannelSession` is a short-lived object that turns a channel and a profile into an authenticated secure channel. A session moves from `CREATED` to `AUTHENTICATED`, and ends in `CLOSED` or, after any runtime failure, in `ABORTED`; an aborted or closed session cannot be revived. All public methods synchronize on an internal monitor, so one session can be shared between threads.

#### `SecureChannelSession.create(ApduChannel channel, SecureChannelProfile profile)`

`create` chooses the protocol from `profile.scp()` and returns a session in the `CREATED` state. It sends nothing and does not select the security domain. A null profile throws `NullPointerException`.

#### `authenticate()`

`authenticate` runs the mutual authentication handshake, derives the session keys, and starts MAC chaining. The card's security domain must already be selected, because the caller issues that plain SELECT. Calling the method on an `AUTHENTICATED` session does nothing. On success the state becomes `AUTHENTICATED`; on any `RuntimeException` it becomes `ABORTED` and the exception is rethrown. Calling it on an `ABORTED` or `CLOSED` session throws `IllegalStateException`.

#### `transmit(CommandApdu capdu)`

`transmit` protects the command according to the negotiated security level, sends it over the underlying channel, and returns the unwrapped response. It throws `IllegalStateException` unless the session is `AUTHENTICATED`, which includes any call made after `close()`. Any `RuntimeException` moves the session to `ABORTED`, from which it cannot recover.

#### `isAuthenticated()`, `isAborted()`, `isClosed()`

These methods report the current state. `isAuthenticated` is true only between a successful `authenticate()` and a call to `close()`.

#### `close()`

`close` moves the session to `CLOSED` and drops the session keys and ICVs. It is idempotent and safe to call in any state. It does not close the underlying `ApduChannel`, because the caller owns that object, so using the session in try-with-resources is safe.

## GlobalPlatform installation

CINPO installs the applet before it runs your tasks, and this section documents the code that does so. `InstallPhase` reads the CAP archive, opens the secure channel, loads the executable, and registers each applet instance. These types are CINPO's own rather than ISO 7816-4 or GlobalPlatform vocabulary, and a task normally does not call them; they are documented because they are public and because a custom installation step may need them.

### `app.aoki.cinpo.gp.GpInstaller`

`GpInstaller` runs the complete installation over a session that is already authenticated, and it keeps the order that the specification requires.

#### `install(SecureChannelSession session, byte[] loadFileAid, List<AppletEntry> applets, CapPackage capPackage, boolean deleteFirst)`

`install` performs four steps: it optionally deletes the existing load file together with its applets, it sends INSTALL [for load], it sends one LOAD command per block of the CAP package, and it sends INSTALL [for install and make selectable] for each entry.

The session must already be authenticated, and the caller selects the security domain first. When `deleteFirst` is true, the method sends DELETE [object and related] and accepts `6A88` and `6A82` as success, because those status words mean that the object did not exist.

A null `session`, `capPackage`, or `applets` argument throws `NullPointerException`, and an empty list throws `IllegalArgumentException`. Any command that returns an unexpected status word throws `IllegalStateException`, and the message names the step, the block index, or the applet that failed. If a LOAD block fails after INSTALL [for load] was accepted, the card may be left with a partial load file, so the caller recovers by deleting the load file AID.

#### `AppletEntry(String id, byte[] executableModuleAid, byte[] instanceAid, byte[] privileges, byte[] installParameters)`

`AppletEntry` describes one applet instance to install. The `id` is a label for logs and never reaches the card, and it defaults to `<unnamed>` when null. Both AIDs must be 5–16 bytes, and a null or out-of-range value throws `IllegalArgumentException`. `privileges` accepts null, one byte, or three bytes, and null becomes `00 00 00`. `installParameters` is optional. The record copies every array it receives, and every accessor returns a copy.

### `app.aoki.cinpo.gp.GpCommands`

`GpCommands` is a factory for the GlobalPlatform management commands. Every method returns a `CommandApdu`, and none of them transmit.

| Constant | Value | Used as |
|----------|-------|---------|
| `INS_INSTALL` | `0xE6` | INS |
| `INS_LOAD` | `0xE8` | INS |
| `INS_DELETE` | `0xE4` | INS |
| `INS_GET_STATUS` | `0xF2` | INS |
| `INS_INITIALIZE_UPDATE` | `0x50` | INS |
| `INS_EXTERNAL_AUTHENTICATE` | `0x82` | INS |
| `INSTALL_P1_FOR_LOAD` | `0x02` | INSTALL P1 |
| `INSTALL_P1_FOR_INSTALL` | `0x04` | INSTALL P1 |
| `INSTALL_P1_FOR_MAKE_SELECTABLE` | `0x08` | INSTALL P1 |
| `INSTALL_P1_FOR_INSTALL_AND_MAKE_SELECTABLE` | `0x0C` | INSTALL P1 |
| `INSTALL_P2_NONE` | `0x00` | INSTALL P2 |
| `INSTALL_P2_BEGIN_COMBINED_LOAD_INSTALL` | `0x01` | INSTALL P2 |
| `INSTALL_P2_END_COMBINED_LOAD_INSTALL` | `0x03` | INSTALL P2 |
| `LOAD_P1_MORE_BLOCKS` | `0x00` | LOAD P1 |
| `LOAD_P1_LAST_BLOCK` | `0x80` | LOAD P1 |
| `DELETE_P1_LAST_OR_ONLY` | `0x00` | DELETE P1 |
| `DELETE_P1_MORE_COMMANDS` | `0x80` | DELETE P1 |
| `DELETE_P2_DELETE_OBJECT` | `0x00` | DELETE P2 |
| `DELETE_P2_DELETE_OBJECT_AND_RELATED` | `0x80` | DELETE P2 |

#### `initializeUpdate(int keyVersionNumber, int keyIdentifier, byte[] hostChallenge)`

`initializeUpdate` builds the command that starts authentication, with the key version and key identifier as P1 and P2 and the host challenge as the data field. The challenge must be exactly 8 bytes, or the method throws `IllegalArgumentException`. The command declares a response length of 256.

#### `externalAuthenticate(int securityLevel, byte[] data)`

`externalAuthenticate` builds the command that completes authentication, with the security level as P1. The data field holds the host cryptogram followed by the command MAC, and a null or empty array throws `IllegalArgumentException`.

#### `getStatus(int p1, int p2, byte[] criteria)`

`getStatus` builds a GET STATUS command that returns registry information. The `criteria` argument is the search data field, and a null value throws `IllegalArgumentException`. The command declares a response length of 256.

#### `install(int p1, int p2, byte[] data)`

`install` builds a raw INSTALL command with caller-supplied P1, P2, and data, and a null data field throws `IllegalArgumentException`. The higher-level helpers below are preferable when they cover the operation you need.

#### `installForLoad(byte[] loadFileAid, byte[] securityDomainAid)`

`installForLoad` builds INSTALL [for load] with P1 `0x02` and P2 `0x00`. The load file AID must be 5–16 bytes. A null `securityDomainAid` means the default security domain, and any other value must also be 5–16 bytes.

#### `installForInstall(byte[] loadFileAid, byte[] executableModuleAid, byte[] instanceAid, byte[] privileges, byte[] installParameters)`

`installForInstall` builds INSTALL [for install] with P1 `0x04`. It registers the instance without making it selectable, so a separate command is needed afterwards. Every AID must be 5–16 bytes, `privileges` must be one or three bytes, and a null `installParameters` selects the default value `C9 00`.

#### `installForInstallAndMakeSelectable(byte[] loadFileAid, byte[] executableModuleAid, byte[] instanceAid, byte[] privileges, byte[] installParameters)`

This method builds the combined INSTALL [for install and make selectable] command with P1 `0x0C`, and it validates its arguments exactly as `installForInstall` does.

#### `load(int p1, int blockNumber, byte[] blockData)`

`load` builds one LOAD command with the caller's block number and data, and a null data field throws `IllegalArgumentException`. The command declares a response length of 256.

#### `delete(int p1, int p2, byte[] data)`

`delete` builds a raw DELETE command with caller-supplied P1, P2, and data, and a null data field throws `IllegalArgumentException`.

#### `deleteAid(byte[] aid)`

`deleteAid` builds DELETE [object] with P2 `0x00`, so only the identified object is removed. The AID must be 5–16 bytes.

#### `deleteAidAndRelated(byte[] aid)`

`deleteAidAndRelated` builds DELETE [object and related] with P2 `0x80`, so the load file and all of its applets are removed together. Per the specification, the data field carries only the load file AID.

### `app.aoki.cinpo.gp.cap.CapLoader`

`CapLoader` reads a CAP archive and prepares the data that the LOAD commands transmit. It concatenates the CAP components in Java Card VM order, wraps the result in the `C4` load file data block, and splits that block into pieces of at most `maxBlockSize` bytes. It also parses the header and applet components for metadata, and it tolerates missing metadata by leaving the corresponding fields null or empty.

#### `DEFAULT_MAX_LOAD_BLOCK_SIZE`

`DEFAULT_MAX_LOAD_BLOCK_SIZE` is 247, which is the conservative payload for a block when SCP03 appends an 8-byte C-MAC to the 255-byte command limit.

#### `readCapFile(Path capPath)`

`readCapFile` reads the archive with the default block size. It throws `IOException` when the file cannot be read or contains no recognizable CAP component.

#### `readCapFile(Path capPath, int maxBlockSize)`

This overload sets the block size explicitly. The value must lie in 1–255, or the method throws `IllegalArgumentException`, and a null path throws `NullPointerException`.

#### `readFromClasspath(String resourcePath)`

`readFromClasspath` reads the archive from the classpath, relative to the classpath root, and uses the default block size. It throws `IOException` when the resource is missing or unreadable.

### `app.aoki.cinpo.gp.cap.CapPackage`

`CapPackage` is an immutable record that holds the parsed archive. The constructor copies every array and list, and every accessor returns a copy.

#### `packageAid()`, `hasPackageAid()`

`packageAid` returns the package AID, or null when the header could not be parsed. `hasPackageAid` reports whether it is present.

#### `majorVersion()`, `minorVersion()`

These return the package version from the header, or `-1` when the header could not be parsed.

#### `appletAids()`

`appletAids` returns the AIDs that the `Applet.cap` component declares, or an empty list when the component is missing.

#### `loadFileData()`

`loadFileData` returns the complete `C4`-wrapped load file data block before segmentation.

#### `loadBlocks()`

`loadBlocks` returns the load file data split into the blocks that the LOAD commands send.

### `app.aoki.cinpo.gp.GpCrypto`

`GpCrypto` collects the raw cryptographic primitives that the SCP02 and SCP03 implementations use. Every method is static and stateless, and the ones that can fail declare `GeneralSecurityException`.

#### `aesEcbEncrypt(byte[] key, byte[] block)`

Encrypts one block with AES in ECB mode, and throws `GeneralSecurityException` when the cipher cannot be initialised.

#### `aesCbcEncrypt(byte[] key, byte[] iv, byte[] message)`

Encrypts the message with AES in CBC mode using the supplied IV.

#### `aesCbcEncryptSingleBlock(byte[] key, byte[] block)`

Encrypts one block with AES in CBC mode using a zero IV.

#### `aesCbcDecrypt(byte[] key, byte[] iv, byte[] cipherText)`

Decrypts the ciphertext with AES in CBC mode using the supplied IV.

#### `cmac(byte[] key, byte[] message)`

Computes the AES-CMAC of the message and returns the eight most significant bytes, which is the MAC length that the secure channel protocols use.

#### `des3CbcEncrypt(byte[] key24, byte[] iv8, byte[] data)`

Encrypts the data with three-key triple DES in CBC mode.

#### `full3DesCbcMac(byte[] key16, byte[] paddedData)`

Computes the full triple DES CBC MAC over already padded data.

#### `retailMac(byte[] key16, byte[] icv, byte[] paddedData)`

Computes the retail MAC over already padded data, starting from the given ICV.

#### `desEcbEncrypt(byte[] key8, byte[] data)`

Encrypts the data with single DES in ECB mode.

#### `expand3DesKey(byte[] key)`

Expands a 16-byte two-key triple DES key into a 24-byte three-key key.

#### `padIso9797Method2(byte[] input, int blockSize)`

Pads the input to a multiple of the block size using ISO 9797-1 method 2.

#### `removeIso9797Method2Padding(byte[] padded)`

Removes the padding that `padIso9797Method2` added.

#### `xor(byte[] left, byte[] right)`

Returns the byte-wise XOR of two equal-length arrays.

### `app.aoki.cinpo.gp.GpUtil`

`GpUtil` exposes the security-level and CLA helpers that the secure channel implementations share. The AID and privilege normalizers and the C-MAC predicate are package-private, so they are not part of this surface.

#### `secureMessagingCla(int logicalCla)`

`secureMessagingCla` converts a logical CLA byte into the GP secure-messaging CLA, setting the secure-messaging bits and preserving the channel bits.

#### `usesCDecryption(int securityLevel)`

Reports whether the security level requests C-DECRYPTION, which requires both low bits of the level to be set.

#### `usesRMac(int securityLevel)`

Reports whether the security level requests R-MAC.

#### `usesREncryption(int securityLevel)`

Reports whether the security level requests R-ENCRYPTION, which requires both bits of the relevant pair to be set.

## Worked examples

Each example below is a complete task class, in which `CardTask` and `@CardTaskDef` come from CINPO. All the examples assume these imports:

```java
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskDef;
import java.nio.charset.StandardCharsets;
```

Examples that concern the task lifecycle rather than the protocol, such as declaring kinds or reading the manifest, are in [Writing host-side tasks](tasks.md#worked-examples).

### A provision task that SELECTs and writes the PIN

```java
package provision;

@CardTaskDef("provision")
public final class SetupPin extends CardTask {

    @Override
    public void run() {
        transmitOk(Iso7816Commands.selectDf(manifest.applets().get(0).instanceAid()));
        transmitOk(Iso7816Commands.changeReferenceData(0x01, "1234".getBytes(StandardCharsets.US_ASCII)));
    }

    private void transmitOk(CommandApdu command) {
        ResponseApdu response = channel.transmit(command);
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("APDU failed: SW=%04X".formatted(response.sw()));
        }
    }
}
```

### A test task

```java
package test;

@CardTaskDef("test")
public final class VerifyPinWorks extends CardTask {

    @Override
    public void run() {
        byte[] aid = manifest.applets().get(0).instanceAid();
        channel.transmit(Iso7816Commands.selectDf(aid));

        ResponseApdu response = channel.transmit(
            Iso7816Commands.verify(0x01, "1234".getBytes(StandardCharsets.US_ASCII)));
        if (response.sw() != 0x9000) {
            throw new IllegalStateException("PIN verify failed: SW=%04X".formatted(response.sw()));
        }
    }
}
```

### A task inside its own secure channel session

```java
package provision;

import app.aoki.cinpo.gp.scp.SecureChannelSession;

@CardTaskDef(value = "provision", order = 200)
public final class WriteKeys extends CardTask {

    @Override
    public void run() {
        channel.transmit(Iso7816Commands.selectDf(secureChannelProfile.securityDomainAid()));

        SecureChannelSession session = SecureChannelSession.create(channel, secureChannelProfile);
        session.authenticate();
        try {
            session.transmit(new CommandApdu(0x80, 0xD4, 0x00, 0x00, new byte[] { /* key material */ }));
        } finally {
            session.close();
        }
    }
}
```

The `order = 200` makes this task run after the provision tasks that use the default order of `0`. The SELECT before `authenticate()` is required, because the session assumes that the security domain is already selected.

## Where to look next

- [Writing host-side tasks](tasks.md) covers the task lifecycle, discovery, arguments, and the CINPO types `CardTask`, `AppletManifest`, `Profile` and `ScpConfig`.
- [Profiles](profiles.md) explains where the ISD AID, security level and keys that this API consumes are declared, and when to choose `scp02` or `scp03`.
- The source lives in `cinpo-core/src/main/java/app/aoki/cinpo/apdu/` and `cinpo-core/src/main/java/app/aoki/cinpo/gp/scp/`.

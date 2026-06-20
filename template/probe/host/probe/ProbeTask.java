package probe;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.Iso7816Commands;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.task.CardTask;
import app.aoki.cinpo.task.CardTaskDef;
import app.aoki.cinpo.task.Inject;
import app.aoki.cinpo.task.TaskArguments;
import app.aoki.cinpo.util.Util;
import java.util.Optional;

@CardTaskDef("probe")
public final class ProbeTask implements CardTask {

    private static final CommandApdu SELECT_DEFAULT_APPLICATION = CommandApdu.fromHexString("00A4040000");
    private static final int TAG_CARD_DATA = 0x66;

    @Inject
    private ApduChannel channel;

    @Inject
    private TaskArguments arguments;

    public ProbeTask() {
    }

    @Override
    public void run() {
        selectTarget();
        printCardData();
    }

    private void selectTarget() {
        Optional<byte[]> aid = targetAid();
        ResponseApdu response = channel.transmit(aid.map(ProbeTask::selectByName).orElse(SELECT_DEFAULT_APPLICATION));

        if (response.sw() != 0x9000) {
            System.out.println("SELECT failed: SW=%04X".formatted(response.sw()));
            return;
        }

        if (aid.isPresent()) {
            System.out.println("Selected AID: " + Util.toHex(aid.get()));
        } else {
            System.out.println("Selected default application");
        }
    }

    private Optional<byte[]> targetAid() {
        return arguments.firstValue("aid").map(value -> {
            byte[] aid = Util.parseHex(value);
            if (aid.length < 5 || aid.length > 16) {
                throw new IllegalArgumentException("--aid must be 5 to 16 bytes");
            }
            return aid;
        });
    }

    private void printCardData() {
        ResponseApdu response = channel.transmit(getData(TAG_CARD_DATA));
        if (response.sw() == 0x9000) {
            System.out.println("Card data: " + Util.toHex(response.data()));
        } else {
            System.out.println("Card data unavailable: SW=%04X".formatted(response.sw()));
        }
    }

    private static CommandApdu selectByName(byte[] aid) {
        return new CommandApdu(
                0x00,
                Iso7816Commands.INS_SELECT,
                0x04,
                0x00,
                aid,
                256);
    }

    private static CommandApdu getData(int tag) {
        return new CommandApdu(
                0x80,
                Iso7816Commands.INS_GET_DATA,
                (tag >>> 8) & 0xFF,
                tag & 0xFF,
                null,
                256);
    }
}

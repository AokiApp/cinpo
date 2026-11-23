package app.aoki.cinpo.task;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.AppletEntry;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.config.ScpConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardTaskLoaderTest {

  /**
   * Loads card tasks from a task context built with a stub channel, manifest, profile, and task
   * arguments.
   *
   * <p>This test checks that the loaded task receives the same channel, manifest, profile, and task
   * arguments, and that its secure channel profile is present.
   *
   * <p>Benefit: the tasks inherit these fields from the context, so this test catches a loader path
   * that silently drops one of them.
   */
  @Test
  @DisplayName("Loading card tasks should populate the inherited context fields on the loaded task")
  void populatesInheritedDependencyFields() {
    ApduChannel channel = new StubChannel();
    AppletManifest manifest =
        new AppletManifest(
            "test",
            "26.0",
            "3.0.4",
            "test",
            new byte[] {0x01},
            "1.0",
            List.of(
                new AppletEntry(
                    "main",
                    "test.MainApplet",
                    new byte[] {0x01},
                    new byte[] {0x02},
                    new byte[] {0x00})),
            List.of());
    ScpConfig scpConfig =
        new ScpConfig(
            "scp03", new byte[] {0x01}, 0, 0, 1, new byte[16], new byte[16], new byte[16]);
    Profile profile = new Profile("test", "pcsc", scpConfig);
    TaskArguments taskArguments = new TaskArguments(List.of("--record", "sample"));

    TaskContext context = new TaskContext(channel, manifest, profile, taskArguments);
    CardTask task = CardTaskLoader.loadAll("loader-test", context).get(0);

    assertSame(channel, task.channel);
    assertSame(manifest, task.manifest);
    assertSame(profile, task.profile);
    assertSame(taskArguments, task.taskArguments);
    assertNotNull(task.secureChannelProfile);
  }

  private static final class StubChannel implements ApduChannel {

    @Override
    public ResponseApdu transmit(CommandApdu capdu) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {}

    @Override
    public void close() {}
  }
}

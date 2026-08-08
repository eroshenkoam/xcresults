package io.eroshenkoam.xcresults;

import io.eroshenkoam.xcresults.export.ExportCommand;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@CommandLine.Command(
        name = "xcresult", mixinStandardHelpOptions = true,
        versionProvider = MainCommand.VersionProvider.class,
        subcommands = {ExportCommand.class}
)
public class MainCommand implements Runnable {

    @Override
    public void run() {
    }

    public static class VersionProvider implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() throws Exception {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    MainCommand.class.getResourceAsStream("/version.txt"), StandardCharsets.UTF_8))) {
                return new String[]{reader.readLine()};
            }
        }

    }

}

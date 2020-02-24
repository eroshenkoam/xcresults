package io.eroshenkoam.xcresults;

import io.eroshenkoam.xcresults.export.ExportCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "xcresult", mixinStandardHelpOptions = true,
        subcommands = {ExportCommand.class}
)
public class MainCommand implements Runnable {

    @Override
    public void run() {
    }

}

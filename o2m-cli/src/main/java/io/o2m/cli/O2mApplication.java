package io.o2m.cli;

import io.o2m.cli.command.SchemaCommand;
import picocli.CommandLine;

public class O2mApplication {
    public static void main(String[] args) {
        int code = new CommandLine(new SchemaCommand()).execute(args);
        System.exit(code);
    }
}

package io.github.seud0nym.tch_version_compare;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;

import com.jenkov.cliargs.CliArgs;

public final class Main {
    public static void main(String[] args) throws IOException, ParseException, GeneralSecurityException {
        CliArgs cliArgs = new CliArgs(args);

        boolean useCache = cliArgs.switchPresent("-usecache");
        boolean includePackageSizeChanged = cliArgs.switchPresent("-incpkgsizechanged");

        String[] targets = cliArgs.targets();
        if (targets.length != 2) {
            throw new RuntimeException("You must specify the top-level directory of the TWO converted firmware files!");
        }

        Firmware fw1 = new Firmware(new File(targets[0]), useCache);
        Firmware fw2 = new Firmware(new File(targets[1]), useCache);

        fw2.compareWith(fw1, includePackageSizeChanged);
    }
}

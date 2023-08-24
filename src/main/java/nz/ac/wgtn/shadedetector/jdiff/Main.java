package nz.ac.wgtn.shadedetector.jdiff;

import com.google.common.base.Preconditions;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main class providing a CLI.
 * @author jens dietrich
 */
public class Main {

    private static Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main (String[] args) throws IOException {
        Options options = new Options();
        options.addRequiredOption("f1", "folder1",true, "the first folder with Java sources");
        options.addRequiredOption("f2", "folder2",true, "the second folder with Java sources");
        options.addRequiredOption("o", "out",true, "a text file, class names with changes will be written to this file");

        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        }
        catch (ParseException x) {
            LOGGER.error(x.getMessage(),x);
            printHelp(options);
            System.exit(1);
        }

        Path folder1 = Path.of(cmd.getOptionValue("folder1"));
        Preconditions.checkArgument(Files.exists(folder1),"input folder does not exist: " + folder1);
        Path folder2 = Path.of(cmd.getOptionValue("folder2"));
        Preconditions.checkArgument(Files.exists(folder2),"input folder does not exist: " + folder2);

        Preconditions.checkArgument(!folder1.equals(folder2),"comparing a folder with itself: " + folder1);

        LOGGER.info("comparing sources from folders {} and {}",folder1,folder2);

        Path out = Path.of(cmd.getOptionValue("out"));

        Set<String> changes = DiffDetector.findChangedClasses(folder1,folder2);
        // sort for better usability
        List<String> sortedChanges = changes.stream().sorted().collect(Collectors.toList());

        Files.write(out,sortedChanges);
        LOGGER.info("results written to {}",out);
        LOGGER.info("{} classes with changes found",sortedChanges.size());

    }

    private static void printHelp(Options options) {
        String header = "Arguments:\n\n";
        String footer = "\nPlease report issues at https://github.com/jensdietrich/jdiff-study/issues/";
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(150);
        formatter.printHelp("java -cp <classpath> Main", header, options, footer, true);
    }
}

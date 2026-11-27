import java.io.*;
import java.util.*;
import testutil.Check;

/**
 * Runs every test suite, each in its own JVM, so a solution stuck in an infinite loop
 * (which Java cannot stop) only ends its own suite. Exit code 0 = all passed.
 *
 *   java -cp out AllTests                 all suites
 *   java -cp out AllTests "GPU credit"    one suite, in this JVM
 */
public class AllTests {
    static final Map<String, Runnable> SUITES = new LinkedHashMap<>();
    static {
        SUITES.put("Plant disease", plantdisease.PlantDiseaseTest::run);
        SUITES.put("GPU credit", gpucredit.CreditSystemTest::run);
        SUITES.put("Rectangle board", rectangleboard.RectangleBoardTest::run);
        SUITES.put("Cluster mode", clustermode.ClusterModeTest::run);
        SUITES.put("Bootloader", bootloader.BootloaderTest::run);
        SUITES.put("Duplicate files", duplicatefiles.DuplicateFilesTest::run);
        SUITES.put("Banking system", bankingsystem.BankingSystemTest::run);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {                                    // child mode: run one suite, report counts
            Check.suite(args[0], SUITES.get(args[0]));
            System.out.println("##RESULT " + Check.passed + " " + Check.failed);
            for (String f : Check.failures()) System.out.println("##FAIL " + f);
            System.exit(Check.failed == 0 ? 0 : 1);
        }

        int passed = 0, failed = 0;
        List<String> failures = new ArrayList<>();
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        for (String name : SUITES.keySet()) {
            Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "AllTests", name)
                    .redirectErrorStream(true).start();
            boolean reported = false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.startsWith("##RESULT ")) {
                        String[] parts = line.split(" ");
                        passed += Integer.parseInt(parts[1]);
                        failed += Integer.parseInt(parts[2]);
                        reported = true;
                    } else if (line.startsWith("##FAIL ")) {
                        failures.add(line.substring(7));
                    } else {
                        System.out.println(line);
                    }
                }
            }
            int code = p.waitFor();
            if (!reported) {                                       // the child JVM itself died
                failed++;
                failures.add(name + " suite: JVM exited with code " + code + " before reporting");
            }
        }

        System.out.println();
        if (failed == 0) {
            System.out.println("ALL PASSED (" + passed + " checks)");
        } else {
            System.out.println(failed + " FAILED, " + passed + " passed. Failed checks:");
            for (String f : failures) System.out.println("  - " + f);
        }
        System.exit(failed == 0 ? 0 : 1);
    }
}

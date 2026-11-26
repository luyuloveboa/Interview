import testutil.Check;

/** Runs every test suite. Exit code 0 = all passed. */
public class AllTests {
    public static void main(String[] args) {
        plantdisease.PlantDiseaseTest.run();
        gpucredit.CreditSystemTest.run();
        rectangleboard.RectangleBoardTest.run();

        System.out.println();
        System.out.println(Check.failed == 0
                ? "ALL PASSED (" + Check.passed + " checks)"
                : Check.failed + " FAILED, " + Check.passed + " passed");
        System.exit(Check.failed == 0 ? 0 : 1);
    }
}

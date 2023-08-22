package nz.ac.wgtn.shadedetector.jdiff;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DiffDetectorTests {


    private static Path getSrc(String name,String context) {
        String p = DiffDetectorTests.class.getResource("/" + context + "/com/foo/" + name + ".java").getPath();
        return Path.of(p);
    }

    private static void hasChanges(String name) {
        Path src1 = getSrc(name,"before");
        Path src2 = getSrc(name,"after");
        Assumptions.assumeTrue(Files.exists(src1));
        Assumptions.assumeTrue(Files.exists(src2));
        assertTrue(DiffDetector.hasChangesInCU(src1,src2));
    }

    private static void hasNoChanges(String name) {
        Path src1 = getSrc(name,"before");
        Path src2 = getSrc(name,"after");
        Assumptions.assumeTrue(Files.exists(src1));
        Assumptions.assumeTrue(Files.exists(src2));
        assertFalse(DiffDetector.hasChangesInCU(src1,src2));
    }

    @Test
    public void testBrokenClass() {
        Path src1 = getSrc("BrokenClass","before");
        Path src2 = getSrc("Class1","before");
        assertThrows(IllegalArgumentException.class, () -> DiffDetector.hasChangesInCU(src1,src2));
    }

    @Test
    public void testBaselineEquals() {
        hasNoChanges("Class1");
    }

    @Test
    public void testCommentsAddedOrRemoved() {
        hasNoChanges("Class2");
    }

    @Test
    public void testBinaryExpressionChange() {
        hasChanges("Class3");
    }

    @Test
    public void testUnaryExpressionChange1() {
        hasChanges("Class4");
    }

    @Test
    public void testUnaryExpressionChange2() {
        hasChanges("Class5");
    }

    @Test
    public void testRuntimeAnnotationAdded() {
        hasChanges("Class6");
    }

    @Test
    public void testSourceAnnotationAdded1() {
        hasNoChanges("Class7");
    }

    @Test
    public void testSourceAnnotationAdded2() {
        hasNoChanges("Class8");
    }
    
    @Test
    public void testSourceAnnotationAdded3() {
        hasNoChanges("Class8");
    }

}

package nz.ac.wgtn.shadedetector.jdiff;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class DiffDetectorTests {

    public static final String PACKAGE_NAME = "com.foo";
    public static final String PACKAGE_PATH = PACKAGE_NAME.replace('.','/');

    private static Path getSrc(String name,String context) {
        String p = DiffDetectorTests.class.getResource("/" + context + "/" + PACKAGE_PATH + "/" + name + ".java").getPath();
        return Path.of(p);
    }

    private static void hasChanges(String name) {
        Path src1 = getSrc(name,"before");
        Path src2 = getSrc(name,"after");
        Assumptions.assumeTrue(Files.exists(src1));
        Assumptions.assumeTrue(Files.exists(src2));
        Optional<String> change = DiffDetector.hasChangesInCU(src1,src2);
        assertTrue(change.isPresent());
        assertEquals(PACKAGE_NAME + '.' + name,change.get());
    }

    private static void hasNoChanges(String name) {
        Path src1 = getSrc(name,"before");
        Path src2 = getSrc(name,"after");
        Assumptions.assumeTrue(Files.exists(src1));
        Assumptions.assumeTrue(Files.exists(src2));
        assertFalse(DiffDetector.hasChangesInCU(src1,src2).isPresent());
    }

    @Test
    public void testBrokenClass() {
        Path src1 = getSrc("BrokenClass","before");
        Path src2 = getSrc("Class1","before");
        assertThrows(IllegalArgumentException.class, () -> DiffDetector.hasChangesInCU(src1,src2));
    }

    @Test
    public void testClassNameWithPackage() throws Exception {
        Path src = getSrc("Class1","before");
        String className = DiffDetector.getClassName(src, StaticJavaParser.parse(src));
        assertEquals("com.foo.Class1",className);
    }

    @Test
    public void testClassNameWithoutPackage() throws Exception {
        String p = DiffDetectorTests.class.getResource("/ClassWithoutPackage.java").getPath();
        Path src = Path.of(p);
        String className = DiffDetector.getClassName(src, StaticJavaParser.parse(src));
        assertEquals("ClassWithoutPackage",className);
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
        hasNoChanges("Class9");
    }


    @Test
    public void testAll() {
        Set<String> expectedChangedClasses = Set.of(
            "Class3", "Class4", "Class5", "Class6"
        );

        Set<String> expectedChangedClassesQualified = expectedChangedClasses.stream()
            .map(cl -> PACKAGE_NAME + '.' + cl)
            .collect(Collectors.toSet());

        Path beforeFolder = Path.of(DiffDetectorTests.class.getResource("/before/" + PACKAGE_PATH).getPath());
        Path afterFolder = Path.of(DiffDetectorTests.class.getResource("/after/" + PACKAGE_PATH).getPath());

        Assumptions.assumeTrue(Files.exists(beforeFolder));
        Assumptions.assumeTrue(Files.exists(afterFolder));

        Set<String> changedClasses = DiffDetector.findChangedClasses(beforeFolder,afterFolder);

        assertEquals(expectedChangedClassesQualified,changedClasses);

    }

}

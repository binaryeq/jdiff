package nz.ac.wgtn.shadedetector.jdiff;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.metamodel.PropertyMetaModel;
import com.github.javaparser.utils.Pair;
import com.google.common.collect.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * change detection.
 * @author jens dietrich
 */
public class DiffDetector {

    private static Set<String> SHORT_SRC_ANNOTATION_NAMES = Set.of("SuppressWarnings","Override");

    private static Set<String> FULL_SRC_ANNOTATION_NAMES = Set.of("java.lang.SuppressWarnings");

    private static Logger LOGGER = LoggerFactory.getLogger(DiffDetector.class);
    public static final Predicate<Node> IS_COMMENT = node -> node instanceof Comment;
    public static final Predicate<Node> IS_ANNOTATION = node -> node instanceof AnnotationExpr;
    public static final Predicate<Node> IS_RUNTIME_ANNOTATION = node -> {
        if (node instanceof AnnotationExpr) {
            AnnotationExpr anno = (AnnotationExpr) node;
            Name name = anno.findFirst(Name.class).orElse(null);
            if (name!=null) {
                String n = name.asString();
                if (SHORT_SRC_ANNOTATION_NAMES.contains(n) || FULL_SRC_ANNOTATION_NAMES.contains(n)) {
                    return false;
                }
            }
        }
        return true;
    };



    /**
     * Diff two folders containing source code, return pairs of sources with changes.
     * Changes not effecting bytecode will be ignored: comments, formatting, or annotations with RetentionPolicy.SOURCE.
     * @param original
     * @param cloneCandidate
     * @return
     */
    public Set<Pair<Path,Path>> diff(Path original, Path cloneCandidate) {
        try {
            List<Path> originalJavaSources = listJavaSources(original,true);
            List<Path> cloneCandidateJavaSources = listJavaSources(cloneCandidate,true);

            List<Pair<Path,Path>> potentialMatches = new ArrayList<>();
            for (Path originalSource:originalJavaSources) {
                String cuName1 = originalSource.getName(originalSource.getNameCount()-1).toString();
                for (Path cloneSource : cloneCandidateJavaSources) {
                    String cuName2 = cloneSource.getName(cloneSource.getNameCount() - 1).toString();
                    if (Objects.equals(cuName1, cuName2)) {
                        potentialMatches.add(new Pair<>(originalSource, cloneSource));
                    }
                }
            }

            LOGGER.info("Analysing {} pairs of java source code",potentialMatches.size());

            return potentialMatches.stream()
                .filter(p -> hasChangesInCU(p.a,p.b))
                .collect(Collectors.toSet());
        }
        catch (IOException x) {
            LOGGER.error("Error extracting Java sources from {},{}",original,cloneCandidate,x);
        }
        return Collections.EMPTY_SET;
    }

    static boolean hasChangesInCU(Path path1, Path path2) {
        try {
            CompilationUnit cu1 = StaticJavaParser.parse(path1);
            CompilationUnit cu2 = StaticJavaParser.parse(path2);
            // String pck1 = cu1.getPackageDeclaration().isPresent() ? cu1.getPackageDeclaration().get().getNameAsString() : "";
            // String pck2 = cu2.getPackageDeclaration().isPresent() ? cu1.getPackageDeclaration().get().getNameAsString() : "";
            // boolean samePackage = Objects.equals(pck1,pck2);
            return hasChanges(cu1,cu2);

        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    static boolean hasChanges(Node node1, Node node2) {

        if (node1 instanceof Comment) {
            return false;
        }

        // but normally node types should be the same
        if (node1.getClass() != node2.getClass()) {  // must be of the same kind
            return true;
        }

        // special checks for non-node properties
        List<PropertyMetaModel> properties1 = node1.getMetaModel().getDeclaredPropertyMetaModels();
        List<PropertyMetaModel> properties2 = node2.getMetaModel().getDeclaredPropertyMetaModels();

        assert properties1 == properties2;

        for (PropertyMetaModel property:properties1) {
            if (!property.isNode() && !Objects.equals(property.getValue(node1),property.getValue(node2))) {
                return true;
            }
        }


        // special checks for special types


//        if (node1 instanceof BinaryExpr) {
//            BinaryExpr binExpr1 = (BinaryExpr) node1;
//            BinaryExpr binExpr2 = (BinaryExpr) node2;
//            if (binExpr1.getOperator()!=binExpr2.getOperator()) {
//                return true;
//            }
//        }
//
//        if (node1 instanceof UnaryExpr) {
//            UnaryExpr unExpr1 = (UnaryExpr) node1;
//            UnaryExpr unExpr2 = (UnaryExpr) node2;
//            if (unExpr1.getOperator()!=unExpr2.getOperator()) {
//                return true;
//            }
//        }


        List<Node> children1 = node1.getChildNodes().stream().filter(IS_COMMENT.negate()).collect(Collectors.toList());
        List<Node> children2 = node2.getChildNodes().stream().filter(IS_COMMENT.negate()).collect(Collectors.toList());


        List<Node> regularChildren1 = children1.stream().filter(IS_ANNOTATION.negate()).collect(Collectors.toList());
        List<Node> regularChildren2 = children2.stream().filter(IS_ANNOTATION.negate()).collect(Collectors.toList());

        List<Node> rtAnnotations1 = children1.stream().filter(IS_RUNTIME_ANNOTATION).collect(Collectors.toList());
        List<Node> rtAnnotations2 = children2.stream().filter(IS_RUNTIME_ANNOTATION).collect(Collectors.toList());

        if (regularChildren1.isEmpty() && regularChildren2.isEmpty() && rtAnnotations1.isEmpty() & rtAnnotations2.isEmpty()) {
            // compare leaves
            return !Objects.equals(node1.toString().trim(),node2.toString().trim());
        }

        return hasChanges(regularChildren1,regularChildren2) || hasChanges(rtAnnotations1,rtAnnotations2);
    }


    static boolean hasChanges(List<Node> childNodes1, List<Node> childNodes2) {
        if (childNodes1.size()!=childNodes2.size()) {
            return true;
        }
        boolean result = false;
        for (int i=0;i<childNodes1.size();i++) {
            Node childNode1 = childNodes1.get(i);
            Node childNode2 = childNodes2.get(i);
            result = result || hasChanges(childNode1,childNode2);
        }
        return result;
    }


    private static List<Path> listJavaSources(Path zipOrFolder,boolean excludePackageInfo) throws IOException {
        Predicate<Path> filter = path -> path.toString().endsWith(".java");
        if (excludePackageInfo) {
            filter = filter.and(p -> !p.toString().endsWith("package-info.java"));
        }
        return listContent(zipOrFolder,filter);
    }

    private static List<Path> listContent(Path zipOrFolder, Predicate<Path> filter) throws IOException {
        if (zipOrFolder.toFile().isDirectory()) {
            return Files.walk(zipOrFolder)
                .filter(file -> !Files.isDirectory(file))
                .filter(filter)
                .collect(Collectors.toList());
        }
        else {
            // use API in Java <= 11
            FileSystem fs = FileSystems.newFileSystem(zipOrFolder, DiffDetector.class.getClassLoader());
            return Streams.stream(fs.getRootDirectories())
                .flatMap(root -> {
                    try {
                        return Files.walk(root);
                    }
                    catch (IOException x) {
                        LOGGER.error("Error extracting content of file system",x);
                        throw new RuntimeException(x);
                    }
                })
                .filter(filter)
                .collect(Collectors.toList());

        }

    }


}

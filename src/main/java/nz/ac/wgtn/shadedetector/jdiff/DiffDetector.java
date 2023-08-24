package nz.ac.wgtn.shadedetector.jdiff;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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
 * Change detection.
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
     * Given two folders with Java sources, compute and return a set of top-level classes
     * that occur in both folders, and have some relevant changes.
     * Relevant means that formatting details, comments and annotation with source retention (from a given list) are not counted.
     * @param folder1
     * @param folder2
     * @return
     */
    public static Set<String> findChangedClasses(Path folder1, Path folder2) {
        try {
            List<Path> sources1 = listJavaSources(folder1,true);
            List<Path> sources2 = listJavaSources(folder2,true);

            List<Pair<Path,Path>> potentialMatches = new ArrayList<>();
            for (Path originalSource:sources1) {
                String cuName1 = originalSource.getName(originalSource.getNameCount()-1).toString();
                for (Path cloneSource : sources2) {
                    String cuName2 = cloneSource.getName(cloneSource.getNameCount() - 1).toString();
                    if (Objects.equals(cuName1, cuName2)) {
                        potentialMatches.add(new Pair<>(originalSource, cloneSource));
                    }
                }
            }

            LOGGER.info("Analysing {} pairs of java source code",potentialMatches.size());

            return potentialMatches.stream()
                .map(p -> hasChangesInCU(p.a,p.b))
                .filter(c -> c.isPresent())
                .map(c -> c.get())
                .collect(Collectors.toSet());
        }
        catch (IOException x) {
            LOGGER.error("Error extracting Java sources from {},{}",folder1,folder2,x);
        }
        return Collections.EMPTY_SET;
    }


    /**
     * Detect whether the sources associated with those files have changes.
     * If so, return the fully classified classname of the top-level class, or null otherwise
     * @param path1
     * @param path2
     * @return
     */
    static Optional<String> hasChangesInCU(Path path1, Path path2) {
        try {
            CompilationUnit cu1 = StaticJavaParser.parse(path1);
            CompilationUnit cu2 = StaticJavaParser.parse(path2);

            String className1 = getClassName(path1,cu1);
            String className2 = getClassName(path2,cu2);

            if (!Objects.equals(className1,className2)) {
                return Optional.empty();
            }

            return hasChanges(cu1,cu2) ? Optional.of(className1) : Optional.empty();

        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String getClassName(Path path,CompilationUnit cu)  {
        String qName = "";
        PackageDeclaration pckDecl = cu.getPackageDeclaration().orElse(null);
        if (pckDecl!=null) {
            qName = pckDecl.getNameAsString() + '.';
        }
        TypeDeclaration typeDecl = cu.getPrimaryType().orElseThrow(() -> new IllegalArgumentException("CU does not define a primary type (class with the same name as the file): " + path.toFile().getAbsolutePath()));
        return qName + typeDecl.getNameAsString();
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

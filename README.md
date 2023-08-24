# jdiff

A tool to establish whether two java compilation units have changes resulting in different bytecode. This is based on diffing source code using the respective ASTs.

## Prerequisites

java 11 or better

## Building

`mvn clean package`

This will generate `jdiff.jar` in `/target`.

## Usage


```java
java -jar jdff.jar
 -f1,--folder1 <arg>   the first folder with Java sources
 -f2,--folder2 <arg>   the second folder with Java sources
 -o,--out <arg>        a text file, class names with changes will be written to this file
```


## Limitations

1. The tool will provide a list of top-level class names with changes. It will currently not detect inner classes thet may have changes. I.e. the changes related to one source code file (compilation unit) may correspond to changes in only some of the byte code units (.class) files generated from this source code file, corresponding to different inner classes. 
2. There are certain source code changes that will not (or not necesarilly) result in changes to the corresponding bytecode: 

   a. __comments__ -- changes to comments are ignored (athough changes may change line numbers referenced in byte code)

   b. __annotations with retention policy [RetentionPolicy::SOURCE](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/annotation/RetentionPolicy.html#SOURCE)__ -- there is a small list of such annotations hardcoded, if other annotations with source retention were used, then this would result in false positives

   c. there are expressions thar are being simplified by the compiler as part of __constant inlining / folding__ -- this would also detect false positives
   
   
   
   
     



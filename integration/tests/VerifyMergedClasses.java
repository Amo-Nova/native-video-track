import java.nio.file.*;
import java.util.*;

/** Link all changed/helper classes under the normal Bitwig class path, without the JVMTI loader. */
public final class VerifyMergedClasses {
    public static void main(String[] args) throws Exception {
        int count=0;
        for(String name:Files.readAllLines(Path.of(args[0]))) {
            Class<?> type=Class.forName(name,false,ClassLoader.getSystemClassLoader());
            type.getDeclaredMethods();
            type.getDeclaredConstructors();
            type.getDeclaredFields();
            count++;
        }
        System.out.println("MERGED_CLASSES_LINKED "+count);
    }
}

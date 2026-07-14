package e.s.miniweb.models;

import java.util.ArrayList;
import java.util.List;

// An example model file.
// Models should always be under the `/models` path, or the `/controllers` path.
// Any models not in these paths may break if the app is built in release mode.

public class NestedObjectModel {
    public final List<String> children;
    public final String name;

    public NestedObjectModel(String name){
        this.name = name;

        // shove in some sample data
        children = new ArrayList<>();
        children.add("one");
        children.add("two");
        children.add("three");
        children.add("four");
    }
}

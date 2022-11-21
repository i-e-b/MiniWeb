package e.s.miniweb.core.template;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Very simple and liberal HTML parser for templating
 * The tree we output is just a set of ranges over the original string.
 * */
public class HNode {
    public static final int TYPE_ROOT = 0; // the entire document
    public static final int TYPE_TEXT = 1; // doesn't look like mark-up
    public static final int TYPE_NODE = 2; // looks like <elem attr>...</elem>
    public static final int TYPE_ELEM = 3; // looks like <elem attr/>
    public static final int TYPE_DIRK = 4; // looks like <!directive...>
    public static final int TYPE_COME = 5; // looks like <!-- ... ---> or <script>...</script>

    /** Child nodes. Can be empty, never null */
    public final List<HNode> children = new ArrayList<>();
    /** parent node. Can be null */
    public final HNode parent;

    private final String Src; // reference to the original

    public int type;
    public int srcStart, srcEnd; // total span of this node, including children (like outerHTML())
    public int contStart, contEnd; // internal span of this node (like innerHTML())


    private static final int NOT_FOUND = -1;

    public HNode(HNode parent, String src){
        this.parent=parent;
        Src = src;
    }

    protected HNode(HNode parent, int srcStart, int contStart, int contEnd, int srcEnd, int type){
        this.parent = parent;Src = parent.Src;this.type = type;
        this.srcStart = srcStart;this.contStart = contStart;this.contEnd = contEnd;this.srcEnd = srcEnd;
    }

    public static HNode parse(String src) {
        HNode base = new HNode(null, src);

        parseRecursive(base, src, 0);
        base.contStart = base.srcStart = 0;
        base.contEnd = base.srcEnd = src.length() - 1;
        base.type = TYPE_ROOT;

        return base;
    }

    /** recurse, return the offset we ended */
    @SuppressWarnings("UnnecessaryContinue")
    private static int parseRecursive(HNode target, String src, int offset){
        /*
        Plan

        0. We just want to separate tags from text

        1. Keep whitespace, attach it wherever
        2. <script> tags are treated as if they had CDATA tags
        3. entities are ignored (treated as text)
        6. should keep the difference between <x></x> and <x/>
         */

        /*
        for each level of recursion, there is a repetition of nodes,
        each of which could be one of:
        * Text block
        * Child tag
        * Closing tag (ends this recursion)

         */

        int lastIndex = src.length()-1;
        int left = offset;
        target.contStart = offset;
        while (left <= lastIndex){
            int leftAngle = src.indexOf('<', left);
            int more = lastIndex - leftAngle;

            if (leftAngle == NOT_FOUND){
                // no more markup in the source.
                // if left..end is not empty, add a text node and return
                leftAngle = lastIndex;
                if (leftAngle > left){
                    HNode.textChild(target, left, lastIndex);
                }
                return leftAngle; // return because EOF
            /*} else if (leftAngle > left) { // either we're not starting on an element
                target.children.add(HNode.textChild(target, left, leftAngle));
                left = leftAngle; // move forward*/
            } else if (leftAngle >= lastIndex) {
                // sanity check: got a '<' as the last character
                HNode.textChild(target, left, lastIndex);
                return lastIndex; // return because it's broken.
            } else {
                // we have the start of an element <...>
                int rightAngle = src.indexOf('>', left);

                // sanity checks
                if (rightAngle == NOT_FOUND || more < 2 || rightAngle == left + 1) { // `<`...EOF (x2)   or  `<>`
                    // invalid markup? Consider the rest as text and bail out
                    HNode.textChild(target, left, lastIndex);
                    return lastIndex + 1; // return because it's broken.
                }

                if (src.charAt(leftAngle+1) == '/') { //   </...
                    // end of our own tag
                    target.srcEnd = rightAngle;
                    target.contEnd = leftAngle - 1;
                    if (target.contEnd == 0){
                        target.contEnd = -1;
                    }
                    // TODO: unwind until we get to a matching tag
                    return rightAngle + 1; // return because it's the end of this tag.
                } else if (src.charAt(leftAngle+1) == '!') { // <!...
                    if (more > 2 && src.charAt(leftAngle+2) == '-' && src.charAt(leftAngle+3) == '-') {
                        // `<!-- ... -->`
                        left = processOtherBlock(target, left, "-->") + 1;
                    } else { // `<!...>`
                        HNode.directiveChild(target, left, rightAngle);
                        left = rightAngle+1;
                    }
                    continue;
                } else if (src.charAt(leftAngle+1)=='?'){ // <? ... ?>
                    // this is an XML directive.
                    HNode.directiveChild(target, left, rightAngle);
                    left = rightAngle+1;
                    continue;
                } else { // a child tag
                    // check for 'empty' element
                    if (src.charAt(rightAngle - 1) == '/') { // goes like <elem ... />
                        HNode.elemChild(target, left, rightAngle);
                        left = rightAngle + 1;
                        continue;
                    } else if (isScript(target, leftAngle)){
                        left = processOtherBlock(target, left, "</script>") + 1;
                        continue;
                    } else {
                        // This looks like a real node. Recurse
                        HNode node = new HNode(target, target.Src);
                        node.type = TYPE_NODE;
                        node.srcStart = leftAngle;
                        left = parseRecursive(node, src, rightAngle + 1);
                        if (node.contEnd < 1 || node.srcEnd < 1){
                            Log.w("html", "fail");
                        }
                        target.children.add(node);
                    }
                }
            }
        }
        return left;
    }

    private static boolean isScript(HNode target, int offset) {
        final String script = "script";
        int len = script.length();
        int end = target.Src.length() - (offset+1);
        for (int i = 0; i < len || i < end; i ++){
            if (target.Src.charAt(i+offset) != script.charAt(i)) return false;
        }
        return true;
    }

    private static int processOtherBlock(HNode target, int left, String terminator) {
        int right = target.Src.indexOf(terminator, left);
        if (right == NOT_FOUND) right = target.Src.length()-1;
        else right += terminator.length();
        target.children.add(new HNode(target, left, right,right,right,TYPE_COME));
        return right;

    }

    private static void elemChild(HNode target, int left, int right) {
        target.children.add(new HNode(target, left, right, right, right, TYPE_ELEM));
    }

    private static void directiveChild(HNode target, int left, int right) {
        target.children.add(new HNode(target, left, right, right, right, TYPE_DIRK));
    }

    private static void textChild(HNode target, int left, int right) {
        target.children.add(new HNode(target, left, left, right, right, TYPE_TEXT));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString(){
        try {
            if (srcStart == contStart) return "text: " + Src.substring(srcStart, srcEnd);
            return "elem: " + Src.substring(srcStart, srcEnd + 1); // just the opening tag
        } catch (Exception ex){
            return "fail: "+srcStart+".."+srcEnd;
        }
    }
}
